package com.cake.clockify.addon.mileage.api;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.mileage.audit.MileageConversion;
import com.cake.clockify.addon.mileage.audit.MileageConversionRepository;
import com.cake.clockify.addon.mileage.audit.MileageConversionSource;
import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseGateway;
import com.cake.clockify.addon.mileage.clockify.ClockifyProjectOption;
import com.cake.clockify.addon.mileage.clockify.ClockifyUserOption;
import com.cake.clockify.addon.mileage.conversion.ConversionResult;
import com.cake.clockify.addon.mileage.conversion.MileageConversionService;
import com.cake.clockify.addon.mileage.security.MileageAuthorizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MileageConversionControllerTest {
    private static final UUID CONVERSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000321");
    private MileageConversionRepository conversionRepository;
    private MileageConversionService conversionService;
    private ClockifyExpenseGateway gateway;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        conversionRepository = mock(MileageConversionRepository.class);
        conversionService = mock(MileageConversionService.class);
        gateway = mock(ClockifyExpenseGateway.class);
        MileageConversionQueryService queryService = new MileageConversionQueryService(conversionRepository);
        MileageConversionController controller = new MileageConversionController(
                conversionRepository,
                conversionService,
                new MileageAuthorizationService(),
                new MileageDateRangeResolver(Clock.fixed(Instant.parse("2026-05-27T12:00:00Z"), ZoneOffset.UTC)),
                queryService,
                new MileageConversionCsvExporter(queryService),
                new ClockifyOptionNameResolver(gateway));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new MileageExceptionHandler(new ObjectMapper().findAndRegisterModules()))
                .build();
    }

    @Test
    void adminCanListConversions() throws Exception {
        when(conversionRepository.findAllByWorkspaceIdAndExpenseDateBetween(
                eq("ws-admin"), any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(conversion("ws-admin"))));

        mockMvc.perform(get("/api/mileage/conversions")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversions[0].expenseId").value("exp-1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void adminCanListConversionWithoutUserId() throws Exception {
        when(conversionRepository.findAllByWorkspaceIdAndExpenseDateBetween(
                eq("ws-admin"), any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(conversionWithoutUser("ws-admin"))));

        mockMvc.perform(get("/api/mileage/conversions")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversions[0].userId").doesNotExist())
                .andExpect(jsonPath("$.conversions[0].userName").doesNotExist());

        verify(gateway, never()).listUsers(any());
    }

    @Test
    void memberCanListOnlyOwnMileageRows() throws Exception {
        when(conversionRepository.findAllByWorkspaceIdAndUserIdAndStatusNotAndExpenseDateBetween(
                eq("ws-admin"), eq("user-claims"), eq(MileageConversionStatus.DELETED), eq(LocalDate.parse("2026-05-24")), eq(LocalDate.parse("2026-05-30")), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(conversion("ws-admin"))));

        mockMvc.perform(get("/api/mileage/mine")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversions[0].expenseId").value("exp-1"))
                .andExpect(jsonPath("$.conversions[0].expenseDate").value("2026-05-24"))
                .andExpect(jsonPath("$.conversions[0].calculatedAmount").value("24.497"))
                .andExpect(jsonPath("$.conversions[0].roundedAmount").value("24.50"));

        verify(conversionRepository).findAllByWorkspaceIdAndUserIdAndStatusNotAndExpenseDateBetween(
                eq("ws-admin"), eq("user-claims"), eq(MileageConversionStatus.DELETED), eq(LocalDate.parse("2026-05-24")), eq(LocalDate.parse("2026-05-30")), any(Pageable.class));
    }

    @Test
    void mineRowsDoNotEchoRawUserIdAsUserName() throws Exception {
        // The Mine view is the requester's own rows (no User column), so userName must stay blank rather than
        // echo the raw userId; the admin id-fallback is intentionally kept for Team/Conversions below.
        when(conversionRepository.findAllByWorkspaceIdAndUserIdAndStatusNotAndExpenseDateBetween(
                eq("ws-admin"), eq("user-claims"), eq(MileageConversionStatus.DELETED), any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(conversion("ws-admin"))));

        mockMvc.perform(get("/api/mileage/mine")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversions[0].expenseId").value("exp-1"))
                .andExpect(jsonPath("$.conversions[0].userName").doesNotExist());

        verify(gateway, never()).listUsers(any());
    }

    @Test
    void teamRowsFallBackToUserIdWhenNameLookupDoesNotResolve() throws Exception {
        // Admin Team/Conversions keep the raw-id fallback so an unresolved user still stays identifiable.
        when(conversionRepository.findAllByWorkspaceIdAndStatusNotAndExpenseDateBetween(
                eq("ws-admin"), eq(MileageConversionStatus.DELETED), any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(conversion("ws-admin"))));
        when(gateway.listUsers("ws-admin")).thenReturn(List.of());

        mockMvc.perform(get("/api/mileage/team")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversions[0].userName").value("user-claims"));
    }

    @Test
    void explicitMineDateRangeFiltersByExpenseDateAndSortsByExpenseDateThenUpdatedAt() throws Exception {
        when(conversionRepository.findAllByWorkspaceIdAndUserIdAndStatusNotAndExpenseDateBetween(
                eq("ws-admin"), eq("user-claims"), eq(MileageConversionStatus.DELETED), eq(LocalDate.parse("2026-05-01")), eq(LocalDate.parse("2026-05-31")), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(conversion("ws-admin"))));

        mockMvc.perform(get("/api/mileage/mine")
                        .queryParam("from", "2026-05-01")
                        .queryParam("to", "2026-05-31")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("MEMBER")))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(conversionRepository).findAllByWorkspaceIdAndUserIdAndStatusNotAndExpenseDateBetween(
                eq("ws-admin"), eq("user-claims"), eq(MileageConversionStatus.DELETED), eq(LocalDate.parse("2026-05-01")), eq(LocalDate.parse("2026-05-31")), pageable.capture());
        assertThat(pageable.getValue()).isInstanceOf(PageRequest.class);
        assertThat(pageable.getValue().getSort().toString()).isEqualTo("expenseDate: DESC,updatedAt: DESC");
    }

    @Test
    void missingRangeDefaultsToCurrentUsWeekInClaimsTimezone() throws Exception {
        when(conversionRepository.findAllByWorkspaceIdAndUserIdAndStatusNotAndExpenseDateBetween(
                eq("ws-admin"), eq("user-claims"), eq(MileageConversionStatus.DELETED), eq(LocalDate.parse("2026-05-24")), eq(LocalDate.parse("2026-05-30")), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(conversion("ws-admin"))));

        mockMvc.perform(get("/api/mileage/mine")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("MEMBER", "America/New_York")))
                .andExpect(status().isOk());

        verify(conversionRepository).findAllByWorkspaceIdAndUserIdAndStatusNotAndExpenseDateBetween(
                eq("ws-admin"), eq("user-claims"), eq(MileageConversionStatus.DELETED), eq(LocalDate.parse("2026-05-24")), eq(LocalDate.parse("2026-05-30")), any(Pageable.class));
    }

    @Test
    void partialDateRangeReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/mileage/mine")
                        .queryParam("from", "2026-05-01")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("MEMBER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Both from and to dates are required when filtering mileage rows"));
    }

    @Test
    void reversedDateRangeReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/mileage/team")
                        .queryParam("from", "2026-05-31")
                        .queryParam("to", "2026-05-01")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("from must be on or before to"));
    }

    @Test
    void memberCannotReadTeamMileageRows() throws Exception {
        mockMvc.perform(get("/api/mileage/team")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("MEMBER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListTeamMileageRows() throws Exception {
        when(conversionRepository.findAllByWorkspaceIdAndStatusNotAndExpenseDateBetween(
                eq("ws-admin"), eq(MileageConversionStatus.DELETED), any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(conversion("ws-admin"))));
        when(gateway.listUsers("ws-admin")).thenReturn(List.of(new ClockifyUserOption("user-claims", "Ada Lovelace", "ada@example.test")));

        mockMvc.perform(get("/api/mileage/team")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversions[0].userId").value("user-claims"))
                .andExpect(jsonPath("$.conversions[0].userName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.conversions[0].sourceLabel").value("Created through Expenses"))
                .andExpect(jsonPath("$.conversions[0].calculatedAmount").value("24.497"));
    }

    @Test
    void adminCanFilterTeamMileageByUser() throws Exception {
        when(conversionRepository.findAllByWorkspaceIdAndUserIdAndStatusNotAndExpenseDateBetween(
                eq("ws-admin"), eq("user-two"), eq(MileageConversionStatus.DELETED), any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(conversion("ws-admin"))));

        mockMvc.perform(get("/api/mileage/team")
                        .queryParam("userId", "user-two")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("ADMIN")))
                .andExpect(status().isOk());

        verify(conversionRepository).findAllByWorkspaceIdAndUserIdAndStatusNotAndExpenseDateBetween(
                eq("ws-admin"), eq("user-two"), eq(MileageConversionStatus.DELETED), any(LocalDate.class), any(LocalDate.class), any(Pageable.class));
    }

    @Test
    void adminCanFilterConversionsByUserAndStatus() throws Exception {
        when(conversionRepository.findAllByWorkspaceIdAndUserIdAndStatusAndExpenseDateBetween(
                eq("ws-admin"), eq("user-two"), eq(MileageConversionStatus.CONVERTED), eq(LocalDate.parse("2026-05-01")), eq(LocalDate.parse("2026-05-31")), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(conversion("ws-admin"))));

        mockMvc.perform(get("/api/mileage/conversions")
                        .queryParam("userId", "user-two")
                        .queryParam("status", "CONVERTED")
                        .queryParam("from", "2026-05-01")
                        .queryParam("to", "2026-05-31")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk());

        verify(conversionRepository).findAllByWorkspaceIdAndUserIdAndStatusAndExpenseDateBetween(
                eq("ws-admin"), eq("user-two"), eq(MileageConversionStatus.CONVERTED), eq(LocalDate.parse("2026-05-01")), eq(LocalDate.parse("2026-05-31")), any(Pageable.class));
    }

    @Test
    void adminCanFilterConversionsByUserWithoutStatus() throws Exception {
        when(conversionRepository.findAllByWorkspaceIdAndUserIdAndExpenseDateBetween(
                eq("ws-admin"), eq("user-two"), any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(conversion("ws-admin"))));

        mockMvc.perform(get("/api/mileage/conversions")
                        .queryParam("userId", "user-two")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk());

        verify(conversionRepository).findAllByWorkspaceIdAndUserIdAndExpenseDateBetween(
                eq("ws-admin"), eq("user-two"), any(LocalDate.class), any(LocalDate.class), any(Pageable.class));
    }

    @Test
    void adminCanFilterTeamCsvByUser() throws Exception {
        when(conversionRepository.findAllByWorkspaceIdAndUserIdAndStatusNotAndExpenseDateBetween(
                eq("ws-admin"), eq("user-two"), eq(MileageConversionStatus.DELETED), any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(conversion("ws-admin"))));

        mockMvc.perform(get("/api/mileage/team.csv")
                        .queryParam("userId", "user-two")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk());

        verify(conversionRepository).findAllByWorkspaceIdAndUserIdAndStatusNotAndExpenseDateBetween(
                eq("ws-admin"), eq("user-two"), eq(MileageConversionStatus.DELETED), any(LocalDate.class), any(LocalDate.class), any(Pageable.class));
    }

    @Test
    void adminCanReadConversionDetail() throws Exception {
        when(conversionRepository.findByIdAndWorkspaceId(CONVERSION_ID, "ws-admin"))
                .thenReturn(Optional.of(conversion("ws-admin")));

        mockMvc.perform(get("/api/mileage/conversions/{id}", CONVERSION_ID)
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CONVERSION_ID.toString()))
                .andExpect(jsonPath("$.sourceLabel").value("Created through Expenses"))
                .andExpect(jsonPath("$.roundedAmount").value("24.50"));

        verify(conversionRepository).findByIdAndWorkspaceId(CONVERSION_ID, "ws-admin");
    }

    @Test
    void adminCanReadConversionDetailWithoutUserId() throws Exception {
        when(conversionRepository.findByIdAndWorkspaceId(CONVERSION_ID, "ws-admin"))
                .thenReturn(Optional.of(conversionWithoutUser("ws-admin")));

        mockMvc.perform(get("/api/mileage/conversions/{id}", CONVERSION_ID)
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CONVERSION_ID.toString()))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.userName").doesNotExist());

        verify(gateway, never()).listUsers(any());
    }

    @Test
    void adminCanRetryFailedConversion() throws Exception {
        when(conversionService.retry(any(), eq(CONVERSION_ID))).thenReturn(new ConversionResult(
                CONVERSION_ID, "exp-1", MileageConversionStatus.CONVERTED, null, "Converted mileage expense"));

        mockMvc.perform(post("/api/mileage/conversions/{id}/retry", CONVERSION_ID)
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONVERTED"));
    }

    @Test
    void mineCsvExportsOwnRowsAndEscapesCsvCells() throws Exception {
        when(conversionRepository.findAllByWorkspaceIdAndUserIdAndStatusNotAndExpenseDateBetween(
                eq("ws-admin"), eq("user-claims"), eq(MileageConversionStatus.DELETED), eq(LocalDate.parse("2026-05-01")), eq(LocalDate.parse("2026-05-31")), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(conversionWithCsvCharacters("ws-admin"))));

        mockMvc.perform(get("/api/mileage/mine.csv")
                        .queryParam("from", "2026-05-01")
                        .queryParam("to", "2026-05-31")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("MEMBER")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("mileage-mine.csv")))
                .andExpect(content().string(containsString("expense_id,source,source_label,status,user_id,user_name,project_id,project_name,miles,rate,calculated_amount,expense_amount,rounding_mode,expense_date,updated_at,converted_at,note_marker")))
                .andExpect(content().string(containsString("2026-05-24")))
                .andExpect(content().string(containsString("\"project, \"\"north\"\"")))
                .andExpect(content().string(containsString("\"[MileageAddon:converted:v1 id=quoted \"\"marker\"\"]\"")));
    }

    @Test
    void teamCsvNeutralizesSpreadsheetFormulas() throws Exception {
        MileageConversion conversion = conversion("ws-admin");
        conversion.setExpenseId("\r=HYPERLINK(\"https://evil.example\",\"x\")");
        when(conversionRepository.findAllByWorkspaceIdAndStatusNotAndExpenseDateBetween(
                eq("ws-admin"), eq(MileageConversionStatus.DELETED), any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(conversion)));

        mockMvc.perform(get("/api/mileage/team.csv")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"'\r=HYPERLINK")));
    }

    @Test
    void teamCsvExportsRowsWithoutUserId() throws Exception {
        when(conversionRepository.findAllByWorkspaceIdAndStatusNotAndExpenseDateBetween(
                eq("ws-admin"), eq(MileageConversionStatus.DELETED), any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(conversionWithoutUser("ws-admin"))));

        mockMvc.perform(get("/api/mileage/team.csv")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CONVERTED,,,")));

        verify(gateway, never()).listUsers(any());
    }

    @Test
    void teamCsvPaginatesBeyondFirstCsvPage() throws Exception {
        MileageConversion first = conversion("ws-admin");
        first.setExpenseId("exp-page-1");
        MileageConversion second = conversion("ws-admin");
        second.setExpenseId("exp-page-2");

        when(conversionRepository.findAllByWorkspaceIdAndStatusNotAndExpenseDateBetween(
                eq("ws-admin"), eq(MileageConversionStatus.DELETED), any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(4);
                    if (pageable.getPageNumber() == 0) {
                        return new PageImpl<>(List.of(first), pageable, 1001);
                    }
                    return new PageImpl<>(List.of(second), pageable, 1001);
                });

        mockMvc.perform(get("/api/mileage/team.csv")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Mileage-Export-Truncated", "false"))
                .andExpect(content().string(containsString("exp-page-1")))
                .andExpect(content().string(containsString("exp-page-2")));
    }

    @Test
    void teamAndConversionsCsvRequireAdminRole() throws Exception {
        mockMvc.perform(get("/api/mileage/team.csv")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("MEMBER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/mileage/conversions.csv")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("MEMBER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanExportTeamAndConversionCsv() throws Exception {
        when(conversionRepository.findAllByWorkspaceIdAndStatusNotAndExpenseDateBetween(
                eq("ws-admin"), eq(MileageConversionStatus.DELETED), any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(conversion("ws-admin"))));
        when(conversionRepository.findAllByWorkspaceIdAndExpenseDateBetween(
                eq("ws-admin"), any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(conversion("ws-admin"))));
        when(gateway.listUsers("ws-admin")).thenReturn(List.of(new ClockifyUserOption("user-claims", "Ada Lovelace", "ada@example.test")));
        when(gateway.listProjects("ws-admin")).thenReturn(List.of(new ClockifyProjectOption("project-1", "Northern Route")));

        mockMvc.perform(get("/api/mileage/team.csv")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("mileage-team.csv")))
                .andExpect(content().string(containsString("WEBHOOK_CREATED,Created through Expenses,CONVERTED,user-claims,Ada Lovelace,project-1,Northern Route,")))
                .andExpect(content().string(containsString("24.497,24.50")));

        mockMvc.perform(get("/api/mileage/conversions.csv")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("mileage-conversions.csv")))
                .andExpect(content().string(containsString("project-1,Northern Route,")))
                .andExpect(content().string(containsString("24.497,24.50")));
    }

    @Test
    void mineCsvIncludesProjectNameFromClockifyLookup() throws Exception {
        when(conversionRepository.findAllByWorkspaceIdAndUserIdAndStatusNotAndExpenseDateBetween(
                eq("ws-admin"), eq("user-claims"), eq(MileageConversionStatus.DELETED), any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(conversion("ws-admin"))));
        when(gateway.listProjects("ws-admin")).thenReturn(List.of(new ClockifyProjectOption("project-1", "Northern Route")));

        mockMvc.perform(get("/api/mileage/mine.csv")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("MEMBER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(",project-1,Northern Route,")));

        verify(gateway, never()).listUsers(any());
    }

    @Test
    void memberCannotReadConversionLog() throws Exception {
        mockMvc.perform(get("/api/mileage/conversions")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("MEMBER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void conversionListIsWorkspaceIsolated() throws Exception {
        when(conversionRepository.findAllByWorkspaceIdAndExpenseDateBetween(
                eq("ws-admin"), any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(conversion("ws-admin"))));

        mockMvc.perform(get("/api/mileage/conversions?pageSize=500")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk());

        verify(conversionRepository).findAllByWorkspaceIdAndExpenseDateBetween(
                eq("ws-admin"), any(LocalDate.class), any(LocalDate.class), any(Pageable.class));
    }

    @Test
    void conversionListStatusFilterAlsoUsesExpenseDateRange() throws Exception {
        when(conversionRepository.findAllByWorkspaceIdAndStatusAndExpenseDateBetween(
                eq("ws-admin"), eq(MileageConversionStatus.CONVERTED), eq(LocalDate.parse("2026-05-01")), eq(LocalDate.parse("2026-05-31")), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(conversion("ws-admin"))));

        mockMvc.perform(get("/api/mileage/conversions")
                        .queryParam("status", "CONVERTED")
                        .queryParam("from", "2026-05-01")
                        .queryParam("to", "2026-05-31")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk());

        verify(conversionRepository).findAllByWorkspaceIdAndStatusAndExpenseDateBetween(
                eq("ws-admin"), eq(MileageConversionStatus.CONVERTED), eq(LocalDate.parse("2026-05-01")), eq(LocalDate.parse("2026-05-31")), any(Pageable.class));
    }

    private static MileageConversion conversion(String workspaceId) {
        MileageConversion conversion = new MileageConversion();
        conversion.setId(CONVERSION_ID);
        conversion.setWorkspaceId(workspaceId);
        conversion.setExpenseId("exp-1");
        conversion.setSource(MileageConversionSource.WEBHOOK_CREATED);
        conversion.setStatus(MileageConversionStatus.CONVERTED);
        conversion.setUserId("user-claims");
        conversion.setProjectId("project-1");
        conversion.setMiles(new BigDecimal("37.4"));
        conversion.setRate(new BigDecimal("0.655"));
        conversion.setCalculatedAmount(new BigDecimal("24.4970"));
        conversion.setRoundedAmount(new BigDecimal("24.50"));
        conversion.setRoundingMode("HALF_UP");
        conversion.setNoteMarker("[MileageAddon:converted:v1 id=00000000-0000-0000-0000-000000000321]");
        conversion.setExpenseDate(LocalDate.parse("2026-05-24"));
        conversion.setCreatedAt(Instant.parse("2026-05-24T10:00:00Z"));
        conversion.setUpdatedAt(Instant.parse("2026-05-24T10:01:00Z"));
        conversion.setConvertedAt(Instant.parse("2026-05-24T10:02:00Z"));
        return conversion;
    }

    private static MileageConversion conversionWithCsvCharacters(String workspaceId) {
        MileageConversion conversion = conversion(workspaceId);
        conversion.setProjectId("project, \"north\"\nline");
        conversion.setNoteMarker("[MileageAddon:converted:v1 id=quoted \"marker\"]");
        return conversion;
    }

    private static MileageConversion conversionWithoutUser(String workspaceId) {
        MileageConversion conversion = conversion(workspaceId);
        conversion.setUserId(null);
        return conversion;
    }

    private static NormalizedClaims claims(String role) {
        return claims(role, "UTC");
    }

    private static NormalizedClaims claims(String role, String timezone) {
        return new NormalizedClaims("ws-admin", "mileage-for-clockify", "https://backend.example.test",
                "https://reports.example.test", null, null, "user-claims", role, "en", "DEFAULT", timezone, Instant.now());
    }
}
