package com.cake.clockify.addon.mileage.report;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.mileage.api.ClockifyOptionNameResolver;
import com.cake.clockify.addon.mileage.api.MileageConversionQueryService;
import com.cake.clockify.addon.mileage.api.MileageDateRangeResolver;
import com.cake.clockify.addon.mileage.api.MileageExceptionHandler;
import com.cake.clockify.addon.mileage.audit.MileageConversion;
import com.cake.clockify.addon.mileage.audit.MileageConversionRepository;
import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseGateway;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseListItem;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseListResult;
import com.cake.clockify.addon.mileage.clockify.ClockifyProjectOption;
import com.cake.clockify.addon.mileage.clockify.ClockifyUserOption;
import com.cake.clockify.addon.mileage.security.MileageAuthorizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MileageReportControllerTest {
    private MileageConversionRepository conversionRepository;
    private ClockifyExpenseGateway gateway;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        conversionRepository = mock(MileageConversionRepository.class);
        gateway = mock(ClockifyExpenseGateway.class);
        MileageConversionQueryService queryService = new MileageConversionQueryService(conversionRepository);
        MileageReportController controller = new MileageReportController(
                new MileageAuthorizationService(),
                gateway,
                new MileageDateRangeResolver(Clock.fixed(Instant.parse("2026-05-27T12:00:00Z"), ZoneOffset.UTC)),
                queryService,
                new ClockifyOptionNameResolver(gateway));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new MileageExceptionHandler(new ObjectMapper().findAndRegisterModules()))
                .build();
    }

    @Test
    void adminWithoutUserIdReportsAllUsers() throws Exception {
        when(gateway.listUsers("ws-admin")).thenReturn(List.of(new ClockifyUserOption("user-1", "Ada Lovelace", "a@x.test")));
        when(gateway.listExpensesForReport(eq("ws-admin"), isNull(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new ClockifyExpenseListResult(List.of(native_("e2", "user-1", "Meals", "18.00")), false));
        when(conversionRepository.findByWorkspaceIdAndStatusAndExpenseIdIn(eq("ws-admin"), eq(MileageConversionStatus.CONVERTED), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/iframe/report")
                        .queryParam("scope", "team")
                        .queryParam("from", "2026-05-01").queryParam("to", "2026-05-31")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<th>User</th>")))
                .andExpect(content().string(containsString("Ada Lovelace")))
                .andExpect(content().string(containsString("Meals")));

        verify(gateway).listExpensesForReport(eq("ws-admin"), isNull(), any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void adminWithUserIdReportsSingleUser() throws Exception {
        when(gateway.listExpensesForReport(eq("ws-admin"), eq("user-two"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new ClockifyExpenseListResult(List.of(), false));

        mockMvc.perform(get("/iframe/report")
                        .queryParam("scope", "team").queryParam("userId", "user-two")
                        .queryParam("from", "2026-05-01").queryParam("to", "2026-05-31")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Expense Report")));

        verify(gateway).listExpensesForReport(eq("ws-admin"), eq("user-two"), any(LocalDate.class), any(LocalDate.class));
        verify(gateway, never()).listUsers(any());
    }

    @Test
    void adminWithUserIdCanDisplaySelectedUserNameWithoutFetchingAllUsers() throws Exception {
        when(gateway.listExpensesForReport(eq("ws-admin"), eq("user-two"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new ClockifyExpenseListResult(List.of(), false));

        mockMvc.perform(get("/iframe/report")
                        .queryParam("scope", "team")
                        .queryParam("userId", "user-two")
                        .queryParam("selectedUserName", "Ada Lovelace")
                        .queryParam("from", "2026-05-01")
                        .queryParam("to", "2026-05-31")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ada Lovelace")));

        verify(gateway, never()).listUsers(any());
    }

    @Test
    void adminMineScopeReportsOwnNotAllUsers() throws Exception {
        when(gateway.listExpensesForReport(eq("ws-admin"), eq("user-claims"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new ClockifyExpenseListResult(List.of(), false));

        mockMvc.perform(get("/iframe/report")
                        .queryParam("scope", "mine")
                        .queryParam("from", "2026-05-01").queryParam("to", "2026-05-31")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("<th>User</th>")))); // single-user, no User column

        // Mine pins an admin to their own rows, NOT all users.
        verify(gateway).listExpensesForReport(eq("ws-admin"), eq("user-claims"), any(LocalDate.class), any(LocalDate.class));
        verify(gateway, never()).listUsers(any());
    }

    @Test
    void memberReportsOwnEvenWithForeignUserId() throws Exception {
        when(gateway.listExpensesForReport(eq("ws-admin"), eq("user-claims"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new ClockifyExpenseListResult(List.of(), false));

        mockMvc.perform(get("/iframe/report")
                        .queryParam("scope", "team").queryParam("userId", "user-two")
                        .queryParam("from", "2026-05-01").queryParam("to", "2026-05-31")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("MEMBER")))
                .andExpect(status().isOk());

        verify(gateway).listExpensesForReport(eq("ws-admin"), eq("user-claims"), any(LocalDate.class), any(LocalDate.class));
        verify(gateway, never()).listUsers(any());
    }

    @Test
    void mileageExpenseShowsReconciledValuesAndNativeShowsNative() throws Exception {
        when(gateway.listUsers("ws-admin")).thenReturn(List.of(new ClockifyUserOption("user-claims", "Ada", "a@x.test")));
        when(gateway.listExpensesForReport(eq("ws-admin"), eq("user-claims"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new ClockifyExpenseListResult(List.of(
                        new ClockifyExpenseListItem("e1", "user-claims", LocalDate.parse("2026-05-03"), "North Route", "cat-mileage", "Mileage", new BigDecimal("7.25")),
                        native_("e2", "user-claims", "Meals", "18.00")), false));
        when(conversionRepository.findByWorkspaceIdAndStatusAndExpenseIdIn(eq("ws-admin"), eq(MileageConversionStatus.CONVERTED), any()))
                .thenReturn(List.of(conversion("e1", "user-claims", "3.5", "7.2531", "25.38585")));

        mockMvc.perform(get("/iframe/report")
                        .queryParam("scope", "team").queryParam("userId", "user-claims")
                        .queryParam("from", "2026-05-01").queryParam("to", "2026-05-31")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("25.39")))     // our reconciled amount at 2 dp, not native 7.25
                .andExpect(content().string(containsString("7.2531")))    // our rate (natural precision)
                .andExpect(content().string(containsString("Meals")))     // native category
                .andExpect(content().string(containsString(">18.00</td>"))); // native amount at 2 dp
    }

    @Test
    void degradesToMileageOnlyWithProjectNamesWhenExpenseListingFails() throws Exception {
        when(conversionRepository.findAllByWorkspaceIdAndUserIdAndStatusAndExpenseDateBetween(
                eq("ws-admin"), eq("user-claims"), eq(MileageConversionStatus.CONVERTED), any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(conversion("e1", "user-claims", "2", "7.25", "14.5"))));
        when(gateway.listUsers("ws-admin")).thenReturn(List.of(new ClockifyUserOption("user-claims", "Ada", "a@x.test")));
        when(gateway.listProjects("ws-admin")).thenReturn(List.of(new ClockifyProjectOption("proj-e1", "North Route")));
        when(gateway.listExpensesForReport(eq("ws-admin"), eq("user-claims"), any(LocalDate.class), any(LocalDate.class)))
                .thenThrow(new IOException("clockify unavailable"));

        mockMvc.perform(get("/iframe/report")
                        .queryParam("scope", "mine")
                        .queryParam("from", "2026-05-01").queryParam("to", "2026-05-31")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("MEMBER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Live expense data is unavailable")))
                .andExpect(content().string(containsString("14.50")))     // amount at 2 dp
                .andExpect(content().string(containsString("North Route"))); // project attribution survives the outage
    }

    @Test
    void internalReportMergeFailureReturnsServerError() throws Exception {
        when(gateway.listExpensesForReport(eq("ws-admin"), eq("user-claims"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new ClockifyExpenseListResult(List.of(native_("e2", "user-claims", "Meals", "18.00")), false));
        when(conversionRepository.findByWorkspaceIdAndStatusAndExpenseIdIn(eq("ws-admin"), eq(MileageConversionStatus.CONVERTED), any()))
                .thenThrow(new IllegalStateException("db bug"));

        mockMvc.perform(get("/iframe/report")
                        .queryParam("scope", "mine")
                        .queryParam("from", "2026-05-01")
                        .queryParam("to", "2026-05-31")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("MEMBER")))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void missingDatesReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/iframe/report")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("MEMBER")))
                .andExpect(status().isBadRequest());
    }

    private static ClockifyExpenseListItem native_(String id, String userId, String category, String amount) {
        return new ClockifyExpenseListItem(id, userId, LocalDate.parse("2026-05-04"), "", "cat-" + id, category, new BigDecimal(amount));
    }

    private static MileageConversion conversion(String expenseId, String userId, String miles, String rate, String calc) {
        MileageConversion conversion = new MileageConversion();
        conversion.setExpenseId(expenseId);
        conversion.setUserId(userId);
        conversion.setProjectId("proj-" + expenseId);
        conversion.setMiles(new BigDecimal(miles));
        conversion.setRate(new BigDecimal(rate));
        conversion.setCalculatedAmount(new BigDecimal(calc));
        conversion.setExpenseDate(LocalDate.parse("2026-05-03"));
        return conversion;
    }

    private static NormalizedClaims claims(String role) {
        return new NormalizedClaims("ws-admin", "mileage-for-clockify", "https://backend.example.test",
                "https://reports.example.test", null, null, "user-claims", role, "en", "DEFAULT", "UTC", Instant.now());
    }
}
