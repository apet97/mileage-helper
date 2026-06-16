package com.cake.clockify.addon.mileage.policy;

import com.cake.clockify.addon.mileage.api.model.MileageRatePolicyListResponse;
import com.cake.clockify.addon.mileage.api.model.MileageRatePolicyRequest;
import com.cake.clockify.addon.mileage.api.model.MileageRatePolicyResponse;
import com.cake.clockify.addon.mileage.calculation.MileageDecimalPolicy;
import com.cake.clockify.addon.mileage.settings.MileageSettingsRepository;
import com.cake.clockify.addon.mileage.settings.MileageSettingsService;
import com.cake.clockify.addon.mileage.settings.MileageSettingsValidation;
import com.cake.clockify.addon.mileage.settings.MileageWorkspaceSettings;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MileageRatePolicyService {
    private static final LocalDate OPEN_END = LocalDate.of(9999, 12, 31);
    private static final String DEFAULT_POLICY_NAME = "Default mileage rate";

    private final MileageRatePolicyRepository policyRepository;
    private final MileageSettingsRepository settingsRepository;

    public MileageRatePolicyService(
            MileageRatePolicyRepository policyRepository,
            MileageSettingsRepository settingsRepository) {
        this.policyRepository = policyRepository;
        this.settingsRepository = settingsRepository;
    }

    @Transactional(readOnly = true)
    public List<MileageRatePolicyResponse> listPolicies(String workspaceId) {
        return policyRepository.findByWorkspaceIdOrderByEffectiveFromDescCreatedAtDesc(workspaceId)
                .stream()
                .map(MileageRatePolicyResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MileageRatePolicyListResponse listPolicyResponse(String workspaceId) {
        return new MileageRatePolicyListResponse(listPolicies(workspaceId), null);
    }

    @Transactional
    public MileageRatePolicyResponse createPolicy(
            String workspaceId,
            MileageRatePolicyRequest request,
            String updatedByUserId) {
        ValidPolicy valid = validate(request);
        if (valid.active()) {
            ensureNoOverlap(workspaceId, null, valid.effectiveFrom(), valid.effectiveTo());
        }
        MileageRatePolicy policy = new MileageRatePolicy();
        policy.setWorkspaceId(workspaceId);
        apply(policy, valid, updatedByUserId);
        return MileageRatePolicyResponse.from(policyRepository.saveAndFlush(policy));
    }

    @Transactional
    public MileageRatePolicyResponse updatePolicy(
            String workspaceId,
            UUID id,
            MileageRatePolicyRequest request,
            String updatedByUserId) {
        MileageRatePolicy policy = findPolicy(workspaceId, id);
        ValidPolicy valid = validate(request);
        if (valid.active()) {
            ensureNoOverlap(workspaceId, id, valid.effectiveFrom(), valid.effectiveTo());
        }
        apply(policy, valid, updatedByUserId);
        return MileageRatePolicyResponse.from(policyRepository.saveAndFlush(policy));
    }

    @Transactional
    public MileageRatePolicyResponse deactivatePolicy(String workspaceId, UUID id, String updatedByUserId) {
        MileageRatePolicy policy = findPolicy(workspaceId, id);
        if (policy.isActive() && activePolicyCount(workspaceId) == 1 && fallbackRate(workspaceId) == null) {
            throw new IllegalArgumentException("cannot deactivate the last active policy when no fallback rate exists");
        }
        policy.setActive(false);
        policy.setUpdatedByUserId(updatedByUserId);
        return MileageRatePolicyResponse.from(policyRepository.saveAndFlush(policy));
    }

    @Transactional(readOnly = true)
    public MileageRateResolution resolveRate(
            String workspaceId,
            LocalDate expenseDate,
            MileageSettingsValidation fallbackSettings) {
        if (expenseDate != null) {
            List<MileageRatePolicy> policies =
                    policyRepository.findActiveCandidatesForDate(workspaceId, expenseDate);
            if (!policies.isEmpty()) {
                MileageRatePolicy policy = policies.getFirst();
                return MileageRateResolution.policy(policy);
            }
        }
        if (fallbackSettings != null && fallbackSettings.rate() != null) {
            return MileageRateResolution.settingsFallback(fallbackSettings.rate());
        }
        return MileageRateResolution.incomplete(List.of("rate is required"));
    }

    @Transactional
    public MileageRatePolicyResponse ensureDefaultPolicy(
            String workspaceId,
            MileageSettingsValidation settings,
            String updatedByUserId) {
        List<MileageRatePolicy> existing =
                policyRepository.findByWorkspaceIdOrderByEffectiveFromDescCreatedAtDesc(workspaceId);
        if (!existing.isEmpty()) {
            return MileageRatePolicyResponse.from(existing.getFirst());
        }
        if (settings == null || settings.rate() == null) {
            return null;
        }
        MileageRatePolicy policy = new MileageRatePolicy();
        policy.setWorkspaceId(workspaceId);
        policy.setName(DEFAULT_POLICY_NAME);
        policy.setRate(settings.rate());
        policy.setUnit(MileageSettingsService.DEFAULT_UNIT);
        policy.setEffectiveFrom(LocalDate.parse("1970-01-01"));
        policy.setEffectiveTo(null);
        policy.setActive(true);
        policy.setUpdatedByUserId(updatedByUserId);
        return MileageRatePolicyResponse.from(policyRepository.saveAndFlush(policy));
    }

    private MileageRatePolicy findPolicy(String workspaceId, UUID id) {
        return policyRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rate policy not found"));
    }

    private void apply(MileageRatePolicy policy, ValidPolicy valid, String updatedByUserId) {
        policy.setName(valid.name());
        policy.setRate(valid.rate());
        policy.setUnit(MileageSettingsService.DEFAULT_UNIT);
        policy.setEffectiveFrom(valid.effectiveFrom());
        policy.setEffectiveTo(valid.effectiveTo());
        policy.setActive(valid.active());
        policy.setUpdatedByUserId(updatedByUserId);
    }

    private void ensureNoOverlap(String workspaceId, UUID excludeId, LocalDate from, LocalDate to) {
        List<MileageRatePolicy> overlaps = policyRepository.findActivePoliciesOverlapping(
                workspaceId,
                from,
                to == null ? OPEN_END : to).stream()
                .filter(policy -> excludeId == null || !excludeId.equals(policy.getId()))
                .toList();
        if (!overlaps.isEmpty()) {
            throw new IllegalArgumentException("active rate policies may not overlap");
        }
    }

    private long activePolicyCount(String workspaceId) {
        return policyRepository.findByWorkspaceIdOrderByEffectiveFromDescCreatedAtDesc(workspaceId)
                .stream()
                .filter(MileageRatePolicy::isActive)
                .count();
    }

    private BigDecimal fallbackRate(String workspaceId) {
        Optional<MileageWorkspaceSettings> settings = settingsRepository.findById(workspaceId);
        if (settings.isPresent()) {
            return settings.get().getRate();
        }
        return MileageSettingsService.DEFAULT_RATE;
    }

    private static ValidPolicy validate(MileageRatePolicyRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        String name = cleanName(request.name());
        BigDecimal rate = MileageDecimalPolicy.parseOptionalRate(request.rate());
        if (rate == null) {
            throw new IllegalArgumentException("rate is required");
        }
        LocalDate effectiveFrom = request.effectiveFrom();
        if (effectiveFrom == null) {
            throw new IllegalArgumentException("effectiveFrom is required");
        }
        LocalDate effectiveTo = request.effectiveTo();
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveTo must be on or after effectiveFrom");
        }
        return new ValidPolicy(name, rate, effectiveFrom, effectiveTo, request.active() == null || request.active());
    }

    private static String cleanName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        String cleaned = raw.trim();
        if (cleaned.length() > 128) {
            throw new IllegalArgumentException("name must be 128 characters or fewer");
        }
        return cleaned;
    }

    private record ValidPolicy(
            String name,
            BigDecimal rate,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean active
    ) {
    }
}
