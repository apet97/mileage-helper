package com.cake.clockify.addon.db.service;

import com.cake.clockify.addon.db.AbstractDbTest;
import com.cake.clockify.addon.db.entity.AddonInstallation;
import com.cake.clockify.addon.db.repository.AddonInstallationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AddonInstallationServiceTest extends AbstractDbTest {

    @Autowired
    private AddonInstallationService service;

    @Autowired
    private AddonInstallationRepository repository;

    @Test
    void testMarkInstalledCreatesNewInstallation() {
        AddonInstallation inst = service.markInstalled(
                "ws-1", "test-addon", "addon-id-123", "raw-token-abc",
                "http://backend.clockify.me", "http://reports.clockify.me", "user-123");

        assertThat(inst).isNotNull();
        assertThat(inst.getWorkspaceId()).isEqualTo("ws-1");
        assertThat(inst.getAddonKey()).isEqualTo("test-addon");
        assertThat(inst.getAddonId()).isEqualTo("addon-id-123");
        assertThat(inst.getBackendUrl()).isEqualTo("http://backend.clockify.me");
        assertThat(inst.getReportsUrl()).isEqualTo("http://reports.clockify.me");
        assertThat(inst.getInstallerUserId()).isEqualTo("user-123");
        assertThat(inst.getStatus()).isEqualTo("ACTIVE");

        // Token must be encrypted and not store raw token
        assertThat(inst.getInstallationTokenEnc()).isNotEqualTo("raw-token-abc");

        // Verify record exists in DB
        Optional<AddonInstallation> dbInst = repository.findById("ws-1");
        assertThat(dbInst).isPresent();
    }

    @Test
    void testMarkInstalledIsIdempotent() {
        service.markInstalled("ws-1", "test-addon", "addon-id-123", "raw-token-1", "http://b1", "http://r1", "u1");
        
        // Verify we can call markInstalled again without error, updating values
        AddonInstallation inst = service.markInstalled("ws-1", "test-addon", "addon-id-123", "raw-token-2", "http://b2", "http://r2", "u2");

        assertThat(inst.getBackendUrl()).isEqualTo("http://b2");
        assertThat(repository.findById("ws-1")).isPresent();
    }

    @Test
    void testMarkUninstalledUpdatesStatus() {
        service.markInstalled("ws-1", "test-addon", "addon-id-123", "raw-token-abc", "http://b", "http://r", "u");
        service.markUninstalled("ws-1");

        Optional<AddonInstallation> inst = repository.findById("ws-1");
        assertThat(inst).isPresent();
        assertThat(inst.get().getStatus()).isEqualTo("DELETED");
    }

    @Test
    void testMarkUninstalledIsIdempotent() {
        // Uninstalled non-existing workspace should do nothing and not throw
        service.markUninstalled("ws-non-exist");

        // Uninstalled existing workspace twice should keep status DELETED
        service.markInstalled("ws-1", "test-addon", "addon-id-123", "raw-token-abc", "http://b", "http://r", "u");
        service.markUninstalled("ws-1");
        service.markUninstalled("ws-1");

        assertThat(repository.findById("ws-1").get().getStatus()).isEqualTo("DELETED");
    }

    @Test
    void testGetRequiredInstallationReturnsRecord() {
        service.markInstalled("ws-1", "test-addon", "addon-id-123", "raw-token-abc", "http://b", "http://r", "u");
        AddonInstallation inst = service.getRequiredInstallation("ws-1");
        assertThat(inst).isNotNull();
    }

    @Test
    void testGetRequiredInstallationThrowsWhenNotFound() {
        assertThatThrownBy(() -> service.getRequiredInstallation("ws-non-exist"))
                .isInstanceOf(IllegalArgumentException.class);

        // Should also throw when installation is DELETED
        service.markInstalled("ws-1", "test-addon", "addon-id-123", "raw-token-abc", "http://b", "http://r", "u");
        service.markUninstalled("ws-1");
        assertThatThrownBy(() -> service.getRequiredInstallation("ws-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testFindInstallationReturnsOptional() {
        assertThat(service.findInstallation("ws-non-exist")).isEmpty();

        service.markInstalled("ws-1", "test-addon", "addon-id-123", "raw-token-abc", "http://b", "http://r", "u");
        assertThat(service.findInstallation("ws-1")).isPresent();

        // Should be empty when installation is DELETED
        service.markUninstalled("ws-1");
        assertThat(service.findInstallation("ws-1")).isEmpty();
    }

    @Test
    void testIsInstalledReturnsCorrectly() {
        assertThat(service.isInstalled("ws-1")).isFalse();

        service.markInstalled("ws-1", "test-addon", "addon-id-123", "raw-token-abc", "http://b", "http://r", "u");
        assertThat(service.isInstalled("ws-1")).isTrue();

        service.markUninstalled("ws-1");
        assertThat(service.isInstalled("ws-1")).isFalse();
    }
}
