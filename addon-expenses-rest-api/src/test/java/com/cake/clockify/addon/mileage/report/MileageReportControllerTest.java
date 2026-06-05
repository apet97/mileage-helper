package com.cake.clockify.addon.mileage.report;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.mileage.api.MileageExceptionHandler;
import com.cake.clockify.addon.mileage.audit.MileageConversion;
import com.cake.clockify.addon.mileage.audit.MileageConversionRepository;
import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseGateway;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
        MileageReportController controller = new MileageReportController(
                conversionRepository, new MileageAuthorizationService(), gateway);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new MileageExceptionHandler(new ObjectMapper().findAndRegisterModules()))
                .build();
    }

    @Test
    void memberGetsOwnReport() throws Exception {
        when(conversionRepository.findAllByWorkspaceIdAndUserIdAndStatusNotAndExpenseDateBetween(
                eq("ws-admin"), eq("user-claims"), eq(MileageConversionStatus.DELETED),
                eq(LocalDate.parse("2026-05-01")), eq(LocalDate.parse("2026-05-31")), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row())));
        when(gateway.listUsers("ws-admin")).thenReturn(List.of(new ClockifyUserOption("user-claims", "Ada Lovelace", "ada@example.test")));
        when(gateway.listProjects("ws-admin")).thenReturn(List.of(new ClockifyProjectOption("p1", "North Route")));

        mockMvc.perform(get("/iframe/report")
                        .queryParam("from", "2026-05-01")
                        .queryParam("to", "2026-05-31")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("MEMBER")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("Ada Lovelace")))
                .andExpect(content().string(containsString("North Route")));
    }

    @Test
    void adminGetsReportForAnotherUser() throws Exception {
        when(conversionRepository.findAllByWorkspaceIdAndUserIdAndStatusNotAndExpenseDateBetween(
                eq("ws-admin"), eq("user-two"), eq(MileageConversionStatus.DELETED),
                any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row())));

        mockMvc.perform(get("/iframe/report")
                        .queryParam("userId", "user-two")
                        .queryParam("from", "2026-05-01")
                        .queryParam("to", "2026-05-31")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk());

        verify(conversionRepository).findAllByWorkspaceIdAndUserIdAndStatusNotAndExpenseDateBetween(
                eq("ws-admin"), eq("user-two"), eq(MileageConversionStatus.DELETED),
                any(LocalDate.class), any(LocalDate.class), any(Pageable.class));
    }

    @Test
    void memberCannotReadAnotherUsersReport() throws Exception {
        when(conversionRepository.findAllByWorkspaceIdAndUserIdAndStatusNotAndExpenseDateBetween(
                eq("ws-admin"), eq("user-claims"), eq(MileageConversionStatus.DELETED),
                any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row())));

        mockMvc.perform(get("/iframe/report")
                        .queryParam("userId", "user-two")
                        .queryParam("from", "2026-05-01")
                        .queryParam("to", "2026-05-31")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("MEMBER")))
                .andExpect(status().isOk());

        // The foreign userId is ignored; the query is forced to the requester.
        verify(conversionRepository).findAllByWorkspaceIdAndUserIdAndStatusNotAndExpenseDateBetween(
                eq("ws-admin"), eq("user-claims"), eq(MileageConversionStatus.DELETED),
                any(LocalDate.class), any(LocalDate.class), any(Pageable.class));
    }

    @Test
    void missingDatesReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/iframe/report")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("MEMBER")))
                .andExpect(status().isBadRequest());
    }

    private static MileageConversion row() {
        MileageConversion conversion = new MileageConversion();
        conversion.setExpenseDate(LocalDate.parse("2026-05-24"));
        conversion.setProjectId("p1");
        conversion.setUserId("user-claims");
        conversion.setMiles(new BigDecimal("12.5"));
        conversion.setRate(new BigDecimal("0.725"));
        conversion.setCalculatedAmount(new BigDecimal("9.0625"));
        return conversion;
    }

    private static NormalizedClaims claims(String role) {
        return new NormalizedClaims("ws-admin", "mileage-for-clockify", "https://backend.example.test",
                "https://reports.example.test", null, null, "user-claims", role, "en", "DEFAULT", "UTC", Instant.now());
    }
}
