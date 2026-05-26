package com.cake.clockify.addon.mileage.conversion;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.mileage.audit.MileageConversion;
import com.cake.clockify.addon.mileage.audit.MileageConversionRepository;
import com.cake.clockify.addon.mileage.audit.MileageConversionSource;
import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import com.cake.clockify.addon.mileage.audit.MileageSkipReason;
import com.cake.clockify.addon.mileage.calculation.MileageCalculation;
import com.cake.clockify.addon.mileage.calculation.MileageCalculator;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseGateway;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseSnapshot;
import com.cake.clockify.addon.mileage.clockify.UpdateFlatExpenseCommand;
import com.cake.clockify.addon.mileage.note.MileageNoteService;
import com.cake.clockify.addon.mileage.settings.MileageSettingsService;
import com.cake.clockify.addon.mileage.settings.MileageSettingsValidation;
import com.cake.clockify.client.ClockifyApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class MileageConversionService {
    private final MileageSettingsService settingsService;
    private final ClockifyExpenseGateway gateway;
    private final MileageConversionRepository conversionRepository;
    private final MileageEligibilityService eligibilityService;
    private final MileageCalculator calculator;
    private final MileageNoteService noteService;

    public MileageConversionService(
            MileageSettingsService settingsService,
            ClockifyExpenseGateway gateway,
            MileageConversionRepository conversionRepository,
            MileageEligibilityService eligibilityService,
            MileageCalculator calculator,
            MileageNoteService noteService) {
        this.settingsService = settingsService;
        this.gateway = gateway;
        this.conversionRepository = conversionRepository;
        this.eligibilityService = eligibilityService;
        this.calculator = calculator;
        this.noteService = noteService;
    }

    @Transactional
    public ConversionResult convertIfEligible(
            NormalizedClaims claims,
            String expenseId,
            MileageConversionSource source,
            String sourceEventType) {
        String cleanedExpenseId = blankToNull(expenseId);
        if (cleanedExpenseId == null) {
            return new ConversionResult(null, null, MileageConversionStatus.SKIPPED,
                    MileageSkipReason.API_RESOURCE_NOT_FOUND, "Expense id is required");
        }
        MileageConversion conversion = newConversion(claims.workspaceId(), cleanedExpenseId, source, sourceEventType);
        try {
            MileageSettingsValidation settings = settingsService.validateForNativeConversion(claims.workspaceId());
            ClockifyExpenseSnapshot expense = gateway.getExpense(claims.workspaceId(), cleanedExpenseId);
            Optional<MileageConversion> existing = conversionRepository.findByWorkspaceIdAndExpenseId(
                    claims.workspaceId(), cleanedExpenseId);
            conversion = existing.orElse(conversion);
            ensureId(conversion);
            applySnapshot(conversion, expense);
            conversion.setSource(source);
            conversion.setSourceEventType(sourceEventType);

            if (source == MileageConversionSource.WEBHOOK_RESTORED
                    && (isSuccessfullyConverted(existing) || noteService.hasMileageMarker(expense.notes()))) {
                conversion.setStatus(MileageConversionStatus.RESTORED_IGNORED);
                conversion.setSkipReason(MileageSkipReason.ALREADY_CONVERTED);
                conversionRepository.saveAndFlush(conversion);
                return result(conversion, "Restored expense already appears converted");
            }

            EligibilityDecision decision = eligibilityService.evaluate(settings, expense, isSuccessfullyConverted(existing));
            if (decision.dryRun()) {
                MileageCalculation calculation = calculator.calculate(expense.quantity().toPlainString(),
                        settings.rate().toPlainString(), settings.roundingMode());
                applyCalculation(conversion, settings, calculation, null);
                conversion.setStatus(MileageConversionStatus.DRY_RUN);
                conversion.setSkipReason(MileageSkipReason.DRY_RUN);
                conversionRepository.saveAndFlush(conversion);
                return result(conversion, decision.message());
            }
            if (!decision.eligible()) {
                conversion.setStatus(MileageConversionStatus.SKIPPED);
                conversion.setSkipReason(decision.skipReason());
                conversionRepository.saveAndFlush(conversion);
                return result(conversion, decision.message());
            }

            MileageCalculation calculation = calculator.calculate(expense.quantity().toPlainString(),
                    settings.rate().toPlainString(), settings.roundingMode());
            String marker = noteService.marker(conversion.getId());
            String note = noteService.buildConvertedNote(
                    expense.notes(),
                    calculation,
                    settings.unit(),
                    conversion.getId(),
                    settings.noteTemplate());
            applyCalculation(conversion, settings, calculation, marker);
            conversion.setStatus(MileageConversionStatus.CONVERTING);
            conversionRepository.saveAndFlush(conversion);

            gateway.updateFlatExpense(claims.workspaceId(), cleanedExpenseId, new UpdateFlatExpenseCommand(
                    settings.outputCategoryId(),
                    expense.userId(),
                    expense.date(),
                    expense.projectId(),
                    expense.taskId(),
                    expense.billable(),
                    clockifyExpenseAmount(settings, expense, calculation),
                    note,
                    calculation.roundingMode(),
                    singleMileageCategory(settings)));

            conversion.setStatus(MileageConversionStatus.CONVERTED);
            conversion.setConvertedAt(Instant.now());
            conversion.setSkipReason(null);
            conversion.setErrorCode(null);
            conversion.setErrorMessage(null);
            conversionRepository.saveAndFlush(conversion);
            return result(conversion, "Converted mileage expense");
        } catch (ClockifyApiException e) {
            return fail(conversion, "clockify_api_error",
                    "Clockify API request failed with status " + e.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fail(conversion, "interrupted", "Mileage conversion was interrupted");
        } catch (IOException e) {
            return fail(conversion, "clockify_io_error", "Clockify API request could not be completed");
        }
    }

    @Transactional
    public ConversionResult markDeleted(NormalizedClaims claims, String expenseId) {
        String cleanedExpenseId = blankToNull(expenseId);
        if (cleanedExpenseId == null) {
            return new ConversionResult(null, null, MileageConversionStatus.SKIPPED,
                    MileageSkipReason.API_RESOURCE_NOT_FOUND, "Expense id is required");
        }
        return conversionRepository.findByWorkspaceIdAndExpenseId(claims.workspaceId(), cleanedExpenseId)
                .map(conversion -> {
                    conversion.setStatus(MileageConversionStatus.DELETED);
                    conversion.setDeletedAt(Instant.now());
                    conversion.setSkipReason(null);
                    conversionRepository.saveAndFlush(conversion);
                    return result(conversion, "Mileage conversion marked deleted");
                })
                .orElseGet(() -> new ConversionResult(null, cleanedExpenseId, MileageConversionStatus.SKIPPED,
                        MileageSkipReason.API_RESOURCE_NOT_FOUND, "No mileage conversion exists for deleted expense"));
    }

    @Transactional
    public ConversionResult retry(NormalizedClaims claims, UUID conversionId) {
        if (conversionId == null) {
            return new ConversionResult(null, null, MileageConversionStatus.SKIPPED,
                    MileageSkipReason.API_RESOURCE_NOT_FOUND, "Conversion id is required");
        }
        return conversionRepository.findByIdAndWorkspaceId(conversionId, claims.workspaceId())
                .map(conversion -> {
                    MileageConversionSource source = conversion.getSource() == null
                            ? MileageConversionSource.WEBHOOK_UPDATED
                            : conversion.getSource();
                    return convertIfEligible(claims, conversion.getExpenseId(), source, "RETRY");
                })
                .orElseGet(() -> new ConversionResult(conversionId, null, MileageConversionStatus.SKIPPED,
                        MileageSkipReason.API_RESOURCE_NOT_FOUND, "Mileage conversion was not found"));
    }

    private ConversionResult fail(MileageConversion conversion, String errorCode, String message) {
        ensureId(conversion);
        conversion.setStatus(MileageConversionStatus.FAILED);
        conversion.setErrorCode(errorCode);
        conversion.setErrorMessage(message);
        conversionRepository.saveAndFlush(conversion);
        return result(conversion, message);
    }

    private static ConversionResult result(MileageConversion conversion, String message) {
        return new ConversionResult(
                conversion.getId(),
                conversion.getExpenseId(),
                conversion.getStatus(),
                conversion.getSkipReason(),
                message);
    }

    private static MileageConversion newConversion(
            String workspaceId,
            String expenseId,
            MileageConversionSource source,
            String sourceEventType) {
        MileageConversion conversion = new MileageConversion();
        conversion.setId(UUID.randomUUID());
        conversion.setWorkspaceId(workspaceId);
        conversion.setExpenseId(expenseId);
        conversion.setSource(source);
        conversion.setSourceEventType(sourceEventType);
        conversion.setStatus(MileageConversionStatus.RECEIVED);
        return conversion;
    }

    private static void ensureId(MileageConversion conversion) {
        if (conversion.getId() == null) {
            conversion.setId(UUID.randomUUID());
        }
    }

    private static void applySnapshot(MileageConversion conversion, ClockifyExpenseSnapshot expense) {
        conversion.setSourceCategoryId(expense.categoryId());
        conversion.setUserId(expense.userId());
        conversion.setProjectId(expense.projectId());
        conversion.setTaskId(expense.taskId());
        conversion.setExpenseDate(expenseDate(expense.date()));
    }

    private static LocalDate expenseDate(String value) {
        String cleaned = blankToNull(value);
        if (cleaned == null) {
            return null;
        }
        String datePortion = cleaned.length() >= 10 ? cleaned.substring(0, 10) : cleaned;
        try {
            return LocalDate.parse(datePortion);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static void applyCalculation(
            MileageConversion conversion,
            MileageSettingsValidation settings,
            MileageCalculation calculation,
            String marker) {
        conversion.setTargetCategoryId(settings.outputCategoryId());
        conversion.setMiles(calculation.miles());
        conversion.setRate(calculation.rate());
        conversion.setCalculatedAmount(calculation.calculatedAmount());
        conversion.setRoundedAmount(calculation.roundedAmount());
        conversion.setRoundingMode(calculation.roundingMode().name());
        conversion.setNoteMarker(marker);
    }

    private static boolean isSuccessfullyConverted(Optional<MileageConversion> existing) {
        return existing
                .map(MileageConversion::getStatus)
                .filter(status -> status == MileageConversionStatus.CONVERTED || status == MileageConversionStatus.CONVERTING)
                .isPresent();
    }

    private static BigDecimal clockifyExpenseAmount(
            MileageSettingsValidation settings,
            ClockifyExpenseSnapshot expense,
            MileageCalculation calculation) {
        if (singleMileageCategory(settings)) {
            return expense.quantity();
        }
        return calculation.roundedAmount();
    }

    private static boolean singleMileageCategory(MileageSettingsValidation settings) {
        return Objects.equals(settings.inputCategoryId(), settings.outputCategoryId());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
