package com.cake.clockify.addon.db.service;

import com.cake.clockify.addon.db.AbstractDbTest;
import com.cake.clockify.addon.db.entity.AddonWebhookEvent;
import com.cake.clockify.addon.db.repository.AddonWebhookEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class AddonWebhookEventServiceTest extends AbstractDbTest {

    @Autowired
    private AddonWebhookEventService service;

    @Autowired
    private AddonWebhookEventRepository repository;

    @Test
    void testRecordReceivedCreatesEvent() {
        AddonWebhookEvent event = service.recordReceived("my-addon", "ws-1", "TIME_ENTRY_CREATED", "dedupe-1", "hash-1");
        assertThat(event).isNotNull();
        assertThat(event.getId()).isNotNull();
        assertThat(event.getStatus()).isEqualTo("RECEIVED");
        assertThat(event.getReceivedAt()).isNotNull();
        assertThat(event.getRetryCount()).isEqualTo(0);

        Optional<AddonWebhookEvent> dbEvent = repository.findById(event.getId());
        assertThat(dbEvent).isPresent();
    }

    @Test
    void testIsDuplicate() {
        assertThat(service.isDuplicate("my-addon", "ws-1", "dedupe-1")).isFalse();

        service.recordReceived("my-addon", "ws-1", "TIME_ENTRY_CREATED", "dedupe-1", "hash-1");
        assertThat(service.isDuplicate("my-addon", "ws-1", "dedupe-1")).isTrue();
        assertThat(service.isDuplicate("my-addon", "ws-1", "dedupe-2")).isFalse(); // different key
    }

    @Test
    void testIsDuplicateAllowsFailedRetry() {
        assertThat(service.isDuplicate("my-addon", "ws-1", "dedupe-1")).isFalse();

        AddonWebhookEvent event = service.recordReceived("my-addon", "ws-1", "TIME_ENTRY_CREATED", "dedupe-1", "hash-1");
        service.tryStartProcessing(event.getId());
        
        // When processing or received, it's considered duplicate
        assertThat(service.isDuplicate("my-addon", "ws-1", "dedupe-1")).isTrue();

        service.markFailed(event.getId(), "Failed call");
        
        // Once failed, it should not be duplicate anymore, allowing retries
        assertThat(service.isDuplicate("my-addon", "ws-1", "dedupe-1")).isFalse();

        // Record again (retry starts)
        AddonWebhookEvent retryEvent = service.recordReceived("my-addon", "ws-1", "TIME_ENTRY_CREATED", "dedupe-1", "hash-1");
        assertThat(retryEvent.getId()).isEqualTo(event.getId());
        assertThat(retryEvent.getStatus()).isEqualTo("RECEIVED");
    }

    @Test
    void recordEventHandlesConcurrentDuplicateReceiptWithoutLeakingConstraintFailure() throws Exception {
        int attempts = 8;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        Callable<UUID> receipt = () -> {
            ready.countDown();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.await(5, TimeUnit.SECONDS);
            return service.recordEvent("my-addon", "ws-race", "TIME_ENTRY_CREATED", "dedupe-race", "hash-1");
        };

        try {
            var futures = IntStream.range(0, attempts)
                    .mapToObj(ignored -> executor.submit(receipt))
                    .toList();
            start.countDown();

            assertThat(futures)
                    .extracting(this::getFutureValue)
                    .doesNotContainNull()
                    .containsOnly(futures.getFirst().get());
            assertThat(repository.findByAddonKeyAndWorkspaceIdAndDedupeKey("my-addon", "ws-race", "dedupe-race"))
                    .isPresent();
        } finally {
            executor.shutdownNow();
            assertThatNoException().isThrownBy(() -> executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void recordReceivedDoesNotReopenActiveDuplicate() {
        AddonWebhookEvent event = service.recordReceived("my-addon", "ws-active", "TIME_ENTRY_CREATED", "dedupe-active", "hash-1");
        assertThat(service.tryStartProcessing(event.getId())).isTrue();

        AddonWebhookEvent duplicate = service.recordReceived("my-addon", "ws-active", "TIME_ENTRY_CREATED", "dedupe-active", "hash-1");

        assertThat(duplicate.getId()).isEqualTo(event.getId());
        assertThat(duplicate.getStatus()).isEqualTo("PROCESSING");
        assertThat(service.tryStartProcessing(duplicate.getId())).isFalse();
    }

    @Test
    void testTryStartProcessingTransitionsStatus() {
        AddonWebhookEvent event = service.recordReceived("my-addon", "ws-1", "TIME_ENTRY_CREATED", "dedupe-1", "hash-1");

        boolean started = service.tryStartProcessing(event.getId());
        assertThat(started).isTrue();

        Optional<AddonWebhookEvent> dbEvent = repository.findById(event.getId());
        assertThat(dbEvent).isPresent();
        assertThat(dbEvent.get().getStatus()).isEqualTo("PROCESSING");
    }

    @Test
    void testTryStartProcessingReturnsFalseWhenAlreadyProcessingOrProcessed() {
        AddonWebhookEvent event = service.recordReceived("my-addon", "ws-1", "TIME_ENTRY_CREATED", "dedupe-1", "hash-1");

        // First transition succeeds
        assertThat(service.tryStartProcessing(event.getId())).isTrue();

        // Second transition fails because status is no longer RECEIVED
        assertThat(service.tryStartProcessing(event.getId())).isFalse();

        // Mark processed
        service.markProcessed(event.getId());

        // Attempting to process again fails
        assertThat(service.tryStartProcessing(event.getId())).isFalse();
    }

    @Test
    void testMarkProcessedUpdatesStatus() {
        AddonWebhookEvent event = service.recordReceived("my-addon", "ws-1", "TIME_ENTRY_CREATED", "dedupe-1", "hash-1");
        service.tryStartProcessing(event.getId());
        service.markProcessed(event.getId());

        Optional<AddonWebhookEvent> dbEvent = repository.findById(event.getId());
        assertThat(dbEvent).isPresent();
        assertThat(dbEvent.get().getStatus()).isEqualTo("PROCESSED");
        assertThat(dbEvent.get().getProcessedAt()).isNotNull();
    }

    @Test
    void testMarkFailedIncrementsRetryCountAndRedactsSecrets() {
        AddonWebhookEvent event = service.recordReceived("my-addon", "ws-1", "TIME_ENTRY_CREATED", "dedupe-1", "hash-1");
        service.tryStartProcessing(event.getId());

        String errorWithSecrets = "Failed call: " + "token=" + "xyz123abc456"
                + " and api" + "Key=" + "some-secret-key-123";
        service.markFailed(event.getId(), errorWithSecrets);

        Optional<AddonWebhookEvent> dbEvent = repository.findById(event.getId());
        assertThat(dbEvent).isPresent();
        assertThat(dbEvent.get().getStatus()).isEqualTo("FAILED");
        assertThat(dbEvent.get().getRetryCount()).isEqualTo(1);
        
        String redacted = dbEvent.get().getErrorMessageRedacted();
        assertThat(redacted).isNotNull();
        assertThat(redacted).doesNotContain("xyz123abc456");
        assertThat(redacted).doesNotContain("some-secret-key-123");
        assertThat(redacted).contains("token=REDACTED");
        assertThat(redacted).contains("apiKey=REDACTED");

        // Fail again
        service.markFailed(event.getId(), "Another failure: Bearer super-secret-jwt");
        dbEvent = repository.findById(event.getId());
        assertThat(dbEvent.get().getRetryCount()).isEqualTo(2);
        assertThat(dbEvent.get().getErrorMessageRedacted()).doesNotContain("super-secret-jwt");
    }

    @Test
    void testMarkFailedRedactsJsonAndHeaderStyleSecrets() {
        AddonWebhookEvent event = service.recordReceived("my-addon", "ws-1", "TIME_ENTRY_CREATED", "dedupe-json", "hash-1");
        service.tryStartProcessing(event.getId());

        service.markFailed(event.getId(), """
                {
                  "authToken": "install-secret",
                  "x-addon-token": "addon-secret",
                  "X-Api-Key": "api-secret",
                  "password": "password-secret",
                  "message": "Authorization: Bearer bearer-secret"
                }
                X-Addon-Token: header-addon-secret
                """);

        Optional<AddonWebhookEvent> dbEvent = repository.findById(event.getId());
        assertThat(dbEvent).isPresent();
        String redacted = dbEvent.get().getErrorMessageRedacted();
        assertThat(redacted).doesNotContain("install-secret");
        assertThat(redacted).doesNotContain("addon-secret");
        assertThat(redacted).doesNotContain("api-secret");
        assertThat(redacted).doesNotContain("password-secret");
        assertThat(redacted).doesNotContain("bearer-secret");
        assertThat(redacted).doesNotContain("header-addon-secret");
    }

    private UUID getFutureValue(Future<UUID> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
