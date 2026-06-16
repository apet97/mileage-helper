package com.cake.clockify.addon.mileage.policy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MileageRatePolicyRepository extends JpaRepository<MileageRatePolicy, UUID> {
    List<MileageRatePolicy> findByWorkspaceIdOrderByEffectiveFromDescCreatedAtDesc(String workspaceId);

    Optional<MileageRatePolicy> findByIdAndWorkspaceId(UUID id, String workspaceId);

    @Query("""
            select p from MileageRatePolicy p
            where p.workspaceId = :workspaceId
              and p.active = true
              and p.effectiveFrom <= :expenseDate
              and (p.effectiveTo is null or p.effectiveTo >= :expenseDate)
            order by p.effectiveFrom desc, p.createdAt desc
            """)
    List<MileageRatePolicy> findActiveCandidatesForDate(
            @Param("workspaceId") String workspaceId,
            @Param("expenseDate") LocalDate expenseDate);

    @Query("""
            select p from MileageRatePolicy p
            where p.workspaceId = :workspaceId
              and p.active = true
              and p.effectiveFrom <= :effectiveToMax
              and (p.effectiveTo is null or p.effectiveTo >= :effectiveFrom)
            order by p.effectiveFrom desc, p.createdAt desc
            """)
    List<MileageRatePolicy> findActivePoliciesOverlapping(
            @Param("workspaceId") String workspaceId,
            @Param("effectiveFrom") LocalDate effectiveFrom,
            @Param("effectiveToMax") LocalDate effectiveToMax);
}
