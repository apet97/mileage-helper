package com.cake.clockify.addon.mileage.api;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.mileage.api.model.CreateMileageExpenseRequest;
import com.cake.clockify.addon.mileage.api.model.MileageCreateContextResponse;
import com.cake.clockify.addon.mileage.api.model.MileageCreateExpenseResponse;
import com.cake.clockify.addon.mileage.api.model.MileagePreviewRequest;
import com.cake.clockify.addon.mileage.api.model.MileagePreviewResponse;
import com.cake.clockify.addon.mileage.audit.MileageConversion;
import com.cake.clockify.addon.mileage.audit.MileageConversionRepository;
import com.cake.clockify.addon.mileage.audit.MileageConversionSource;
import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import com.cake.clockify.addon.mileage.calculation.MileageCalculation;
import com.cake.clockify.addon.mileage.calculation.MileageCalculator;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseGateway;
import com.cake.clockify.addon.mileage.clockify.CreateFlatExpenseCommand;
import com.cake.clockify.addon.mileage.note.MileageNoteService;
import com.cake.clockify.addon.mileage.settings.MileageSettingsService;
import com.cake.clockify.addon.mileage.settings.MileageSettingsValidation;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/mileage")
public class MileageApiController {
    private static final long MAX_RECEIPT_BYTES = 10L * 1024L * 1024L;
    private static final Set<String> ALLOWED_RECEIPT_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp", "image/heic", "application/pdf");
    private static final Set<String> MULTIPART_FIELDS = Set.of(
            "date", "projectId", "miles", "rate", "billable", "notes");

    private final MileageSettingsService settingsService;
    private final MileageCalculator calculator;
    private final ClockifyExpenseGateway gateway;
    private final MileageConversionRepository conversionRepository;
    private final MileageNoteService noteService;

    public MileageApiController(
            MileageSettingsService settingsService,
            MileageCalculator calculator,
            ClockifyExpenseGateway gateway,
            MileageConversionRepository conversionRepository,
            MileageNoteService noteService) {
        this.settingsService = settingsService;
        this.calculator = calculator;
        this.gateway = gateway;
        this.conversionRepository = conversionRepository;
        this.noteService = noteService;
    }

    @PostMapping(value = "/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MileagePreviewResponse> preview(
            HttpServletRequest request,
            @Valid @RequestBody MileagePreviewRequest body) {
        NormalizedClaims claims = RequestAttributes.requireClaims(request);
        MileageSettingsValidation settings = requireAddonSettings(claims.workspaceId());
        MileageCalculation calculation = calculation(body.miles(), body.rate(), settings);
        return ResponseEntity.ok(new MileagePreviewResponse(
                calculation.milesText(),
                calculation.rateText(),
                calculation.calculatedAmountText(),
                calculation.roundedAmountText()));
    }

    @GetMapping("/create-context")
    public ResponseEntity<MileageCreateContextResponse> createContext(HttpServletRequest request) {
        NormalizedClaims claims = RequestAttributes.requireClaims(request);
        MileageSettingsValidation settings = settingsService.validateForAddonCreate(claims.workspaceId());
        return ResponseEntity.ok(MileageCreateContextResponse.from(settings));
    }

