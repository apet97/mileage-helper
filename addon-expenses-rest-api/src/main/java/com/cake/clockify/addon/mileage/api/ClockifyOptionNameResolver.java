package com.cake.clockify.addon.mileage.api;

import com.cake.clockify.addon.mileage.audit.MileageConversion;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseGateway;
import com.cake.clockify.addon.mileage.clockify.ClockifyProjectOption;
import com.cake.clockify.addon.mileage.clockify.ClockifyUserOption;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ClockifyOptionNameResolver {
    private final ClockifyExpenseGateway gateway;

    public ClockifyOptionNameResolver(ClockifyExpenseGateway gateway) {
        this.gateway = gateway;
    }

    public Map<String, String> userNamesById(String workspaceId, Collection<MileageConversion> conversions) {
        if (conversions.stream().map(MileageConversion::getUserId).filter(Objects::nonNull).findAny().isEmpty()) {
            return Map.of();
        }
        return allUserNamesById(workspaceId);
    }

    public Map<String, String> projectNamesById(String workspaceId, Collection<MileageConversion> conversions) {
        if (conversions.stream().map(MileageConversion::getProjectId).filter(Objects::nonNull).findAny().isEmpty()) {
            return Map.of();
        }
        return allProjectNamesById(workspaceId);
    }

    public Map<String, String> allUserNamesById(String workspaceId) {
        try {
            return gateway.listUsers(workspaceId).stream()
                    .filter(user -> user.id() != null && !user.id().isBlank() && user.name() != null)
                    .collect(Collectors.toMap(
                            ClockifyUserOption::id,
                            ClockifyUserOption::name,
                            (left, right) -> left));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Map.of();
        } catch (IOException | RuntimeException e) {
            return Map.of();
        }
    }

    public Map<String, String> allProjectNamesById(String workspaceId) {
        try {
            return gateway.listProjects(workspaceId).stream()
                    .filter(project -> project.id() != null && !project.id().isBlank() && project.name() != null)
                    .collect(Collectors.toMap(
                            ClockifyProjectOption::id,
                            ClockifyProjectOption::name,
                            (left, right) -> left));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Map.of();
        } catch (IOException | RuntimeException e) {
            return Map.of();
        }
    }

    public String userNameOrNull(String userId, Map<String, String> userNamesById) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        return userNamesById.get(userId);
    }
}
