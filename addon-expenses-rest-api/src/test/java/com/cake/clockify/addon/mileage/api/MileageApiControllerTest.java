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
import com.cake.clockify.addon.mileage.policy.MileageRatePolicyService;
import com.cake.clockify.addon.mileage.policy.MileageRateResolution;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MileageApiControllerTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private MileageSettingsService settingsService;
    private MileageRatePolicyService ratePolicyService;
    private ClockifyExpenseGateway gateway;
    private MileageConversionRepository conversionRepository;
    private MileageConversionReservationRepository reservationRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        settingsService = mock(MileageSettingsService.class);
        ratePolicyService = mock(MileageRatePolicyService.class);
        gateway = mock(ClockifyExpenseGateway.class);
        conversionRepository = mock(MileageConversionRepository.class);
        reservationRepository = mock(MileageConversionReservationRepository.class);
        MileageApiController controller = new MileageApiController(
                settingsService,
                ratePolicyService,
                new MileageDateRangeResolver(Clock.fixed(Instant.parse("2026-05-24T12:00:00Z"), ZoneOffset.UTC)),
                new MileageCalculator(),
                gateway,
                conversionRepository,
                reservationRepository,
                new MileageNoteService());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new MileageExceptionHandler(objectMapper))
                .build();
        when(ratePolicyService.resolveRate(anyString(), any(), any(MileageSettingsValidation.class)))
                .thenAnswer(invocation -> settingsFallback(invocation.getArgument(2)));
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
                                {"date":"2026-05-24","miles":"37.4"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calculatedAmount").value("24.497"))
                .andExpect(jsonPath("$.roundedAmount").value("24.50"))
                .andExpect(jsonPath("$.rateSource").value("SETTINGS_FALLBACK"));
    }

    @Test
    void previewUsesPolicyMatchingDate() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));
        UUID policyId = UUID.fromString("00000000-0000-0000-0000-000000000701");
        when(ratePolicyService.resolveRate(eq("ws-api"), eq(LocalDate.parse("2026-05-24")), any(MileageSettingsValidation.class)))
                .thenReturn(policyResolution(policyId, "2026 mileage rate", "0.700"));

        mockMvc.perform(post("/api/mileage/preview")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"2026-05-24","miles":"10"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value("0.7"))
                .andExpect(jsonPath("$.calculatedAmount").value("7"))
                .andExpect(jsonPath("$.roundedAmount").value("7.00"))
                .andExpect(jsonPath("$.rateSource").value("POLICY"))
                .andExpect(jsonPath("$.ratePolicyId").value(policyId.toString()))
                .andExpect(jsonPath("$.ratePolicyName").value("2026 mileage rate"));
    }

    @Test
    void previewRequiresIsoDate() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));

        mockMvc.perform(post("/api/mileage/preview")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"miles":"37.4"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("date must use YYYY-MM-DD"));

        mockMvc.perform(post("/api/mileage/preview")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"05/24/2026","miles":"37.4"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("date must use YYYY-MM-DD"));
    }

    @Test
    void previewUserOverrideWinsOnlyWhenAllowed() throws Exception {
        UUID policyId = UUID.fromString("00000000-0000-0000-0000-000000000702");
        when(ratePolicyService.resolveRate(eq("ws-api"), eq(LocalDate.parse("2026-05-24")), any(MileageSettingsValidation.class)))
                .thenReturn(policyResolution(policyId, "2026 mileage rate", "0.700"));

        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(true));
        mockMvc.perform(post("/api/mileage/preview")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"2026-05-24","miles":"10","rate":"2.50"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value("2.5"))
                .andExpect(jsonPath("$.rateSource").value("USER_OVERRIDE"))
                .andExpect(jsonPath("$.ratePolicyId").doesNotExist());

        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));
        mockMvc.perform(post("/api/mileage/preview")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"2026-05-24","miles":"10","rate":"2.50"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value("0.7"))
                .andExpect(jsonPath("$.rateSource").value("POLICY"));
    }

    @Test
    void previewRejectsUnboundedMileageInputBeforeCallingClockify() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));

        mockMvc.perform(post("/api/mileage/preview")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-05-24\",\"miles\":\"1E+1000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("miles must be a plain decimal number"));

        verifyNoInteractions(gateway);
    }

    @Test
    void previewAcceptsBearerTokenThroughIframeAuthFilter() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));
        MockMvc filteredMockMvc = MockMvcBuilders.standaloneSetup(new MileageApiController(
                        settingsService,
                        ratePolicyService,
                        new MileageDateRangeResolver(Clock.fixed(Instant.parse("2026-05-24T12:00:00Z"), ZoneOffset.UTC)),
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
                                {"date":"2026-05-24","miles":"37.4"}
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
    void createDoesNotReissueUpdateInNormalPath() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));
        // A normal add-on create (no webhook-reserved-first race) must NOT fire a second Clockify write. The
        // category-charge annotation was removed because that second synchronous write to the just-created
        // expense races Clockify's own EXPENSE_CREATED webhook and proved unreliable in production; the note
        // stays as the create note and is reconciled only by Fix 1A's category-price sync.
        when(gateway.createFlatExpense(eq("ws-api"), any(CreateFlatExpenseCommand.class)))
                .thenReturn(createdExpense("exp-1"));

        mockMvc.perform(post("/api/mileage/expenses")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("37.4", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenseId").value("exp-1"))
                .andExpect(jsonPath("$.roundedAmount").value("24.50"));

        verify(gateway, never()).updateFlatExpense(any(), any(), any());
        verify(gateway, never()).getExpense(any(), any());
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
    void createMileageExpenseStoresPolicyAuditFields() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));
        UUID policyId = UUID.fromString("00000000-0000-0000-0000-000000000703");
        when(ratePolicyService.resolveRate(eq("ws-api"), eq(LocalDate.parse("2026-05-24")), any(MileageSettingsValidation.class)))
                .thenReturn(policyResolution(policyId, "2026 mileage rate", "0.700"));
        when(gateway.createFlatExpense(eq("ws-api"), any(CreateFlatExpenseCommand.class))).thenReturn(createdExpense("exp-1"));

        mockMvc.perform(post("/api/mileage/expenses")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("10", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value("0.7"))
                .andExpect(jsonPath("$.rateSource").value("POLICY"))
                .andExpect(jsonPath("$.ratePolicyId").value(policyId.toString()))
                .andExpect(jsonPath("$.ratePolicyName").value("2026 mileage rate"));

        ArgumentCaptor<MileageConversion> saved = ArgumentCaptor.forClass(MileageConversion.class);
        verify(conversionRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getRate()).isEqualByComparingTo("0.700");
        assertThat(saved.getValue().getRateSource()).isEqualTo("POLICY");
        assertThat(saved.getValue().getRatePolicyId()).isEqualTo(policyId);
        assertThat(saved.getValue().getRatePolicyName()).isEqualTo("2026 mileage rate");
    }

    @Test
    void createMileageExpenseJsonStoresTripEvidence() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));
        when(gateway.createFlatExpense(eq("ws-api"), any(CreateFlatExpenseCommand.class))).thenReturn(createdExpense("exp-1"));

        mockMvc.perform(post("/api/mileage/expenses")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"2026-05-24","projectId":"project-1","miles":"10","tripOrigin":"  HQ  ","tripDestination":"Client site","tripPurpose":"Install support","odometerStart":"1200.5","odometerEnd":"1225.5","policyExceptionReason":"Storm detour"}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<MileageConversion> saved = ArgumentCaptor.forClass(MileageConversion.class);
        verify(conversionRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getTripOrigin()).isEqualTo("HQ");
        assertThat(saved.getValue().getTripDestination()).isEqualTo("Client site");
        assertThat(saved.getValue().getTripPurpose()).isEqualTo("Install support");
        assertThat(saved.getValue().getOdometerStart()).isEqualByComparingTo("1200.5");
        assertThat(saved.getValue().getOdometerEnd()).isEqualByComparingTo("1225.5");
        assertThat(saved.getValue().getPolicyExceptionReason()).isEqualTo("Storm detour");
    }

    @Test
    void createMileageExpenseMultipartStoresPolicyAuditFields() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));
        UUID policyId = UUID.fromString("00000000-0000-0000-0000-000000000704");
        when(ratePolicyService.resolveRate(eq("ws-api"), eq(LocalDate.parse("2026-05-24")), any(MileageSettingsValidation.class)))
                .thenReturn(policyResolution(policyId, "2026 mileage rate", "0.700"));
        when(gateway.createFlatExpenseWithReceipt(eq("ws-api"), any(CreateFlatExpenseCommand.class), eq("receipt.png"), eq("image/png"), any()))
                .thenReturn(createdExpense("exp-file"));
        MockMultipartFile file = new MockMultipartFile("file", "receipt.png", "image/png", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/mileage/expenses")
                        .file(file)
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .param("date", "2026-05-24")
                        .param("projectId", "project-1")
                        .param("miles", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateSource").value("POLICY"));

        ArgumentCaptor<MileageConversion> saved = ArgumentCaptor.forClass(MileageConversion.class);
        verify(conversionRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getRatePolicyId()).isEqualTo(policyId);
    }

    @Test
    void createMileageExpenseMultipartStoresTripEvidence() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));
        when(gateway.createFlatExpenseWithReceipt(eq("ws-api"), any(CreateFlatExpenseCommand.class), eq("receipt.png"), eq("image/png"), any()))
                .thenReturn(createdExpense("exp-file"));
        MockMultipartFile file = new MockMultipartFile("file", "receipt.png", "image/png", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/mileage/expenses")
                        .file(file)
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .param("date", "2026-05-24")
                        .param("projectId", "project-1")
                        .param("miles", "10")
                        .param("tripOrigin", "HQ")
                        .param("tripDestination", "Client site")
                        .param("tripPurpose", "Install support")
                        .param("odometerStart", "1200")
                        .param("odometerEnd", "1225")
                        .param("policyExceptionReason", "Storm detour"))
                .andExpect(status().isOk());

        ArgumentCaptor<MileageConversion> saved = ArgumentCaptor.forClass(MileageConversion.class);
        verify(conversionRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getTripOrigin()).isEqualTo("HQ");
        assertThat(saved.getValue().getOdometerEnd()).isEqualByComparingTo("1225");
        assertThat(saved.getValue().getPolicyExceptionReason()).isEqualTo("Storm detour");
    }

    @Test
    void createMileageExpenseRejectsOverlongTripEvidenceBeforeCallingClockify() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));
        String tooLong = "x".repeat(257);

        mockMvc.perform(post("/api/mileage/expenses")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"2026-05-24","projectId":"project-1","miles":"10","tripPurpose":"%s"}
                                """.formatted(tooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("tripPurpose must be 256 characters or fewer"));

        verify(gateway, never()).createFlatExpense(any(), any());
    }

    @Test
    void createMileageExpenseRejectsInvalidOdometerOrderBeforeCallingClockify() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));

        mockMvc.perform(post("/api/mileage/expenses")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"2026-05-24","projectId":"project-1","miles":"10","odometerStart":"1225","odometerEnd":"1200"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("odometerEnd must be greater than or equal to odometerStart"));

        verify(gateway, never()).createFlatExpense(any(), any());
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
    void createRejectsInvalidMileageBeforeCallingClockify() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenReturn(settings(false));

        mockMvc.perform(post("/api/mileage/expenses")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("12.3456", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("miles supports at most 3 decimal places"));

        verifyNoInteractions(gateway);
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

        verify(ratePolicyService).resolveRate(
                eq("ws-api"), eq(LocalDate.parse("2026-05-24")), any(MileageSettingsValidation.class));
    }

    @Test
    void validationErrorDoesNotLeakStackTrace() throws Exception {
        when(settingsService.validateForAddonCreate("ws-api")).thenThrow(new ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "Mileage output category is not configured"));

        String response = mockMvc.perform(post("/api/mileage/preview")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-05-24\",\"miles\":\"37.4\"}"))
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


    private static MileageSettingsValidation settings(boolean allowOverride) {
        return new MileageSettingsValidation("ws-api", true, true, new BigDecimal("0.655"), "mi",
                "cat-input", "cat-output", RoundingMode.HALF_UP, true, true, true, false, allowOverride, null, List.of());
    }

    private static MileageSettingsValidation singleCategorySettings() {
        return new MileageSettingsValidation("ws-api", true, true, new BigDecimal("0.725"), "mile",
                "cat-mileage", "cat-mileage", RoundingMode.HALF_UP, true, true, false, false, false, null, List.of());
    }

    private static MileageRateResolution settingsFallback(MileageSettingsValidation settings) {
        return new MileageRateResolution(
                settings.rate(),
                settings.rate() == null ? null : settings.rate().stripTrailingZeros().toPlainString(),
                MileageRateResolution.SOURCE_SETTINGS_FALLBACK,
                null,
                null,
                null,
                null,
                List.of());
    }

    private static MileageRateResolution policyResolution(UUID id, String name, String rate) {
        return new MileageRateResolution(
                new BigDecimal(rate),
                new BigDecimal(rate).stripTrailingZeros().toPlainString(),
                MileageRateResolution.SOURCE_POLICY,
                id,
                name,
                LocalDate.parse("2026-01-01"),
                null,
                List.of());
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