    @PostMapping(value = "/expenses", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MileageCreateExpenseResponse> createExpenseJson(
            HttpServletRequest request,
            @Valid @RequestBody CreateMileageExpenseRequest body) throws IOException, InterruptedException {
        NormalizedClaims claims = RequestAttributes.requireClaims(request);
        MileageSettingsValidation settings = requireAddonSettings(claims.workspaceId());
        return ResponseEntity.ok(createExpense(claims.workspaceId(), required("userId", claims.userId()), settings, body, null));
    }

    @PostMapping(value = "/expenses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MileageCreateExpenseResponse> createExpenseMultipart(
            HttpServletRequest request,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam Map<String, String> params) throws IOException, InterruptedException {
        NormalizedClaims claims = RequestAttributes.requireClaims(request);
        MileageSettingsValidation settings = requireAddonSettings(claims.workspaceId());
        CreateMileageExpenseRequest body = requestFromMultipart(params);
        return ResponseEntity.ok(createExpense(claims.workspaceId(), required("userId", claims.userId()), settings, body, file));
    }

    private MileageCreateExpenseResponse createExpense(
            String workspaceId,
            String userId,
            MileageSettingsValidation settings,
            CreateMileageExpenseRequest request,
            MultipartFile file) throws IOException, InterruptedException {
        MileageCalculation calculation = calculation(request.miles(), request.rate(), settings);
        UUID conversionId = UUID.randomUUID();
        LocalDate expenseDate = parseExpenseDate(request.date());
        String note = noteService.buildConvertedNote(
                request.notes(),
                calculation,
                settings.unit(),
                conversionId,
                settings.noteTemplate());
        CreateFlatExpenseCommand command = new CreateFlatExpenseCommand(
                settings.outputCategoryId(),
                userId,
                expenseDate,
                blankToNull(request.projectId()),
                null,
                clockifyExpenseAmount(settings, calculation),
                billableOrDefault(request.billable()),
                note,
                settings.roundingMode(),
                singleMileageCategory(settings));
        JsonNode response = createClockifyExpense(workspaceId, command, file);
        String expenseId = response.path("id").asText(null);
        if (expenseId == null || expenseId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Clockify did not return a created expense id");
        }
        MileageConversion conversion = conversion(
                conversionId,
                workspaceId,
                expenseId,
                userId,
                request,
                expenseDate,
                settings,
                calculation,
                noteService.marker(conversionId));
        conversionRepository.saveAndFlush(conversion);
        return new MileageCreateExpenseResponse(
                expenseId,
                conversionId,
                calculation.milesText(),
                calculation.rateText(),
                calculation.calculatedAmountText(),
                calculation.roundedAmountText());
    }

    private JsonNode createClockifyExpense(String workspaceId, CreateFlatExpenseCommand command, MultipartFile file)
            throws IOException, InterruptedException {
        if (file != null && !file.isEmpty()) {
            validateReceipt(file);
            return gateway.createFlatExpenseWithReceipt(
                    workspaceId,
                    command,
                    safeFileName(file.getOriginalFilename()),
                    safeContentType(file.getContentType()),
                    file.getBytes());
        }
        return gateway.createFlatExpense(workspaceId, command);
    }

    private MileageSettingsValidation requireAddonSettings(String workspaceId) {
        MileageSettingsValidation settings = settingsService.validateForAddonCreate(workspaceId);
        if (!settings.complete()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Mileage settings are incomplete: " + String.join(", ", settings.diagnostics()));
        }
        return settings;
    }

    private static LocalDate parseExpenseDate(String raw) {
        try {
            return LocalDate.parse(required("date", raw));
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date must use YYYY-MM-DD", e);
        }
    }

    private MileageCalculation calculation(String miles, String requestedRate, MileageSettingsValidation settings) {
        String rate = settings.allowUserRateOverride() && requestedRate != null && !requestedRate.isBlank()
                ? requestedRate
                : settings.rate().toPlainString();
        return calculator.calculate(miles, rate, settings.roundingMode());
    }

    private static java.math.BigDecimal clockifyExpenseAmount(
            MileageSettingsValidation settings,
            MileageCalculation calculation) {
        if (singleMileageCategory(settings)) {
            return calculation.miles();
        }
        return calculation.roundedAmount();
    }

    private static boolean singleMileageCategory(MileageSettingsValidation settings) {
        return Objects.equals(settings.inputCategoryId(), settings.outputCategoryId());
    }

    private static CreateMileageExpenseRequest requestFromMultipart(Map<String, String> params) {
        Map<String, String> safe = params.entrySet().stream()
                .filter(entry -> MULTIPART_FIELDS.contains(entry.getKey()))
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left));
        return new CreateMileageExpenseRequest(
                safe.get("date"),
                safe.get("projectId"),
                safe.get("miles"),
                safe.get("rate"),
                parseBoolean(safe.get("billable")),
                safe.get("notes"));
    }

    private static MileageConversion conversion(
            UUID id,
            String workspaceId,
            String expenseId,
            String userId,
            CreateMileageExpenseRequest request,
            LocalDate expenseDate,
            MileageSettingsValidation settings,
            MileageCalculation calculation,
            String marker) {
        MileageConversion conversion = new MileageConversion();
        conversion.setId(id);
        conversion.setWorkspaceId(workspaceId);
        conversion.setExpenseId(expenseId);
        conversion.setSource(MileageConversionSource.ADDON_FORM);
        conversion.setSourceCategoryId(settings.inputCategoryId());
        conversion.setTargetCategoryId(settings.outputCategoryId());
        conversion.setUserId(userId);
        conversion.setProjectId(blankToNull(request.projectId()));
        conversion.setTaskId(null);
        conversion.setExpenseDate(expenseDate);
        conversion.setMiles(calculation.miles());
        conversion.setRate(calculation.rate());
        conversion.setCalculatedAmount(calculation.calculatedAmount());
        conversion.setRoundedAmount(calculation.roundedAmount());
        conversion.setRoundingMode(calculation.roundingMode().name());
        conversion.setStatus(MileageConversionStatus.CONVERTED);
        conversion.setNoteMarker(marker);
        conversion.setConvertedAt(Instant.now());
        return conversion;
    }

    private static void validateReceipt(MultipartFile file) {
        if (file.getSize() > MAX_RECEIPT_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Receipt file exceeds 10 MB");
        }
        String contentType = safeContentType(file.getContentType());
        if (!ALLOWED_RECEIPT_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported receipt file type");
        }
    }

    private static String required(String field, String value) {
        String cleaned = blankToNull(value);
        if (cleaned == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return cleaned;
    }

    private static Boolean parseBoolean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Boolean.parseBoolean(value);
    }

    private static Boolean billableOrDefault(Boolean billable) {
        return billable == null ? Boolean.TRUE : billable;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String safeContentType(String contentType) {
        return contentType == null || contentType.isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : contentType.toLowerCase(Locale.ROOT);
    }

    private static String safeFileName(String fileName) {
        String value = blankToNull(fileName);
        return value == null ? "receipt" : value;
    }
}
