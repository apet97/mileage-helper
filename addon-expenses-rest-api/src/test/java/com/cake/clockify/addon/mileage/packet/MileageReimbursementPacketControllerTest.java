package com.cake.clockify.addon.mileage.packet;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.mileage.api.MileageDateRange;
import com.cake.clockify.addon.mileage.api.MileageDateRangeResolver;
import com.cake.clockify.addon.mileage.api.MileageExceptionHandler;
import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import com.cake.clockify.addon.mileage.security.MileageAuthorizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MileageReimbursementPacketControllerTest {
    private MileageReimbursementPacketService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(MileageReimbursementPacketService.class);
        MileageReimbursementPacketController controller = new MileageReimbursementPacketController(
                new MileageAuthorizationService(),
                new MileageDateRangeResolver(Clock.fixed(Instant.parse("2026-05-27T12:00:00Z"), ZoneOffset.UTC)),
                service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new MileageExceptionHandler(new ObjectMapper().findAndRegisterModules()))
                .build();
        when(service.packet(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean()))
                .thenReturn(packet());
        when(service.csv(any())).thenReturn("expense_date,user_id\n2026-05-24,user-claims\n");
    }

    @Test
    void nonAdminSeesOnlyOwnRowsEvenWithForeignUserId() throws Exception {
        mockMvc.perform(get("/iframe/reimbursement-packet")
                        .queryParam("from", "2026-05-01")
                        .queryParam("to", "2026-05-31")
                        .queryParam("userId", "user-two")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("MEMBER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mileage reimbursement packet")));

        verify(service).packet(eq("ws-admin"), eq("user-claims"), any(MileageDateRange.class),
                isNull(), isNull(), eq(false), eq(false));
    }

    @Test
    void adminCanSeeAllRowsOrFilterByUser() throws Exception {
        mockMvc.perform(get("/iframe/reimbursement-packet")
                        .queryParam("from", "2026-05-01")
                        .queryParam("to", "2026-05-31")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk());
        verify(service).packet(eq("ws-admin"), isNull(), any(MileageDateRange.class),
                isNull(), isNull(), eq(false), eq(false));

        mockMvc.perform(get("/iframe/reimbursement-packet")
                        .queryParam("from", "2026-05-01")
                        .queryParam("to", "2026-05-31")
                        .queryParam("userId", "user-two")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk());
        verify(service).packet(eq("ws-admin"), eq("user-two"), any(MileageDateRange.class),
                isNull(), isNull(), eq(false), eq(false));
    }

    @Test
    void mineScopePinsAdminToOwnRows() throws Exception {
        mockMvc.perform(get("/iframe/reimbursement-packet")
                        .queryParam("scope", "mine")
                        .queryParam("from", "2026-05-01")
                        .queryParam("to", "2026-05-31")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk());

        verify(service).packet(eq("ws-admin"), eq("user-claims"), any(MileageDateRange.class),
                isNull(), isNull(), eq(false), eq(false));
    }

    @Test
    void forwardsPacketFiltersAndExportsCsv() throws Exception {
        mockMvc.perform(get("/api/mileage/reimbursement-packet.csv")
                        .queryParam("from", "2026-05-01")
                        .queryParam("to", "2026-05-31")
                        .queryParam("projectId", "project-1")
                        .queryParam("status", "SKIPPED")
                        .queryParam("includeDeleted", "true")
                        .queryParam("exceptionsOnly", "true")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("mileage-reimbursement-packet.csv")))
                .andExpect(content().string(containsString("expense_date,user_id")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<MileageConversionStatus> status = ArgumentCaptor.forClass(MileageConversionStatus.class);
        verify(service).packet(eq("ws-admin"), isNull(), any(MileageDateRange.class),
                eq("project-1"), status.capture(), eq(true), eq(true));
        assertThat(status.getValue()).isEqualTo(MileageConversionStatus.SKIPPED);
    }

    @Test
    void missingDatesReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/iframe/reimbursement-packet")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("MEMBER")))
                .andExpect(status().isBadRequest());
    }

    private static MileageReimbursementPacket packet() {
        return new MileageReimbursementPacket(
                "Ada Lovelace",
                LocalDate.parse("2026-05-01"),
                LocalDate.parse("2026-05-31"),
                Instant.parse("2026-06-16T12:00:00Z"),
                List.of(new MileageReimbursementPacketRow(
                        "2026-05-24", "user-claims", "Ada Lovelace", "project-1", "North Route",
                        "exp-1", "1", "0.725", "POLICY", null, "2026 rate", "0.725", "0.73",
                        "", "", "CONVERTED", "", "", "", "", "",
                        "HQ", "Client site", "Visit", "1200", "1225", "")),
                BigDecimal.ONE,
                new BigDecimal("0.725"),
                new BigDecimal("0.73"),
                1,
                0,
                List.of("2026 rate"),
                false);
    }

    private static NormalizedClaims claims(String role) {
        return new NormalizedClaims("ws-admin", "mileage-for-clockify", "https://backend.example.test",
                "https://reports.example.test", null, null, "user-claims", role, "en", "DEFAULT", "UTC", Instant.now());
    }
}
