package com.cake.clockify.addon.mileage.insights;

import com.cake.clockify.addon.mileage.api.ClockifyOptionNameResolver;
import com.cake.clockify.addon.mileage.api.MileageDateRange;
import com.cake.clockify.addon.mileage.api.model.MileageInsightsResponse;
import com.cake.clockify.addon.mileage.audit.MileageConversion;
import com.cake.clockify.addon.mileage.audit.MileageConversionRepository;
import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import com.cake.clockify.addon.mileage.audit.MileageSkipReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MileageInsightsServiceTest {
    private static final MileageDateRange RANGE = new MileageDateRange(
            LocalDate.parse("2026-05-01"),
            LocalDate.parse("2026-05-31"));

    private MileageConversionRepository repository;
    private ClockifyOptionNameResolver nameResolver;
    private MileageInsightsService service;

    @BeforeEach
    void setUp() {
        repository = mock(MileageConversionRepository.class);
        nameResolver = mock(ClockifyOptionNameResolver.class);
        service = new MileageInsightsService(repository, nameResolver);
        when(nameResolver.projectNamesById(eq("ws-1"), any())).thenReturn(Map.of("project-1", "North Route"));
        when(nameResolver.userNamesById(eq("ws-1"), any())).thenReturn(Map.of("user-1", "Ada Lovelace"));
    }

    @Test
    void aggregatesWorkspaceDateRangeAndExcludesDeletedRows() {
        when(repository.findAllByWorkspaceIdAndStatusNotAndExpenseDateBetween(
                eq("ws-1"), eq(MileageConversionStatus.DELETED), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        conversion("user-1", "project-1", MileageConversionStatus.CONVERTED, "10", "7.25", "7.25", null),
                        conversion("user-1", "project-1", MileageConversionStatus.CONVERTED, "5", "3.625", "3.63", "Storm"),
                        conversion("user-2", "project-2", MileageConversionStatus.FAILED, "1", "0.725", "0.73", null),
                        skipped())));

        MileageInsightsResponse response = service.insights("ws-1", RANGE);

        assertThat(response.totalConvertedMiles()).isEqualTo("15");
        assertThat(response.totalCalculatedAmount()).isEqualTo("10.88");
        assertThat(response.totalRoundedAmount()).isEqualTo("10.88");
        assertThat(response.failedConversions()).isEqualTo(1);
        assertThat(response.rowsMissingTripPurpose()).isEqualTo(1);
        assertThat(response.rowsWithPolicyExceptions()).isEqualTo(1);
        assertThat(response.statusCounts()).extracting(MileageInsightsResponse.CountItem::key)
                .contains("CONVERTED", "FAILED", "SKIPPED");
        assertThat(response.skipReasonCounts()).extracting(MileageInsightsResponse.CountItem::key)
                .containsExactly("FINALIZED_OR_LOCKED");
        assertThat(response.topProjects().get(0).name()).isEqualTo("North Route");
        assertThat(response.topUsers().get(0).name()).isEqualTo("Ada Lovelace");
        verify(repository).findAllByWorkspaceIdAndStatusNotAndExpenseDateBetween(
                eq("ws-1"), eq(MileageConversionStatus.DELETED), eq(RANGE.from()), eq(RANGE.to()), any(Pageable.class));
    }

    @Test
    void limitsTopProjectsAndUsersToTen() {
        List<MileageConversion> rows = java.util.stream.IntStream.range(0, 12)
                .mapToObj(i -> conversion("user-" + i, "project-" + i, MileageConversionStatus.CONVERTED,
                        "1", String.valueOf(100 - i), String.valueOf(100 - i), "purpose"))
                .toList();
        when(repository.findAllByWorkspaceIdAndStatusNotAndExpenseDateBetween(
                eq("ws-1"), eq(MileageConversionStatus.DELETED), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(rows));
        when(nameResolver.projectNamesById(eq("ws-1"), any())).thenReturn(Map.of());
        when(nameResolver.userNamesById(eq("ws-1"), any())).thenReturn(Map.of());

        MileageInsightsResponse response = service.insights("ws-1", RANGE);

        assertThat(response.topProjects()).hasSize(10);
        assertThat(response.topUsers()).hasSize(10);
        assertThat(response.topProjects().get(0).id()).isEqualTo("project-0");
    }

    private static MileageConversion skipped() {
        MileageConversion conversion = conversion("user-3", "project-3", MileageConversionStatus.SKIPPED,
                "0", "0", "0.00", null);
        conversion.setSkipReason(MileageSkipReason.FINALIZED_OR_LOCKED);
        return conversion;
    }

    private static MileageConversion conversion(
            String userId,
            String projectId,
            MileageConversionStatus status,
            String miles,
            String calculated,
            String rounded,
            String purpose) {
        MileageConversion conversion = new MileageConversion();
        conversion.setWorkspaceId("ws-1");
        conversion.setUserId(userId);
        conversion.setProjectId(projectId);
        conversion.setStatus(status);
        conversion.setMiles(new BigDecimal(miles));
        conversion.setCalculatedAmount(new BigDecimal(calculated));
        conversion.setRoundedAmount(new BigDecimal(rounded));
        conversion.setExpenseDate(LocalDate.parse("2026-05-24"));
        conversion.setTripPurpose(purpose);
        if ("Storm".equals(purpose)) {
            conversion.setPolicyExceptionReason("Storm");
        }
        return conversion;
    }
}
