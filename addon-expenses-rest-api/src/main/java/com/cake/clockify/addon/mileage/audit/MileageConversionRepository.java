package com.cake.clockify.addon.mileage.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface MileageConversionRepository extends JpaRepository<MileageConversion, UUID> {
    Optional<MileageConversion> findByIdAndWorkspaceId(UUID id, String workspaceId);
    Optional<MileageConversion> findByWorkspaceIdAndExpenseId(String workspaceId, String expenseId);
    Page<MileageConversion> findAllByWorkspaceId(String workspaceId, Pageable pageable);
    Page<MileageConversion> findAllByWorkspaceIdAndUserId(String workspaceId, String userId, Pageable pageable);
    Page<MileageConversion> findAllByWorkspaceIdAndStatus(String workspaceId, MileageConversionStatus status, Pageable pageable);
    Page<MileageConversion> findAllByWorkspaceIdAndExpenseDateBetween(String workspaceId, LocalDate from, LocalDate to, Pageable pageable);
    Page<MileageConversion> findAllByWorkspaceIdAndUserIdAndExpenseDateBetween(String workspaceId, String userId, LocalDate from, LocalDate to, Pageable pageable);
    Page<MileageConversion> findAllByWorkspaceIdAndStatusNotAndExpenseDateBetween(String workspaceId, MileageConversionStatus status, LocalDate from, LocalDate to, Pageable pageable);
    Page<MileageConversion> findAllByWorkspaceIdAndUserIdAndStatusNotAndExpenseDateBetween(String workspaceId, String userId, MileageConversionStatus status, LocalDate from, LocalDate to, Pageable pageable);
    Page<MileageConversion> findAllByWorkspaceIdAndStatusAndExpenseDateBetween(String workspaceId, MileageConversionStatus status, LocalDate from, LocalDate to, Pageable pageable);
    long countByWorkspaceIdAndExpenseId(String workspaceId, String expenseId);
    void deleteByWorkspaceId(String workspaceId);
}
