package com.cake.clockify.addon.mileage.api;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.core.auth.filter.ClockifyIframeAuthFilter;
import com.cake.clockify.addon.mileage.audit.MileageConversion;
import com.cake.clockify.addon.mileage.audit.MileageConversionRepository;
import com.cake.clockify.addon.mileage.audit.MileageConversionReservationRepository;
import com.cake.clockify.addon.mileage.audit.MileageConversionSource;
import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import com.cake.clockify.addon.mileage.calculation.MileageCalculator;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseGateway;
import com.cake.clockify.addon.mileage.clockify.CreateFlatExpenseCommand;
import com.cake.clockify.addon.mileage.clockify.UpdateFlatExpenseCommand;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MileageApiControllerTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private MileageSettingsService settingsService;
    private ClockifyExpenseGateway gateway;
    private MileageConversionRepository conversionRepository;
    private MileageConversionReservationRepository reservationRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        settingsService = mock(MileageSettingsService.class);
        gateway = mock(ClockifyExpenseGateway.class);
        conversionRepository = mock(MileageConversionRepository.class);
        reservationRepository = mock(MileageConversionReservationRepository.class);
        MileageApiController controller = new MileageApiController(
                settingsService,
                new MileageCalculator(),
                gateway,
                conversionRepository,
                reservationRepository,
                new MileageNoteService());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new MileageExceptionHandler(objectMapper))
                .build();
        when(reservationRepository.reserve(anyString(), anyString(), eq(MileageConversionSource.ADDON_FORM), eq("ADDON_FORM")))
                .thenAnswer(invocation -> UUID.randomUUID());
        when(reservationRepository.reserve(any(UUID.class), anyString(), anyString(),
                eq(MileageConversionSource.ADDON_FORM), eq("ADDON_FORM")))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(conversionRepository.findByIdAndWorkspaceId(any(UUID.class), anyString())).thenAnswer(invocation -> {
            MileageConversion conversion = new MileageConversion();
            conversion.setId(invocation.getArgument(0));
            conversion.setWorkspaceId(invocation.getArgument(1));
            return Optional.of(conversion);
        });
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
                .andExpect(jsonPath("$.calculatedAmount").value("24.497"))
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
                        reservationRepository,
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
                .andExpect(jsonPath("$.calculatedAmount").value("24.497"))
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
                        .param("projectId", "project-1")
                        .param("miles", "37.4")
                        .param("notes", "Client site visit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenseId").value("exp-file"));
    }

    @Test
    void createMileageExpenseJsonDefaultsBillableToTrueWhenOmitted() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));
        when(gateway.createFlatExpense(eq("ws-api"), any(CreateFlatExpenseCommand.class))).thenReturn(createdExpense("exp-1"));

        mockMvc.perform(post("/api/mileage/expenses")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"2026-05-24","projectId":"project-1","miles":"37.4"}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<CreateFlatExpenseCommand> command = ArgumentCaptor.forClass(CreateFlatExpenseCommand.class);
        verify(gateway).createFlatExpense(eq("ws-api"), command.capture());
        assertThat(command.getValue().billable()).isTrue();
    }

    @Test
    void createMileageExpenseMultipartDefaultsBillableToTrueWhenOmitted() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));
        when(gateway.createFlatExpenseWithReceipt(eq("ws-api"), any(CreateFlatExpenseCommand.class), eq("receipt.png"), eq("image/png"), any()))
                .thenReturn(createdExpense("exp-file"));
        MockMultipartFile file = new MockMultipartFile("file", "receipt.png", "image/png", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/mileage/expenses")
                        .file(file)
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .param("date", "2026-05-24")
                        .param("projectId", "project-1")
                        .param("miles", "37.4"))
                .andExpect(status().isOk());

        ArgumentCaptor<CreateFlatExpenseCommand> command = ArgumentCaptor.forClass(CreateFlatExpenseCommand.class);
        verify(gateway).createFlatExpenseWithReceipt(eq("ws-api"), command.capture(), eq("receipt.png"), eq("image/png"), any());
        assertThat(command.getValue().billable()).isTrue();
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
        assertThat(command.getValue().userId()).isEqualTo("user-claims");
        assertThat(command.getValue().taskId()).isNull();
        assertThat(command.getValue().amount()).isEqualByComparingTo(new BigDecimal("24.50"));
    }

    @Test
    void createMileageExpenseWithSingleMileageCategorySendsMilesQuantityToClockify() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(singleCategorySettings());
        when(gateway.createFlatExpense(eq("ws-api"), any(CreateFlatExpenseCommand.class))).thenReturn(createdExpense("exp-1"));

        mockMvc.perform(post("/api/mileage/expenses")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("1", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calculatedAmount").value("0.725"))
                .andExpect(jsonPath("$.roundedAmount").value("0.73"));

        ArgumentCaptor<CreateFlatExpenseCommand> command = ArgumentCaptor.forClass(CreateFlatExpenseCommand.class);
        verify(gateway).createFlatExpense(eq("ws-api"), command.capture());
        assertThat(command.getValue().categoryId()).isEqualTo("cat-mileage");
        assertThat(command.getValue().amount()).isEqualByComparingTo(new BigDecimal("1"));
        assertThat(command.getValue().amountIsQuantity()).isTrue();
    }

    @Test
    void createAnnotatesNoteWithClockifyCategoryChargeWhenItDiffersFromRoundedAmount() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));
        // Clockify charges miles × the category's integer-cent unit price; here that lands at $89.90 while the
        // add-on rate amount is $24.50, so the reconciled note must document the real category charge.
        when(gateway.createFlatExpense(eq("ws-api"), any(CreateFlatExpenseCommand.class)))
                .thenReturn(createdExpense("exp-1", 8990));

        mockMvc.perform(post("/api/mileage/expenses")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("37.4", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenseId").value("exp-1"))
                .andExpect(jsonPath("$.roundedAmount").value("24.50"));

        ArgumentCaptor<UpdateFlatExpenseCommand> update = ArgumentCaptor.forClass(UpdateFlatExpenseCommand.class);
        verify(gateway).updateFlatExpense(eq("ws-api"), eq("exp-1"), update.capture());
        assertThat(update.getValue().notes()).contains("(Clockify category charge: 89.90)");
    }

    @Test
    void createDoesNotReissueUpdateWhenClockifyChargeMatchesRoundedAmount() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));
        // Clockify total (2450 cents = $24.50) matches the add-on rate amount, so no reconciling note is needed
        // and no extra updateFlatExpense round-trip should fire.
        when(gateway.createFlatExpense(eq("ws-api"), any(CreateFlatExpenseCommand.class)))
                .thenReturn(createdExpense("exp-1", 2450));

        mockMvc.perform(post("/api/mileage/expenses")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("37.4", null)))
                .andExpect(status().isOk());

        verify(gateway, never()).updateFlatExpense(any(), any(), any());
    }

    @Test
    void createMileageExpenseIgnoresTamperedUserIdAndUsesVerifiedClaimsUser() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));
        when(gateway.createFlatExpense(eq("ws-api"), any(CreateFlatExpenseCommand.class))).thenReturn(createdExpense("exp-1"));

        mockMvc.perform(post("/api/mileage/expenses")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"2026-05-24","userId":"other-user","projectId":"project-1","miles":"37.4","billable":true}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<CreateFlatExpenseCommand> command = ArgumentCaptor.forClass(CreateFlatExpenseCommand.class);
        verify(gateway).createFlatExpense(eq("ws-api"), command.capture());
        assertThat(command.getValue().userId()).isEqualTo("user-claims");

        ArgumentCaptor<MileageConversion> saved = ArgumentCaptor.forClass(MileageConversion.class);
        verify(conversionRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo("user-claims");
    }

    @Test
    void createMileageExpenseCreatesAddonFormAuditRow() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));
        when(gateway.createFlatExpense(eq("ws-api"), any(CreateFlatExpenseCommand.class))).thenReturn(createdExpense("exp-1"));

        String response = mockMvc.perform(post("/api/mileage/expenses")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("37.4", null)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String conversionId = objectMapper.readTree(response).path("conversionId").asText();

        ArgumentCaptor<MileageConversion> saved = ArgumentCaptor.forClass(MileageConversion.class);
        verify(conversionRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getId()).hasToString(conversionId);
        assertThat(saved.getValue().getWorkspaceId()).isEqualTo("ws-api");
        assertThat(saved.getValue().getSource()).isEqualTo(MileageConversionSource.ADDON_FORM);
        assertThat(saved.getValue().getStatus()).isEqualTo(MileageConversionStatus.CONVERTED);
        assertThat(saved.getValue().getExpenseId()).isEqualTo("exp-1");
        assertThat(saved.getValue().getUserId()).isEqualTo("user-claims");
        assertThat(saved.getValue().getExpenseDate()).isEqualTo(LocalDate.parse("2026-05-24"));
        assertThat(saved.getValue().getNoteMarker()).contains(conversionId);
    }

    @Test
    void createExpenseMergesAuditRowWhenWebhookReservedExpenseFirst() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(new MileageSettingsValidation(
                "ws-api", true, true, new BigDecimal("0.655"), "mi",
                "cat-input", "cat-output", RoundingMode.HALF_UP,
                true, true, true, false, false,
                "Mileage {{marker}} {{miles}}", List.of()));
        when(gateway.createFlatExpense(eq("ws-api"), any(CreateFlatExpenseCommand.class))).thenReturn(createdExpense("exp-race"));
        when(gateway.updateFlatExpense(eq("ws-api"), eq("exp-race"), any(UpdateFlatExpenseCommand.class)))
                .thenReturn(createdExpense("exp-race"));
        UUID existingId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        MileageConversion existing = new MileageConversion();
        existing.setId(existingId);
        existing.setWorkspaceId("ws-api");
        existing.setExpenseId("exp-race");
        existing.setSource(MileageConversionSource.WEBHOOK_CREATED);
        existing.setStatus(MileageConversionStatus.SKIPPED);
        when(reservationRepository.reserve(any(UUID.class), eq("ws-api"), eq("exp-race"),
                eq(MileageConversionSource.ADDON_FORM), eq("ADDON_FORM")))
                .thenReturn(existingId);
        when(conversionRepository.findByIdAndWorkspaceId(existingId, "ws-api")).thenReturn(Optional.of(existing));

        mockMvc.perform(post("/api/mileage/expenses")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("37.4", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenseId").value("exp-race"))
                .andExpect(jsonPath("$.conversionId").value(existingId.toString()));

        ArgumentCaptor<MileageConversion> saved = ArgumentCaptor.forClass(MileageConversion.class);
        verify(conversionRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(existingId);
        assertThat(saved.getValue().getSource()).isEqualTo(MileageConversionSource.ADDON_FORM);
        assertThat(saved.getValue().getStatus()).isEqualTo(MileageConversionStatus.CONVERTED);
        assertThat(saved.getValue().getUserId()).isEqualTo("user-claims");
        assertThat(saved.getValue().getNoteMarker()).contains(existingId.toString());

        ArgumentCaptor<UpdateFlatExpenseCommand> repair = ArgumentCaptor.forClass(UpdateFlatExpenseCommand.class);
        verify(gateway).updateFlatExpense(eq("ws-api"), eq("exp-race"), repair.capture());
        assertThat(repair.getValue().notes()).contains(existingId.toString());
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
    void createMileageExpenseRejectsInvalidJsonDate() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));

        mockMvc.perform(post("/api/mileage/expenses")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"05/24/2026","projectId":"project-1","miles":"37.4"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("date must use YYYY-MM-DD"));

        verify(gateway, never()).createFlatExpense(any(), any());
    }

    @Test
    void createMileageExpenseRejectsInvalidMultipartDate() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));
        MockMultipartFile file = new MockMultipartFile("file", "receipt.png", "image/png", new byte[] {1});

        mockMvc.perform(multipart("/api/mileage/expenses")
                        .file(file)
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .param("date", "05/24/2026")
                        .param("miles", "37.4"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("date must use YYYY-MM-DD"));

        verify(gateway, never()).createFlatExpenseWithReceipt(any(), any(), any(), any(), any());
    }

    @Test
    void createMileageExpenseMultipartRejectsInvalidBillableValue() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));
        MockMultipartFile file = new MockMultipartFile("file", "receipt.png", "image/png", new byte[] {1});

        mockMvc.perform(multipart("/api/mileage/expenses")
                        .file(file)
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .param("date", "2026-05-24")
                        .param("miles", "37.4")
                        .param("billable", "maybe"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("billable must be true or false"));

        verify(gateway, never()).createFlatExpenseWithReceipt(any(), any(), any(), any(), any());
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
    void createContextExposesRateOverridePolicyToMembers() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));

        mockMvc.perform(get("/api/mileage/create-context")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value("0.655"))
                .andExpect(jsonPath("$.unit").value("mi"))
                .andExpect(jsonPath("$.allowUserRateOverride").value(false))
                .andExpect(jsonPath("$.complete").value(true));
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
                        .param("miles", "37.4")
                        .param("auth_token", "secret-token-value")
                        .param("addonToken", "secret-addon-value")
                        .param("Authorization", "Bearer secret-auth-value"))
                .andExpect(status().isOk());

        ArgumentCaptor<CreateFlatExpenseCommand> command = ArgumentCaptor.forClass(CreateFlatExpenseCommand.class);
        verify(gateway).createFlatExpenseWithReceipt(eq("ws-api"), command.capture(), eq("receipt.png"), eq("image/png"), any());
        assertThat(command.getValue().userId()).isEqualTo("user-claims");
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
                        .param("miles", "37.4"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unsupported receipt file type"));

        verify(gateway, never()).createFlatExpenseWithReceipt(any(), any(), any(), any(), any());
    }

    private static String createBody(String miles, String rate) {
        String rateField = rate == null ? "" : ",\"rate\":\"" + rate + "\"";
        return """
                {"date":"2026-05-24","projectId":"project-1","miles":"%s"%s,"billable":true,"notes":"Client site visit"}
                """.formatted(miles, rateField);
    }

    private static ObjectNode createdExpense(String id) {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.createObjectNode().put("id", id);
    }

    private static ObjectNode createdExpense(String id, long totalCents) {
        return createdExpense(id).put("total", totalCents);
    }

    private static MileageSettingsValidation settings(boolean allowOverride) {
        return new MileageSettingsValidation("ws-api", true, true, new BigDecimal("0.655"), "mi",
                "cat-input", "cat-output", RoundingMode.HALF_UP, true, true, true, false, allowOverride, null, List.of());
    }

    private static MileageSettingsValidation singleCategorySettings() {
        return new MileageSettingsValidation("ws-api", true, true, new BigDecimal("0.725"), "mile",
                "cat-mileage", "cat-mileage", RoundingMode.HALF_UP, true, true, false, false, false, null, List.of());
    }

    private static NormalizedClaims claims() {
        return claims("OWNER");
    }

    private static NormalizedClaims claims(String role) {
        return new NormalizedClaims("ws-api", "mileage-for-clockify", "https://backend.example.test",
                "https://reports.example.test", null, null, "user-claims", role, "en", "DEFAULT", "UTC", Instant.now());
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
