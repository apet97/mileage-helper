package com.cake.clockify.addon.mileage.packet;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.mileage.api.MileageDateRange;
import com.cake.clockify.addon.mileage.api.MileageDateRangeResolver;
import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import com.cake.clockify.addon.mileage.security.MileageAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class MileageReimbursementPacketController {
    private static final MediaType CSV_MEDIA_TYPE = new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8);

    private final MileageAuthorizationService authorizationService;
    private final MileageDateRangeResolver dateRangeResolver;
    private final MileageReimbursementPacketService packetService;

    public MileageReimbursementPacketController(
            MileageAuthorizationService authorizationService,
            MileageDateRangeResolver dateRangeResolver,
            MileageReimbursementPacketService packetService) {
        this.authorizationService = authorizationService;
        this.dateRangeResolver = dateRangeResolver;
        this.packetService = packetService;
    }

    @GetMapping(value = "/iframe/reimbursement-packet", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> packetHtml(
            HttpServletRequest request,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) MileageConversionStatus status,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "false") boolean exceptionsOnly) {
        MileageReimbursementPacket packet = packet(
                request, scope, userId, from, to, projectId, status, includeDeleted, exceptionsOnly);
        return ResponseEntity.ok(MileageReimbursementPacketRenderer.render(packet));
    }

    @GetMapping(value = "/api/mileage/reimbursement-packet.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> packetCsv(
            HttpServletRequest request,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) MileageConversionStatus status,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "false") boolean exceptionsOnly) {
        MileageReimbursementPacket packet = packet(
                request, scope, userId, from, to, projectId, status, includeDeleted, exceptionsOnly);
        return ResponseEntity.ok()
                .contentType(CSV_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("mileage-reimbursement-packet.csv").build().toString())
                .body(packetService.csv(packet));
    }

    private MileageReimbursementPacket packet(
            HttpServletRequest request,
            String scope,
            String userId,
            String from,
            String to,
            String projectId,
            MileageConversionStatus status,
            boolean includeDeleted,
            boolean exceptionsOnly) {
        NormalizedClaims claims = RequestAttributes.requireClaims(request);
        String requesterId = claims.userId();
        if (requesterId == null || requesterId.isBlank()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "User claim is required");
        }
        boolean admin = authorizationService.isAdmin(claims);
        boolean mineScope = "mine".equalsIgnoreCase(scope);
        String targetUserId = (!admin || mineScope)
                ? requesterId
                : (hasText(userId) ? userId.trim() : null);
        MileageDateRange range = dateRangeResolver.required(from, to);
        return packetService.packet(
                claims.workspaceId(),
                targetUserId,
                range,
                hasText(projectId) ? projectId.trim() : null,
                status,
                includeDeleted,
                exceptionsOnly);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
