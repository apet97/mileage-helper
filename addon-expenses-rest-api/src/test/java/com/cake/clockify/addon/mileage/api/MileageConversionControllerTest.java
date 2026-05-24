package com.cake.clockify.addon.mileage.api;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.mileage.audit.MileageConversion;
import com.cake.clockify.addon.mileage.audit.MileageConversionRepository;
import com.cake.clockify.addon.mileage.audit.MileageConversionSource;
import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import com.cake.clockify.addon.mileage.conversion.ConversionResult;
import com.cake.clockify.addon.mileage.conversion.MileageConversionService;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MileageConversionControllerTest {
    private static final UUID CONVERSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000321");
    private MileageConversionRepository conversionRepository;
    private MileageConversionService conversionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        conversionRepository = mock(MileageConversionRepository.class);
        conversionService = mock(MileageConversionService.class);
        MileageConversionController controller = new MileageConversionController(
                conversionRepository,
                conversionService,
                new MileageAuthorizationService());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new MileageExceptionHandler(new ObjectMapper().findAndRegisterModules()))
                .build();
    }

    @Test
    void adminCanListConversions() throws Exception {
        when(conversionRepository.findAllByWorkspaceId(eq("ws-admin"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(conversion("ws-admin"))));

        mockMvc.perform(get("/api/mileage/conversions")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversions[0].expenseId").value("exp-1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void adminCanReadConversionDetail() throws Exception {
        when(conversionRepository.findById(CONVERSION_ID)).thenReturn(Optional.of(conversion("ws-admin")));

        mockMvc.perform(get("/api/mileage/conversions/{id}", CONVERSION_ID)
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CONVERSION_ID.toString()))
                .andExpect(jsonPath("$.roundedAmount").value("24.50"));
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
    void memberCannotReadConversionLog() throws Exception {
        mockMvc.perform(get("/api/mileage/conversions")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("MEMBER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void conversionListIsWorkspaceIsolated() throws Exception {
        when(conversionRepository.findAllByWorkspaceId(eq("ws-admin"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(conversion("ws-admin"))));

        mockMvc.perform(get("/api/mileage/conversions?pageSize=500")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk());

        verify(conversionRepository).findAllByWorkspaceId(eq("ws-admin"), any(Pageable.class));
    }

    private static MileageConversion conversion(String workspaceId) {
        MileageConversion conversion = new MileageConversion();
        conversion.setId(CONVERSION_ID);
        conversion.setWorkspaceId(workspaceId);
        conversion.setExpenseId("exp-1");
        conversion.setSource(MileageConversionSource.WEBHOOK_CREATED);
        conversion.setStatus(MileageConversionStatus.CONVERTED);
        conversion.setMiles(new BigDecimal("37.4"));
        conversion.setRate(new BigDecimal("0.655"));
        conversion.setCalculatedAmount(new BigDecimal("24.4970"));
        conversion.setRoundedAmount(new BigDecimal("24.50"));
        conversion.setCreatedAt(Instant.parse("2026-05-24T10:00:00Z"));
        conversion.setUpdatedAt(Instant.parse("2026-05-24T10:01:00Z"));
        conversion.setConvertedAt(Instant.parse("2026-05-24T10:02:00Z"));
        return conversion;
    }

    private static NormalizedClaims claims(String role) {
        return new NormalizedClaims("ws-admin", "mileage-for-clockify", "https://backend.example.test",
                "https://reports.example.test", null, null, "user-claims", role, "en", "DEFAULT", "UTC", Instant.now());
    }
}
