package com.cake.clockify.addon.mileage.api;

import com.cake.clockify.addon.mileage.audit.MileageConversion;
import com.cake.clockify.addon.mileage.audit.MileageConversionRepository;
import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
public class MileageConversionQueryService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort VISIBLE_LIST_SORT = Sort.by(
            Sort.Order.desc("expenseDate"),
            Sort.Order.desc("updatedAt"));
    private static final Sort REPORT_SORT = Sort.by(Sort.Order.asc("expenseDate"), Sort.Order.asc("updatedAt"));

    private final MileageConversionRepository conversionRepository;

    public MileageConversionQueryService(MileageConversionRepository conversionRepository) {
        this.conversionRepository = conversionRepository;
    }

    public PageRequest pageRequest(int page, int pageSize) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE), VISIBLE_LIST_SORT);
    }

    public PageRequest exportPageRequest(int page, int pageSize) {
        return PageRequest.of(page, pageSize, VISIBLE_LIST_SORT);
    }

    public Page<MileageConversion> mine(String workspaceId, String userId, MileageDateRange range, PageRequest pageRequest) {
        return conversionRepository.findAllByWorkspaceIdAndUserIdAndStatusNotAndExpenseDateBetween(
                workspaceId,
                userId,
                MileageConversionStatus.DELETED,
                range.from(),
                range.to(),
                pageRequest);
    }

    public Page<MileageConversion> team(String workspaceId, String userId, MileageDateRange range, PageRequest pageRequest) {
        if (userId != null) {
            return conversionRepository.findAllByWorkspaceIdAndUserIdAndStatusNotAndExpenseDateBetween(
                    workspaceId, userId, MileageConversionStatus.DELETED, range.from(), range.to(), pageRequest);
        }
        return conversionRepository.findAllByWorkspaceIdAndStatusNotAndExpenseDateBetween(
                workspaceId, MileageConversionStatus.DELETED, range.from(), range.to(), pageRequest);
    }

    public Page<MileageConversion> conversions(
            String workspaceId,
            String userId,
            MileageConversionStatus status,
            MileageDateRange range,
            PageRequest pageRequest) {
        if (userId != null && status != null) {
            return conversionRepository.findAllByWorkspaceIdAndUserIdAndStatusAndExpenseDateBetween(
                    workspaceId, userId, status, range.from(), range.to(), pageRequest);
        }
        if (userId != null) {
            return conversionRepository.findAllByWorkspaceIdAndUserIdAndExpenseDateBetween(
                    workspaceId, userId, range.from(), range.to(), pageRequest);
        }
        if (status != null) {
            return conversionRepository.findAllByWorkspaceIdAndStatusAndExpenseDateBetween(
                    workspaceId, status, range.from(), range.to(), pageRequest);
        }
        return conversionRepository.findAllByWorkspaceIdAndExpenseDateBetween(
                workspaceId, range.from(), range.to(), pageRequest);
    }

    public Page<MileageConversion> convertedForReport(
            String workspaceId,
            String userId,
            MileageDateRange range,
            int maxRows) {
        PageRequest pageRequest = PageRequest.of(0, maxRows, REPORT_SORT);
        if (userId == null) {
            return conversionRepository.findAllByWorkspaceIdAndStatusAndExpenseDateBetween(
                    workspaceId, MileageConversionStatus.CONVERTED, range.from(), range.to(), pageRequest);
        }
        return conversionRepository.findAllByWorkspaceIdAndUserIdAndStatusAndExpenseDateBetween(
                workspaceId, userId, MileageConversionStatus.CONVERTED, range.from(), range.to(), pageRequest);
    }

    public List<MileageConversion> convertedByExpenseIds(String workspaceId, Collection<String> expenseIds) {
        return conversionRepository.findByWorkspaceIdAndStatusAndExpenseIdIn(
                workspaceId, MileageConversionStatus.CONVERTED, expenseIds);
    }
}
