package com.cake.clockify.addon.mileage.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MileageConversionRepository extends JpaRepository<MileageConversion, UUID> {
    Optional<MileageConversion> findByWorkspaceIdAndExpenseId(String workspaceId, String expenseId);
    Page<MileageConversion> findAllByWorkspaceId(String workspaceId, Pageable pageable);
    Page<MileageConversion> findAllByWorkspaceIdAndStatus(String workspaceId, MileageConversionStatus status, Pageable pageable);
    long countByWorkspaceIdAndExpenseId(String workspaceId, String expenseId);
    void deleteByWorkspaceId(String workspaceId);
}
