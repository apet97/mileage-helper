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
import com.cake.clockify.addon.mileage.audit.MileageConversionReservationRepository;
import com.cake.clockify.addon.mileage.audit.MileageConversionSource;
import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import com.cake.clockify.addon.mileage.calculation.MileageCalculation;
import com.cake.clockify.addon.mileage.calculation.MileageCalculator;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseGateway;
import com.cake.clockify.addon.mileage.clockify.CreateFlatExpenseCommand;
import com.cake.clockify.addon.mileage.clockify.UpdateFlatExpenseCommand;
import com.cake.clockify.addon.mileage.note.MileageNoteService;
import com.cake.clockify.addon.mileage.policy.MileageRatePolicyService;
import com.cake.clockify.addon.mileage.policy.MileageRateResolution;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
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
            "date", "projectId", "miles", "rate", "billable", "notes",
            "tripOrigin", "tripDestination", "tripPurpose",
            "odometerStart", "odometerEnd", "policyExceptionReason");

    private final MileageSettingsService settingsService;
    private final MileageRatePolicyService ratePolicyService;
    private final MileageDateRangeResolver dateRangeResolver;
    private final MileageCalculator calculator;
    private final ClockifyExpenseGateway gateway;
    private final MileageConversionRepository conversionRepository;
    private final MileageConversionReservationRepository reservationRepository;
    private final MileageNoteService noteService;

    public MileageApiController(
            MileageSettingsService settingsService,
            MileageRatePolicyService ratePolicyService,
            MileageDateRangeResolver dateRangeResolver,
            MileageCalculator calculator,
            ClockifyExpenseGateway gateway,
            MileageConversionRepository conversionRepository,
            MileageConversionReservationRepository reservationRepository,
            MileageNoteService noteService) {
        this.settingsService = settingsService;
        this.ratePolicyService = ratePolicyService;
        this.dateRangeResolver = dateRangeResolver;
        this.calculator = calculator;
        this.gateway = gateway;
        this.conversionRepository = conversionRepository;
        this.reservationRepository = reservationRepository;
        this.noteService = noteService;
    }

    @PostMapping(value = "/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MileagePreviewResponse> preview(
            HttpServletRequest request,
            @Valid @RequestBody MileagePreviewRequest body) {
        NormalizedClaims claims = RequestAttributes.requireClaims(request);
        LocalDate expenseDate = parseMileageDate(body.date());
        MileageSettingsValidation settings = requireAddonSettings(claims.workspaceId());
        RateChoice rateChoice = calculation(claims.workspaceId(), expenseDate, body.miles(), body.rate(), settings);
        MileageCalculation calculation = rateChoice.calculation();
        MileageRateResolution resolution = rateChoice.resolution();
        return ResponseEntity.ok(new MileagePreviewResponse(
                calculation.milesText(),
                calculation.rateText(),
                calculation.calculatedAmountText(),
                calculation.roundedAmountText(),
                resolution.source(),
                resolution.policyId(),
                resolution.policyName(),
                resolution.warnings()));
    }

    @GetMapping("/create-context")
    public ResponseEntity<MileageCreateContextResponse> createContext(HttpServletRequest request) {
        NormalizedClaims claims = RequestAttributes.requireClaims(request);
        MileageSettingsValidation settings = settingsService.validateForAddonCreate(claims.workspaceId());
        MileageRateResolution resolution = settings.complete()
                ? ratePolicyService.resolveRate(claims.workspaceId(), dateRangeResolver.today(claims), settings)
                : null;
        return ResponseEntity.ok(MileageCreateContextResponse.from(settings, resolution));
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
        UUID conversionId = UUID.randomUUID();
        LocalDate expenseDate = parseExpenseDate(request.date());
        TripEvidence tripEvidence = validateTripEvidence(request);
        RateChoice rateChoice = calculation(workspaceId, expenseDate, request.miles(), request.rate(), settings);
        MileageCalculation calculation = rateChoice.calculation();
        MileageRateResolution resolution = rateChoice.resolution();
        String note = noteService.buildConvertedNote(
                request.notes(),
                calculation,
                settings.unit(),
                conversionId,
                settings.noteTemplate(),
                null);
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
        UUID persistedId = reservationRepository.reserve(
                conversionId,
                workspaceId,
                expenseId,
                MileageConversionSource.ADDON_FORM,
                "ADDON_FORM");
        MileageConversion conversion = conversionRepository.findByIdAndWorkspaceId(persistedId, workspaceId)
                .orElseThrow(() -> new IllegalStateException("Reserved mileage conversion was not found"));
        String persistedMarker = noteService.marker(persistedId);
        // Re-mark the note only if the reservation returned a different conversion id than the create note used
        // (the webhook-reserved-first race). We intentionally do NOT stamp the Clockify category charge here:
        // reconciling the note requires a second synchronous write to the just-created expense, which races
        // Clockify's own EXPENSE_CREATED webhook and proved unreliable in production (live QA 2026-06-05). The
        // settings-save category-price sync keeps the divergence to integer-cent rounding; native conversions
        // (a single worker-thread update, post-webhook) still carry the (Clockify category charge: X) note.
        String persistedNote = note;
        if (!persistedId.equals(conversionId)) {
            persistedNote = noteService.buildConvertedNote(
                    request.notes(),
                    calculation,
                    settings.unit(),
                    persistedId,
                    settings.noteTemplate(),
                    null);
        }
        applyAddonFormConversion(
                conversion,
                workspaceId,
                expenseId,
                userId,
                request,
                expenseDate,
                settings,
                calculation,
                resolution,
                tripEvidence,
                persistedMarker);
        conversionRepository.saveAndFlush(conversion);
        if (!Objects.equals(persistedNote, note)) {
            gateway.updateFlatExpense(workspaceId, expenseId, new UpdateFlatExpenseCommand(
                    settings.outputCategoryId(),
                    userId,
                    expenseDate.toString(),
                    blankToNull(request.projectId()),
                    null,
                    billableOrDefault(request.billable()),
                    clockifyExpenseAmount(settings, calculation),
                    persistedNote,
                    settings.roundingMode(),
                    singleMileageCategory(settings)));
        }
        return new MileageCreateExpenseResponse(
                expenseId,
                conversion.getId(),
                calculation.milesText(),
                calculation.rateText(),
                calculation.calculatedAmountText(),
                calculation.roundedAmountText(),
                resolution.source(),
                resolution.policyId(),
                resolution.policyName());
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

    private static LocalDate parseMileageDate(String raw) {
        String cleaned = blankToNull(raw);
        if (cleaned == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date must use YYYY-MM-DD");
        }
        try {
            return LocalDate.parse(cleaned);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date must use YYYY-MM-DD", e);
        }
    }

    private RateChoice calculation(
            String workspaceId,
            LocalDate expenseDate,
            String miles,
            String requestedRate,
            MileageSettingsValidation settings) {
        if (settings.allowUserRateOverride() && requestedRate != null && !requestedRate.isBlank()) {
            MileageCalculation calculation = calculator.calculate(miles, requestedRate, settings.roundingMode());
            return new RateChoice(MileageRateResolution.userOverride(calculation.rate()), calculation);
        }
        MileageRateResolution resolution = ratePolicyService.resolveRate(workspaceId, expenseDate, settings);
        if (resolution.rate() == null) {
            List<String> warnings = resolution.warnings() == null ? List.of("rate is required") : resolution.warnings();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Mileage settings are incomplete: " + String.join(", ", warnings));
        }
        return new RateChoice(
                resolution,
                calculator.calculate(miles, resolution.rate().toPlainString(), settings.roundingMode()));
    }

    private static BigDecimal clockifyExpenseAmount(
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
                safe.get("notes"),
                safe.get("tripOrigin"),
                safe.get("tripDestination"),
                safe.get("tripPurpose"),
                safe.get("odometerStart"),
                safe.get("odometerEnd"),
                safe.get("policyExceptionReason"));
    }

    private static void applyAddonFormConversion(
            MileageConversion conversion,
            String workspaceId,
            String expenseId,
            String userId,
            CreateMileageExpenseRequest request,
            LocalDate expenseDate,
            MileageSettingsValidation settings,
            MileageCalculation calculation,
            MileageRateResolution resolution,
            TripEvidence tripEvidence,
            String marker) {
        conversion.setWorkspaceId(workspaceId);
        conversion.setExpenseId(expenseId);
        conversion.setSource(MileageConversionSource.ADDON_FORM);
        conversion.setSourceEventType("ADDON_FORM");
        conversion.setSourceCategoryId(settings.inputCategoryId());
        conversion.setTargetCategoryId(settings.outputCategoryId());
        conversion.setUserId(userId);
        conversion.setProjectId(blankToNull(request.projectId()));
        conversion.setTaskId(null);
        conversion.setExpenseDate(expenseDate);
        conversion.setMiles(calculation.miles());
        conversion.setRate(calculation.rate());
        conversion.setRateSource(resolution.source());
        conversion.setRatePolicyId(resolution.policyId());
        conversion.setRatePolicyName(resolution.policyName());
        conversion.setCalculatedAmount(calculation.calculatedAmount());
        conversion.setRoundedAmount(calculation.roundedAmount());
        conversion.setRoundingMode(calculation.roundingMode().name());
        conversion.setTripOrigin(tripEvidence.tripOrigin());
        conversion.setTripDestination(tripEvidence.tripDestination());
        conversion.setTripPurpose(tripEvidence.tripPurpose());
        conversion.setOdometerStart(tripEvidence.odometerStart());
        conversion.setOdometerEnd(tripEvidence.odometerEnd());
        conversion.setPolicyExceptionReason(tripEvidence.policyExceptionReason());
        conversion.setStatus(MileageConversionStatus.CONVERTED);
        conversion.setNoteMarker(marker);
        conversion.setConvertedAt(Instant.now());
    }

    private static TripEvidence validateTripEvidence(CreateMileageExpenseRequest request) {
        BigDecimal odometerStart = parseOptionalOdometer("odometerStart", request.odometerStart());
        BigDecimal odometerEnd = parseOptionalOdometer("odometerEnd", request.odometerEnd());
        if (odometerStart != null && odometerEnd != null && odometerEnd.compareTo(odometerStart) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "odometerEnd must be greater than or equal to odometerStart");
        }
        return new TripEvidence(
                trimEvidence("tripOrigin", request.tripOrigin()),
                trimEvidence("tripDestination", request.tripDestination()),
                trimEvidence("tripPurpose", request.tripPurpose()),
                odometerStart,
                odometerEnd,
                trimEvidence("policyExceptionReason", request.policyExceptionReason()));
    }

    private static String trimEvidence(String field, String value) {
        String cleaned = blankToNull(value);
        if (cleaned == null) {
            return null;
        }
        if (cleaned.length() > 256) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be 256 characters or fewer");
        }
        return cleaned;
    }

    private static BigDecimal parseOptionalOdometer(String field, String raw) {
        String cleaned = blankToNull(raw);
        if (cleaned == null) {
            return null;
        }
        if (cleaned.length() > com.cake.clockify.addon.mileage.calculation.MileageDecimalPolicy.MAX_DECIMAL_TEXT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be 32 characters or fewer");
        }
        if (cleaned.contains("e") || cleaned.contains("E")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be a plain decimal number");
        }
        try {
            BigDecimal value = new BigDecimal(cleaned);
            if (value.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be greater than zero");
            }
            int scale = Math.max(value.stripTrailingZeros().scale(), 0);
            if (scale > 6) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " supports at most 6 decimal places");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be a decimal number", e);
        }
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
        String cleaned = blankToNull(value);
        if (cleaned == null) {
            return null;
        }
        if ("true".equalsIgnoreCase(cleaned)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(cleaned)) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("billable must be true or false");
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

    private record RateChoice(
            MileageRateResolution resolution,
            MileageCalculation calculation
    ) {
    }

    private record TripEvidence(
            String tripOrigin,
            String tripDestination,
            String tripPurpose,
            BigDecimal odometerStart,
            BigDecimal odometerEnd,
            String policyExceptionReason
    ) {
    }
}
