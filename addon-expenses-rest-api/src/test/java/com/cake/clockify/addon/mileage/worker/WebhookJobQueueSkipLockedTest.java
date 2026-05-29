package com.cake.clockify.addon.mileage.worker;

import com.cake.clockify.addon.db.entity.AddonWebhookJob;
import com.cake.clockify.addon.db.repository.AddonWebhookJobRepository;
import com.cake.clockify.addon.db.service.AddonWebhookJobClaimService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G1 contract test: two workers polling the same queue MUST never claim the same row.
 * Verified by:
 * <ul>
 *   <li>Each {@link AddonWebhookJobClaimService#claimNext()} runs inside a transaction
 *       that holds a row lock until commit, with {@code FOR UPDATE SKIP LOCKED}.</li>
 *   <li>Concurrent claims see disjoint UUID sets and together cover every PENDING row.</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "addon.key=mileage-for-clockify",
        "addon.name=Mileage for Clockify",
        "addon.description=Create and convert precise mileage reimbursements into real Clockify flat expenses.",
        "addon.base-url=https://mileage.example.com",
        "addon.crypto.active-key-id=k1",
        "addon.crypto.keys.k1=00000000000000000000000000000000000000000000000000000000000000aa",
        "mileage.worker.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.default_schema=mileage_skiplocked",
        "spring.flyway.schemas=mileage_skiplocked",
        "spring.flyway.create-schemas=true"
})
@Testcontainers
class WebhookJobQueueSkipLockedTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired AddonWebhookJobRepository repository;
    @Autowired AddonWebhookJobClaimService claimService;

    @Test
    void twoConcurrentWorkersClaimDisjointSetsAndProcessEachJobExactlyOnce() throws Exception {
        repository.deleteAll();
        int totalJobs = 50;
        List<UUID> enqueuedIds = new ArrayList<>();
        for (int i = 0; i < totalJobs; i++) {
            AddonWebhookJob job = pending("ws-" + i, "EXPENSE_CREATED",
                    "dedupe-" + i, "{\"workspaceId\":\"ws-" + i + "\"}");
            enqueuedIds.add(repository.saveAndFlush(job).getId());
        }

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Set<UUID> claimedByA = java.util.concurrent.ConcurrentHashMap.newKeySet();
        Set<UUID> claimedByB = java.util.concurrent.ConcurrentHashMap.newKeySet();
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger remainingAttempts = new AtomicInteger(totalJobs * 4);

        pool.submit(() -> drain(start, claimedByA, remainingAttempts));
        pool.submit(() -> drain(start, claimedByB, remainingAttempts));
        start.countDown();
        pool.shutdown();
        boolean finished = pool.awaitTermination(30, TimeUnit.SECONDS);
        assertThat(finished).as("workers should drain queue in 30s").isTrue();

        // Core SKIP LOCKED guarantee: any single row appears in AT MOST one bucket.
        Set<UUID> intersection = new HashSet<>(claimedByA);
        intersection.retainAll(claimedByB);
        assertThat(intersection)
                .as("rows must not be claimed by both workers")
                .isEmpty();

        // Every enqueued row gets claimed exactly once across both workers.
        Set<UUID> union = new HashSet<>(claimedByA);
        union.addAll(claimedByB);
        assertThat(union)
                .as("every enqueued row should have been claimed by exactly one worker")
                .containsExactlyInAnyOrderElementsOf(enqueuedIds);

        // Worker B may have started after A drained; that is fine for the SKIP LOCKED
        // contract. But the deeper invariant — no row claimed twice — is enforced per-row
        // by attempts == 1.
        for (AddonWebhookJob job : repository.findAllById(enqueuedIds)) {
            assertThat(job.getStatus())
                    .as("job %s should have been claimed", job.getId())
                    .isEqualTo(AddonWebhookJob.STATUS_CLAIMED);
            assertThat(job.getAttempts())
                    .as("job %s should have been claimed exactly once (no double-claim)", job.getId())
                    .isEqualTo(1);
        }
    }

    private void drain(CountDownLatch start, Set<UUID> bucket, AtomicInteger remainingAttempts) {
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        while (remainingAttempts.decrementAndGet() >= 0) {
            var claimed = claimService.claimNext();
            if (claimed.isEmpty()) {
                if (repository.countByStatus(AddonWebhookJob.STATUS_PENDING) == 0L) {
                    return;
                }
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                continue;
            }
            bucket.add(claimed.get().getId());
            // Simulate handler work so the second worker has a chance to claim concurrently.
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static AddonWebhookJob pending(String workspaceId, String eventType, String dedupeKey, String claimsJson) {
        AddonWebhookJob job = new AddonWebhookJob();
        job.setAddonKey("mileage-for-clockify");
        job.setWorkspaceId(workspaceId);
        job.setEventType(eventType);
        job.setDedupeKey(dedupeKey);
        job.setClaimsJson(claimsJson);
        job.setStatus(AddonWebhookJob.STATUS_PENDING);
        job.setPayload(("{\"id\":\"" + dedupeKey + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        job.setCreatedAt(Instant.now());
        return job;
    }
}
