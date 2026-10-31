package com.lia.core.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.lia.core.application.notification.NotificationUseCase;
import com.lia.core.auth.CurrentUser;
import com.lia.core.web.NotificationApiDtos.NotificationItem;
import com.lia.core.web.NotificationApiDtos.NotificationListResponse;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;

/** {@code /api/v1/notifications} — 인앱 알림함(D74, [[service-api-spec]] §3.6). */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationUseCase notifications;

    public NotificationController(NotificationUseCase notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    @Operation(operationId = "listNotifications")
    public NotificationListResponse list(HttpServletRequest request) {
        NotificationUseCase.Inbox inbox = notifications.inbox(CurrentUser.require(request));
        return new NotificationListResponse(inbox.items().stream().map(NotificationItem::from).toList(),
                inbox.unreadCount());
    }

    /** 멱등 — 남의 알림은 무시. */
    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "readNotification")
    public ResponseEntity<Void> read(@PathVariable long id, HttpServletRequest request) {
        notifications.markRead(CurrentUser.require(request), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "readAllNotifications")
    public ResponseEntity<Void> readAll(HttpServletRequest request) {
        notifications.markAllRead(CurrentUser.require(request));
        return ResponseEntity.noContent().build();
    }
}
