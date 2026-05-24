package com.cake.clockify.addon.mileage;

import com.cake.clockify.addon.core.auth.filter.ClockifyIframeAuthFilter;
import com.cake.clockify.addon.mileage.iframe.MileageIframeController;
import com.cake.clockify.addonsdk.clockify.ClockifySignatureParser;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MileageSecurityTest {
    @Test
    void mileageIframeRequiresAuthToken() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new MileageIframeController())
                .addFilters(new ClockifyIframeAuthFilter(new RejectingSignatureParser()))
                .build();

        mockMvc.perform(get("/iframe/mileage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mileageIframeUsesExternalCssAndJs() {
        String html = new MileageIframeController().mileage().getBody();

        assertThat(html).contains("href=\"/assets/mileage/settings.css\"");
        assertThat(html).contains("src=\"/assets/mileage/settings.js\" defer");
    }

    @Test
    void installationTokenNeverAppearsInIframeHtml() {
        String html = new MileageIframeController().mileage().getBody();

        assertThat(html).doesNotContain("secret-token-value");
        assertThat(html).doesNotContain("auth_token=");
        assertThat(html).doesNotContain("Bearer ");
    }

    @Test
    void mileageIframeDoesNotContainInlineScriptOrInlineStyle() {
        String html = new MileageIframeController().mileage().getBody();

        assertThat(html).doesNotContain("<style");
        assertThat(html).doesNotContain("<script>");
        assertThat(html).doesNotContain("onclick=");
        assertThat(html).doesNotContain("style=");
    }

    @Test
    void mileageJavascriptRemovesAuthTokenFromLocation() throws Exception {
        String javascript = Files.readString(Path.of("src/main/resources/static/assets/mileage/settings.js"));

        assertThat(javascript).contains("url.searchParams.delete(\"auth_token\")");
        assertThat(javascript).contains("history.replaceState");
    }

    @Test
    void mileageJavascriptUsesAuthorizationHeaderForBackendCalls() throws Exception {
        String javascript = Files.readString(Path.of("src/main/resources/static/assets/mileage/settings.js"));

        assertThat(javascript).contains("Authorization");
        assertThat(javascript).contains("\"Bearer \" + authToken");
    }

    @Test
    void nonAdminUserDoesNotSeeAdminControlsAfterClaimsLoad() throws Exception {
        String javascript = Files.readString(Path.of("src/main/resources/static/assets/mileage/settings.js"));

        assertThat(javascript).contains("workspaceRole");
        assertThat(javascript).contains("data-admin-only");
        assertThat(javascript).contains("element.hidden = !isAdmin");
    }

    @Test
    void mileageIframeDoesNotAskForRawUserId() {
        String html = new MileageIframeController().mileage().getBody();

        assertThat(html).doesNotContain("User ID");
        assertThat(html).contains("id=\"field-user\"");
        assertThat(html).contains("type=\"hidden\"");
    }

    @Test
    void mileageJavascriptDefaultsUserIdFromVerifiedClaims() throws Exception {
        String javascript = Files.readString(Path.of("src/main/resources/static/assets/mileage/settings.js"));

        assertThat(javascript).contains("claims.user || claims.userId");
        assertThat(javascript).contains("document.getElementById(\"field-user\").value = currentUserId");
    }

    private static final class RejectingSignatureParser extends ClockifySignatureParser {
        private RejectingSignatureParser() {
            super("mileage-for-clockify", publicKey());
        }

        @Override
        public Map<String, Object> parseClaims(String token) {
            throw new IllegalArgumentException("invalid signature");
        }

        private static RSAPublicKey publicKey() {
            try {
                return (RSAPublicKey) KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
