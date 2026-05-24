package com.cake.clockify.addon.mileage.conversion;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.mileage.audit.MileageConversion;
import com.cake.clockify.addon.mileage.audit.MileageConversionRepository;
import com.cake.clockify.addon.mileage.audit.MileageConversionSource;
import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import com.cake.clockify.addon.mileage.audit.MileageSkipReason;
import com.cake.clockify.addon.mileage.calculation.MileageCalculator;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseGateway;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseSnapshot;
import com.cake.clockify.addon.mileage.clockify.UpdateFlatExpenseCommand;
import com.cake.clockify.addon.mileage.note.MileageNoteService;
import com.cake.clockify.addon.mileage.settings.MileageSettingsService;
import com.cake.clockify.addon.mileage.settings.MileageSettingsValidation;
import com.cake.clockify.client.ClockifyApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MileageConversionServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MileageSettingsService settingsService;
    private ClockifyExpenseGateway gateway;
    private MileageConversionRepository conversionRepository;
    private MileageConversionService service;

    @BeforeEach
    void setUp() {
        settingsService = mock(MileageSettingsService.class);
        gateway = mock(ClockifyExpenseGateway.class);
        conversionRepository = mock(MileageConversionRepository.class);
        service = new MileageConversionService(
                settingsService,
                gateway,
                conversionRepository,
                new MileageEligibilityService(),
                new MileageCalculator(),
                new MileageNoteService());
        when(conversionRepository.saveAndFlush(any(MileageConversion.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void eligibleCreatedWebhookConvertsExpenseOnce() throws Exception {
        when(settingsService.validateForNativeConversion("ws-native")).thenReturn(settings(false));
        when(conversionRepository.findByWorkspaceIdAndExpenseId("ws-native", "exp-1")).thenReturn(Optional.empty());
        when(gateway.getExpense("ws-native", "exp-1")).thenReturn(inputExpense("exp-1"));
        when(gateway.updateFlatExpense(eq("ws-native"), eq("exp-1"), any(UpdateFlatExpenseCommand.class)))
                .thenReturn(objectMapper.createObjectNode().put("id", "exp-1"));

        ConversionResult result = service.convertIfEligible(claims(), "exp-1", MileageConversionSource.WEBHOOK_CREATED, "EXPENSE_CREATED");

        assertThat(result.status()).isEqualTo(MileageConversionStatus.CONVERTED);
        verify(gateway).updateFlatExpense(eq("ws-native"), eq("exp-1"), any(UpdateFlatExpenseCommand.class));
        ArgumentCaptor<MileageConversion> saved = ArgumentCaptor.forClass(MileageConversion.class);
        verify(conversionRepository, org.mockito.Mockito.atLeastOnce()).saveAndFlush(saved.capture());
        assertThat(saved.getAllValues().getLast().getStatus()).isEqualTo(MileageConversionStatus.CONVERTED);
    }

    @Test
    void conversionUsesFetchedQuantityAsMiles() throws Exception {
        when(settingsService.validateForNativeConversion("ws-native")).thenReturn(settings(false));
        when(conversionRepository.findByWorkspaceIdAndExpenseId("ws-native", "exp-1")).thenReturn(Optional.empty());
        when(gateway.getExpense("ws-native", "exp-1")).thenReturn(inputExpense("exp-1"));
        when(gateway.updateFlatExpense(eq("ws-native"), eq("exp-1"), any(UpdateFlatExpenseCommand.class)))
                .thenReturn(objectMapper.createObjectNode().put("id", "exp-1"));

        service.convertIfEligible(claims(), "exp-1", MileageConversionSource.WEBHOOK_CREATED, "EXPENSE_CREATED");

        ArgumentCaptor<MileageConversion> saved = ArgumentCaptor.forClass(MileageConversion.class);
        verify(conversionRepository, org.mockito.Mockito.atLeastOnce()).saveAndFlush(saved.capture());
        assertThat(saved.getAllValues().getLast().getMiles()).isEqualByComparingTo(new BigDecimal("37.4"));
    }

    @Test
    void conversionUpdatesSameExpenseToOutputCategory() throws Exception {
        when(settingsService.validateForNativeConversion("ws-native")).thenReturn(settings(false));
        when(conversionRepository.findByWorkspaceIdAndExpenseId("ws-native", "exp-1")).thenReturn(Optional.empty());
        when(gateway.getExpense("ws-native", "exp-1")).thenReturn(inputExpense("exp-1"));
        when(gateway.updateFlatExpense(eq("ws-native"), eq("exp-1"), any(UpdateFlatExpenseCommand.class)))
                .thenReturn(objectMapper.createObjectNode().put("id", "exp-1"));

        service.convertIfEligible(claims(), "exp-1", MileageConversionSource.WEBHOOK_CREATED, "EXPENSE_CREATED");

        ArgumentCaptor<UpdateFlatExpenseCommand> command = ArgumentCaptor.forClass(UpdateFlatExpenseCommand.class);
        verify(gateway).updateFlatExpense(eq("ws-native"), eq("exp-1"), command.capture());
        assertThat(command.getValue().categoryId()).isEqualTo("cat-mileage");
        assertThat(command.getValue().userId()).isEqualTo("user-1");
        assertThat(command.getValue().date()).isEqualTo("2026-05-24T12:00:00Z");
        assertThat(command.getValue().projectId()).isEqualTo("project-1");
        assertThat(command.getValue().taskId()).isEqualTo("task-1");
        assertThat(command.getValue().billable()).isTrue();
        assertThat(command.getValue().amount()).isEqualByComparingTo(new BigDecimal("37.4"));
        assertThat(command.getValue().amountIsQuantity()).isTrue();
    }

    @Test
    void distinctCategoryConversionSendsRoundedExpenseAmount() throws Exception {
        when(settingsService.validateForNativeConversion("ws-native")).thenReturn(distinctCategorySettings(false));
        when(conversionRepository.findByWorkspaceIdAndExpenseId("ws-native", "exp-1")).thenReturn(Optional.empty());
        when(gateway.getExpense("ws-native", "exp-1")).thenReturn(distinctInputExpense("exp-1"));
        when(gateway.updateFlatExpense(eq("ws-native"), eq("exp-1"), any(UpdateFlatExpenseCommand.class)))
                .thenReturn(objectMapper.createObjectNode().put("id", "exp-1"));

        service.convertIfEligible(claims(), "exp-1", MileageConversionSource.WEBHOOK_CREATED, "EXPENSE_CREATED");

        ArgumentCaptor<UpdateFlatExpenseCommand> command = ArgumentCaptor.forClass(UpdateFlatExpenseCommand.class);
        verify(gateway).updateFlatExpense(eq("ws-native"), eq("exp-1"), command.capture());
        assertThat(command.getValue().categoryId()).isEqualTo("cat-output");
        assertThat(command.getValue().amount()).isEqualByComparingTo(new BigDecimal("24.50"));
        assertThat(command.getValue().amountIsQuantity()).isFalse();
    }

    @Test
    void conversionReplacesNativeNoteWithCleanExactGeneratedNote() throws Exception {
        when(settingsService.validateForNativeConversion("ws-native")).thenReturn(new MileageSettingsValidation(
                "ws-native", true, true, new BigDecimal("0.725"), "mile",
                "cat-mileage", "cat-mileage", RoundingMode.HALF_UP, true, true, false, false, false, null, List.of()));
        when(conversionRepository.findByWorkspaceIdAndExpenseId("ws-native", "exp-1")).thenReturn(Optional.empty());
        when(gateway.getExpense("ws-native", "exp-1")).thenReturn(new ClockifyExpenseSnapshot(
                "exp-1", "ws-native", "user-1", "2026-05-24T12:00:00Z", "project-1", "task-1",
                "cat-mileage", "Native mileage", new BigDecimal("1"), true, null, null, false));
        when(gateway.updateFlatExpense(eq("ws-native"), eq("exp-1"), any(UpdateFlatExpenseCommand.class)))
                .thenReturn(objectMapper.createObjectNode().put("id", "exp-1"));

        service.convertIfEligible(claims(), "exp-1", MileageConversionSource.WEBHOOK_CREATED, "EXPENSE_CREATED");

        ArgumentCaptor<UpdateFlatExpenseCommand> command = ArgumentCaptor.forClass(UpdateFlatExpenseCommand.class);
        verify(gateway).updateFlatExpense(eq("ws-native"), eq("exp-1"), command.capture());
        assertThat(command.getValue().amount()).isEqualByComparingTo(new BigDecimal("1"));
        assertThat(command.getValue().amountIsQuantity()).isTrue();
        assertThat(command.getValue().notes())
                .isEqualTo("Mileage reimbursement: 1 mile x 0.725 = 0.725. Created/converted by Mileage for Clockify.");
    }

    @Test
    void singleCategoryMileageExpenseDoesNotSkipAsAlreadyOutputCategory() throws Exception {
        when(settingsService.validateForNativeConversion("ws-native")).thenReturn(settings(false));
        when(conversionRepository.findByWorkspaceIdAndExpenseId("ws-native", "exp-1")).thenReturn(Optional.empty());
        when(gateway.getExpense("ws-native", "exp-1")).thenReturn(inputExpense("exp-1"));
        when(gateway.updateFlatExpense(eq("ws-native"), eq("exp-1"), any(UpdateFlatExpenseCommand.class)))
                .thenReturn(objectMapper.createObjectNode().put("id", "exp-1"));

        service.convertIfEligible(claims(), "exp-1", MileageConversionSource.WEBHOOK_CREATED, "EXPENSE_CREATED");

        ArgumentCaptor<UpdateFlatExpenseCommand> command = ArgumentCaptor.forClass(UpdateFlatExpenseCommand.class);
        verify(gateway).updateFlatExpense(eq("ws-native"), eq("exp-1"), command.capture());
        assertThat(command.getValue().categoryId()).isEqualTo("cat-mileage");
    }

    @Test
    void dryRunCreatesDryRunAuditAndDoesNotUpdateClockify() throws Exception {
        when(settingsService.validateForNativeConversion("ws-native")).thenReturn(settings(true));
        when(conversionRepository.findByWorkspaceIdAndExpenseId("ws-native", "exp-1")).thenReturn(Optional.empty());
        when(gateway.getExpense("ws-native", "exp-1")).thenReturn(inputExpense("exp-1"));

        ConversionResult result = service.convertIfEligible(claims(), "exp-1", MileageConversionSource.WEBHOOK_CREATED, "EXPENSE_CREATED");

        assertThat(result.status()).isEqualTo(MileageConversionStatus.DRY_RUN);
        verify(gateway, never()).updateFlatExpense(any(), any(), any());
    }

    @Test
    void existingConvertedAuditPreventsSecondUpdate() throws Exception {
        MileageConversion existing = existing(MileageConversionStatus.CONVERTED);
        when(settingsService.validateForNativeConversion("ws-native")).thenReturn(settings(false));
        when(conversionRepository.findByWorkspaceIdAndExpenseId("ws-native", "exp-1")).thenReturn(Optional.of(existing));
        when(gateway.getExpense("ws-native", "exp-1")).thenReturn(inputExpense("exp-1"));

        ConversionResult result = service.convertIfEligible(claims(), "exp-1", MileageConversionSource.WEBHOOK_CREATED, "EXPENSE_CREATED");

        assertThat(result.status()).isEqualTo(MileageConversionStatus.SKIPPED);
        assertThat(result.skipReason()).isEqualTo(MileageSkipReason.ALREADY_CONVERTED);
        verify(gateway, never()).updateFlatExpense(any(), any(), any());
    }

    @Test
    void outputCategoryPreventsSecondUpdate() throws Exception {
        when(settingsService.validateForNativeConversion("ws-native")).thenReturn(distinctCategorySettings(false));
        when(conversionRepository.findByWorkspaceIdAndExpenseId("ws-native", "exp-1")).thenReturn(Optional.empty());
        when(gateway.getExpense("ws-native", "exp-1")).thenReturn(outputExpense("exp-1"));

        ConversionResult result = service.convertIfEligible(claims(), "exp-1", MileageConversionSource.WEBHOOK_UPDATED, "EXPENSE_UPDATED");

        assertThat(result.status()).isEqualTo(MileageConversionStatus.SKIPPED);
        assertThat(result.skipReason()).isEqualTo(MileageSkipReason.ALREADY_OUTPUT_CATEGORY);
        verify(gateway, never()).updateFlatExpense(any(), any(), any());
    }

    @Test
    void markerPreventsSecondUpdate() throws Exception {
        when(settingsService.validateForNativeConversion("ws-native")).thenReturn(settings(false));
        when(conversionRepository.findByWorkspaceIdAndExpenseId("ws-native", "exp-1")).thenReturn(Optional.empty());
        when(gateway.getExpense("ws-native", "exp-1")).thenReturn(markedExpense("exp-1"));

        ConversionResult result = service.convertIfEligible(claims(), "exp-1", MileageConversionSource.WEBHOOK_UPDATED, "EXPENSE_UPDATED");

        assertThat(result.status()).isEqualTo(MileageConversionStatus.SKIPPED);
        assertThat(result.skipReason()).isEqualTo(MileageSkipReason.ALREADY_MARKED);
        verify(gateway, never()).updateFlatExpense(any(), any(), any());
    }

    @Test
    void clockifyConflictRecordsFailedSanitized() throws Exception {
        when(settingsService.validateForNativeConversion("ws-native")).thenReturn(settings(false));
        when(conversionRepository.findByWorkspaceIdAndExpenseId("ws-native", "exp-1")).thenReturn(Optional.empty());
        when(gateway.getExpense("ws-native", "exp-1")).thenReturn(inputExpense("exp-1"));
        when(gateway.updateFlatExpense(eq("ws-native"), eq("exp-1"), any(UpdateFlatExpenseCommand.class)))
                .thenThrow(ClockifyApiException.forStatus(409, Map.of("Authorization", List.of("Bearer super-secret")), "{\"message\":\"locked\"}"));

        ConversionResult result = service.convertIfEligible(claims(), "exp-1", MileageConversionSource.WEBHOOK_UPDATED, "EXPENSE_UPDATED");

        assertThat(result.status()).isEqualTo(MileageConversionStatus.FAILED);
        ArgumentCaptor<MileageConversion> saved = ArgumentCaptor.forClass(MileageConversion.class);
        verify(conversionRepository, org.mockito.Mockito.atLeastOnce()).saveAndFlush(saved.capture());
        MileageConversion failed = saved.getAllValues().getLast();
        assertThat(failed.getErrorCode()).isEqualTo("clockify_api_error");
        assertThat(failed.getErrorMessage()).contains("status 409");
        assertThat(failed.getErrorMessage()).doesNotContain("super-secret");
    }

    @Test
    void crossWorkspaceFetchedExpenseIsSkipped() throws Exception {
        when(settingsService.validateForNativeConversion("ws-native")).thenReturn(settings(false));
        when(conversionRepository.findByWorkspaceIdAndExpenseId("ws-native", "exp-1")).thenReturn(Optional.empty());
        when(gateway.getExpense("ws-native", "exp-1")).thenReturn(new ClockifyExpenseSnapshot(
                "exp-1", "other-ws", "user-1", "2026-05-24", "project-1", "task-1",
                "cat-input", "Native mileage", new BigDecimal("37.4"), true, null, null, false));

        ConversionResult result = service.convertIfEligible(claims(), "exp-1", MileageConversionSource.WEBHOOK_CREATED, "EXPENSE_CREATED");

        assertThat(result.status()).isEqualTo(MileageConversionStatus.SKIPPED);
        assertThat(result.skipReason()).isEqualTo(MileageSkipReason.WORKSPACE_MISMATCH);
        verify(gateway, never()).updateFlatExpense(any(), any(), any());
    }

    @Test
    void markDeletedDoesNotHardDeleteConversion() {
        MileageConversion existing = existing(MileageConversionStatus.CONVERTED);
        when(conversionRepository.findByWorkspaceIdAndExpenseId("ws-native", "exp-1")).thenReturn(Optional.of(existing));

        ConversionResult result = service.markDeleted(claims(), "exp-1");

        assertThat(result.status()).isEqualTo(MileageConversionStatus.DELETED);
        assertThat(existing.getDeletedAt()).isNotNull();
        verify(conversionRepository).saveAndFlush(existing);
        verify(conversionRepository, never()).delete(any());
    }

    @Test
    void restoredAlreadyConvertedExpenseReturnsRestoredIgnored() throws Exception {
        MileageConversion existing = existing(MileageConversionStatus.CONVERTED);
        when(settingsService.validateForNativeConversion("ws-native")).thenReturn(settings(false));
        when(conversionRepository.findByWorkspaceIdAndExpenseId("ws-native", "exp-1")).thenReturn(Optional.of(existing));
        when(gateway.getExpense("ws-native", "exp-1")).thenReturn(outputExpense("exp-1"));

        ConversionResult result = service.convertIfEligible(claims(), "exp-1", MileageConversionSource.WEBHOOK_RESTORED, "EXPENSE_RESTORED");

        assertThat(result.status()).isEqualTo(MileageConversionStatus.RESTORED_IGNORED);
        verify(gateway, never()).updateFlatExpense(any(), any(), any());
    }

    @Test
    void restoredInputCategoryExpenseConvertsWhenNoSuccessfulConversionExists() throws Exception {
        when(settingsService.validateForNativeConversion("ws-native")).thenReturn(settings(false));
        when(conversionRepository.findByWorkspaceIdAndExpenseId("ws-native", "exp-1")).thenReturn(Optional.empty());
        when(gateway.getExpense("ws-native", "exp-1")).thenReturn(inputExpense("exp-1"));
        when(gateway.updateFlatExpense(eq("ws-native"), eq("exp-1"), any(UpdateFlatExpenseCommand.class)))
                .thenReturn(objectMapper.createObjectNode().put("id", "exp-1"));

        ConversionResult result = service.convertIfEligible(claims(), "exp-1", MileageConversionSource.WEBHOOK_RESTORED, "EXPENSE_RESTORED");

        assertThat(result.status()).isEqualTo(MileageConversionStatus.CONVERTED);
        verify(gateway).updateFlatExpense(eq("ws-native"), eq("exp-1"), any(UpdateFlatExpenseCommand.class));
    }

    private static NormalizedClaims claims() {
        return new NormalizedClaims("ws-native", "mileage-for-clockify", "https://backend.example.test",
                "https://reports.example.test", null, null, "user-claims", "OWNER", "en", "DEFAULT", "UTC", Instant.now());
    }

    private static MileageSettingsValidation settings(boolean dryRun) {
        return new MileageSettingsValidation("ws-native", true, true, new BigDecimal("0.655"), "mile",
                "cat-mileage", "cat-mileage", RoundingMode.HALF_UP, true, true, false, dryRun, false, null, List.of());
    }

    private static MileageSettingsValidation distinctCategorySettings(boolean dryRun) {
        return new MileageSettingsValidation("ws-native", true, true, new BigDecimal("0.655"), "mile",
                "cat-input", "cat-output", RoundingMode.HALF_UP, true, true, false, dryRun, false, null, List.of());
    }

    private static ClockifyExpenseSnapshot inputExpense(String id) {
        return new ClockifyExpenseSnapshot(id, "ws-native", "user-1", "2026-05-24T12:00:00Z", "project-1", "task-1",
                "cat-mileage", "Native mileage", new BigDecimal("37.4"), true, null, null, false);
    }

    private static ClockifyExpenseSnapshot distinctInputExpense(String id) {
        return new ClockifyExpenseSnapshot(id, "ws-native", "user-1", "2026-05-24T12:00:00Z", "project-1", "task-1",
                "cat-input", "Native mileage", new BigDecimal("37.4"), true, null, null, false);
    }

    private static ClockifyExpenseSnapshot outputExpense(String id) {
        return new ClockifyExpenseSnapshot(id, "ws-native", "user-1", "2026-05-24", "project-1", "task-1",
                "cat-output", "Converted mileage", null, true, null, new BigDecimal("24.50"), false);
    }

    private static ClockifyExpenseSnapshot markedExpense(String id) {
        return new ClockifyExpenseSnapshot(id, "ws-native", "user-1", "2026-05-24", "project-1", "task-1",
                "cat-input", "Native mileage [MileageAddon:converted:v1 id=00000000-0000-0000-0000-000000000123]",
                new BigDecimal("37.4"), true, null, null, false);
    }

    private static MileageConversion existing(MileageConversionStatus status) {
        MileageConversion conversion = new MileageConversion();
        conversion.setWorkspaceId("ws-native");
        conversion.setExpenseId("exp-1");
        conversion.setStatus(status);
        conversion.setSource(MileageConversionSource.WEBHOOK_CREATED);
        conversion.setMiles(new BigDecimal("37.4"));
        conversion.setRate(new BigDecimal("0.655"));
        conversion.setRoundedAmount(new BigDecimal("24.50"));
        return conversion;
    }
}
