package com.frauddetect.notification.web;

import com.frauddetect.common.api.PageResponse;
import com.frauddetect.notification.dto.NotificationResponse;
import com.frauddetect.notification.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read API over the notification ledger (Section 3 + Section 11 RBAC). Staff-only: notifications
 * reference customers and alert context, so access is limited to ANALYST/INVESTIGATOR/ADMIN.
 * Optionally filter by {@code alertId} or {@code customerId}; with no filter it returns the most
 * recent notifications (newest first).
 */
@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("hasAnyRole('ANALYST','INVESTIGATOR','ADMIN')")
public class NotificationController {

    private static final int MAX_PAGE_SIZE = 200;

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<NotificationResponse> list(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String alertId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<NotificationResponse> result;
        if (alertId != null && !alertId.isBlank()) {
            result = service.listByAlert(alertId, pageable).map(NotificationResponse::from);
        } else if (customerId != null && !customerId.isBlank()) {
            result = service.listByCustomer(customerId, pageable).map(NotificationResponse::from);
        } else {
            result = service.list(pageable).map(NotificationResponse::from);
        }
        return PageResponse.of(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements());
    }
}
