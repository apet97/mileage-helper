package com.cake.clockify.addon.mileage.audit;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
public class MileageConversionReservationRepository {
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public UUID reserve(
            String workspaceId,
            String expenseId,
            MileageConversionSource source,
            String sourceEventType) {
        UUID id = UUID.randomUUID();
        Object result = entityManager.createNativeQuery("""
                INSERT INTO {h-schema}mileage_conversion AS mc (
                    id, workspace_id, expense_id, source, source_event_type, status, created_at, updated_at
                )
                VALUES (
                    :id, :workspaceId, :expenseId, :source, :sourceEventType, 'RECEIVED', NOW(), NOW()
                )
                ON CONFLICT (workspace_id, expense_id) DO UPDATE
                    SET updated_at = mc.updated_at
                RETURNING id
                """)
                .setParameter("id", id)
                .setParameter("workspaceId", workspaceId)
                .setParameter("expenseId", expenseId)
                .setParameter("source", source.name())
                .setParameter("sourceEventType", sourceEventType)
                .getSingleResult();
        return result instanceof UUID uuid ? uuid : UUID.fromString(result.toString());
    }
}
