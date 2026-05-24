package com.cake.clockify.addon.mileage.api;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.core.auth.filter.ClockifyIframeAuthFilter;
import com.cake.clockify.addon.mileage.audit.MileageConversion;
import com.cake.clockify.addon.mileage.audit.MileageConversionRepository;
import com.cake.clockify.addon.mileage.audit.MileageConversionSource;
import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import com.cake.clockify.addon.mileage.calculation.MileageCalculator;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseGateway;
import com.cake.clockify.addon.mileage.clockify.CreateFlatExpenseCommand;
import com.cake.clockify.addon.mileage.note.MileageNoteService;
import com.cake.clockify.addon.mileage.settings.MileageSettingsService;
import com.cake.clockify.addon.mileage.settings.MileageSettingsValidation;
import com.cake.clockify.addonsdk.clockify.ClockifySignatureParser;
import com.cake.clockify.client.ClockifyApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MileageApiControllerTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private MileageSettingsService settingsService;
    private ClockifyExpenseGateway gateway;
    private MileageConversionRepository conversionRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        settingsService = mock(MileageSettingsService.class);
        gateway = mock(ClockifyExpenseGateway.class);
        conversionRepository = mock(MileageConversionRepository.class);
        MileageApiController controller = new MileageApiController(
                settingsService,
                new MileageCalculator(),
                gateway,
                conversionRepository,
                new MileageNoteService());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new MileageExceptionHandler(objectMapper))
                .build();
        when(conversionRepository.saveAndFlush(any(MileageConversion.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void previewReturnsCalculatedAndRoundedAmounts() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));

        mockMvc.perform(post("/api/mileage/preview")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""" 
                                {"miles":"37.4"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calculatedAmount").value("24.4970"))
                .andExpect(jsonPath("$.roundedAmount").value("24.50"));
    }

    @Test
    void previewAcceptsBearerTokenThroughIframeAuthFilter() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));
        MockMvc filteredMockMvc = MockMvcBuilders.standaloneSetup(new MileageApiController(
                        settingsService,
                        new MileageCalculator(),
                        gateway,
                        conversionRepository,
                        new MileageNoteService()))
                .setControllerAdvice(new MileageExceptionHandler(objectMapper))
                .addFilters(new ClockifyIframeAuthFilter(new ValidUserSignatureParser()))
                .build();

        filteredMockMvc.perform(post("/api/mileage/preview")
                        .header("Authorization", "Bearer valid-user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"miles":"37.4"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calculatedAmount").value("24.4970"))
                .andExpect(jsonPath("$.roundedAmount").value("24.50"));
    }

    @Test
    void createMileageExpenseJsonCreatesFlatClockifyExpense() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));
        when(gateway.createFlatExpense(eq("ws-api"), any(CreateFlatExpenseCommand.class))).thenReturn(createdExpense("exp-1"));

        mockMvc.perform(post("/api/mileage/expenses")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("37.4", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenseId").value("exp-1"))
                .andExpect(jsonPath("$.roundedAmount").value("24.50"));
    }

    @Test
    void createMileageExpenseMultipartForwardsReceipt() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));
        when(gateway.createFlatExpenseWithReceipt(eq("ws-api"), any(CreateFlatExpenseCommand.class), eq("receipt.png"), eq("image/png"), any()))
                .thenReturn(createdExpense("exp-file"));
        MockMultipartFile file = new MockMultipartFile("file", "receipt.png", "image/png", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/mileage/expenses")
                        .file(file)
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .param("date", "2026-05-24")
                        .param("userId", "user-1")
                        .param("projectId", "project-1")
                        .param("taskId", "task-1")
                        .param("miles", "37.4")
                        .param("notes", "Client site visit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenseId").value("exp-file"));
    }

    @Test
    void createMileageExpenseUsesConfiguredOutputCategory() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));
        when(gateway.createFlatExpense(eq("ws-api"), any(CreateFlatExpenseCommand.class))).thenReturn(createdExpense("exp-1"));

        mockMvc.perform(post("/api/mileage/expenses")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("37.4", "99.99")))
                .andExpect(status().isOk());

        ArgumentCaptor<CreateFlatExpenseCommand> command = ArgumentCaptor.forClass(CreateFlatExpenseCommand.class);
        verify(gateway).createFlatExpense(eq("ws-api"), command.capture());
        assertThat(command.getValue().categoryId()).isEqualTo("cat-output");
        assertThat(command.getValue().amount()).isEqualByComparingTo(new BigDecimal("24.50"));
    }

    @Test
    void createMileageExpenseCreatesAddonFormAuditRow() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));
        when(gateway.createFlatExpense(eq("ws-api"), any(CreateFlatExpenseCommand.class))).thenReturn(createdExpense("exp-1"));

        mockMvc.perform(post("/api/mileage/expenses")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("37.4", null)))
                .andExpect(status().isOk());

        ArgumentCaptor<MileageConversion> saved = ArgumentCaptor.forClass(MileageConversion.class);
        verify(conversionRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getWorkspaceId()).isEqualTo("ws-api");
        assertThat(saved.getValue().getSource()).isEqualTo(MileageConversionSource.ADDON_FORM);
        assertThat(saved.getValue().getStatus()).isEqualTo(MileageConversionStatus.CONVERTED);
        assertThat(saved.getValue().getExpenseId()).isEqualTo("exp-1");
    }

    @Test
    void createMileageExpenseRejectsMissingSettings() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(new MileageSettingsValidation(
                "ws-api", true, false, null, "mi", null, null, RoundingMode.HALF_UP,
                true, true, true, false, false, null, List.of("outputCategoryId is required")));

        mockMvc.perform(post("/api/mileage/expenses")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("37.4", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("configuration_missing"));

        verify(gateway, never()).createFlatExpense(any(), any());
    }

    @Test
    void createMileageExpenseRejectsUserRateOverrideWhenDisabled() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));
        when(gateway.createFlatExpense(eq("ws-api"), any(CreateFlatExpenseCommand.class))).thenReturn(createdExpense("exp-1"));

        mockMvc.perform(post("/api/mileage/expenses")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("37.4", "99.99")))
                .andExpect(status().isOk());

        ArgumentCaptor<MileageConversion> saved = ArgumentCaptor.forClass(MileageConversion.class);
        verify(conversionRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getRate()).isEqualByComparingTo(new BigDecimal("0.655"));
    }

    @Test
    void validationErrorDoesNotLeakStackTrace() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenThrow(new ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "Mileage output category is not configured"));

        String response = mockMvc.perform(post("/api/mileage/preview")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"miles\":\"37.4\"}"))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).contains("Mileage output category is not configured");
        assertThat(response).doesNotContain("ResponseStatusException");
        assertThat(response).doesNotContain("at com.cake");
    }

    @Test
    void installationTokenNeverAppearsInMileageApiResponse() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));
        when(gateway.createFlatExpenseWithReceipt(eq("ws-api"), any(CreateFlatExpenseCommand.class), eq("receipt.png"), eq("image/png"), any()))
                .thenReturn(createdExpense("exp-file"));
        MockMultipartFile file = new MockMultipartFile("file", "receipt.png", "image/png", new byte[] {1});

        String response = mockMvc.perform(multipart("/api/mileage/expenses")
                        .file(file)
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .param("date", "2026-05-24")
                        .param("userId", "user-1")
                        .param("miles", "37.4")
                        .param("auth_token", "secret-token-value")
                        .param("addonToken", "secret-addon-value")
                        .param("Authorization", "Bearer secret-auth-value"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain("secret-token-value");
        assertThat(response).doesNotContain("secret-addon-value");
        assertThat(response).doesNotContain("secret-auth-value");
    }

    @Test
    void multipartCreateDropsAuthTokenAuthorizationAndAddonTokenFields() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));
        when(gateway.createFlatExpenseWithReceipt(eq("ws-api"), any(CreateFlatExpenseCommand.class), eq("receipt.png"), eq("image/png"), any()))
                .thenReturn(createdExpense("exp-file"));
        MockMultipartFile file = new MockMultipartFile("file", "receipt.png", "image/png", new byte[] {1});

        mockMvc.perform(multipart("/api/mileage/expenses")
                        .file(file)
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .param("date", "2026-05-24")
                        .param("userId", "user-1")
                        .param("miles", "37.4")
                        .param("auth_token", "secret-token-value")
                        .param("addonToken", "secret-addon-value")
                        .param("Authorization", "Bearer secret-auth-value"))
                .andExpect(status().isOk());

        ArgumentCaptor<CreateFlatExpenseCommand> command = ArgumentCaptor.forClass(CreateFlatExpenseCommand.class);
        verify(gateway).createFlatExpenseWithReceipt(eq("ws-api"), command.capture(), eq("receipt.png"), eq("image/png"), any());
        assertThat(command.getValue().userId()).isEqualTo("user-1");
        assertThat(command.getValue().amount()).isEqualByComparingTo(new BigDecimal("24.50"));
    }

    @Test
    void errorsDoNotExposeStackTraceOrRawClockifyBody() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));
        when(gateway.createFlatExpense(eq("ws-api"), any(CreateFlatExpenseCommand.class)))
                .thenThrow(ClockifyApiException.forStatus(400, Map.of(), "{\"message\":\"Invalid mileage\",\"token\":\"secret-upstream-value\"}"));

        String response = mockMvc.perform(post("/api/mileage/expenses")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("37.4", null)))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).contains("clockify_api_error");
        assertThat(response).contains("Invalid mileage");
        assertThat(response).doesNotContain("secret-upstream-value");
        assertThat(response).doesNotContain("ClockifyApiException");
        assertThat(response).doesNotContain("at com.cake");
    }

    @Test
    void fileUploadRejectsFilesLargerThanTenMegabytes() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));
        MockMultipartFile file = new MockMultipartFile("file", "receipt.png", "image/png", new byte[(10 * 1024 * 1024) + 1]);

        mockMvc.perform(multipart("/api/mileage/expenses")
                        .file(file)
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .param("date", "2026-05-24")
                        .param("userId", "user-1")
                        .param("miles", "37.4"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Receipt file exceeds 10 MB"));

        verify(gateway, never()).createFlatExpenseWithReceipt(any(), any(), any(), any(), any());
    }

    @Test
    void fileUploadRejectsUnsafeContentTypeBeforeForwarding() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));
        MockMultipartFile file = new MockMultipartFile("file", "receipt.txt", "text/plain", new byte[] {1});

        mockMvc.perform(multipart("/api/mileage/expenses")
                        .file(file)
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .param("date", "2026-05-24")
                        .param("userId", "user-1")
                        .param("miles", "37.4"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unsupported receipt file type"));

        verify(gateway, never()).createFlatExpenseWithReceipt(any(), any(), any(), any(), any());
    }

    private static String createBody(String miles, String rate) {
        String rateField = rate == null ? "" : ",\"rate\":\"" + rate + "\"";
        return """
                {"date":"2026-05-24","userId":"user-1","projectId":"project-1","taskId":"task-1","miles":"%s"%s,"billable":true,"notes":"Client site visit"}
                """.formatted(miles, rateField);
    }

    private static ObjectNode createdExpense(String id) {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.createObjectNode().put("id", id);
    }

    private static MileageSettingsValidation settings(boolean allowOverride) {
        return new MileageSettingsValidation("ws-api", true, true, new BigDecimal("0.655"), "mi",
                "cat-input", "cat-output", RoundingMode.HALF_UP, true, true, true, false, allowOverride, null, List.of());
    }

    private static NormalizedClaims claims() {
        return new NormalizedClaims("ws-api", "mileage-for-clockify", "https://backend.example.test",
                "https://reports.example.test", null, null, "user-claims", "OWNER", "en", "DEFAULT", "UTC", Instant.now());
    }

    private static final class ValidUserSignatureParser extends ClockifySignatureParser {
        private ValidUserSignatureParser() {
            super("mileage-for-clockify", publicKey());
        }

        @Override
        public Map<String, Object> parseClaims(String token) {
            if (!"valid-user-token".equals(token)) {
                throw new IllegalArgumentException("invalid signature");
            }
            return Map.of(
                    "workspaceId", "ws-api",
                    "addonId", "mileage-for-clockify",
                    "backendUrl", "https://api.clockify.me",
                    "userId", "user-1",
                    "exp", 9999999999L
            );
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
