package com.lia.core.web;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.lia.core.store.NotificationStore.NotificationRow;

/** {@code /api/v1/notifications} 응답(D74). */
final class NotificationApiDtos {

    private NotificationApiDtos() {}

    public record NotificationItem(long id, String lawId, String title, LocalDate effectiveDate, int notifyBefore,
                                   OffsetDateTime createdAt, boolean read) {

        static NotificationItem from(NotificationRow row) {
            return new NotificationItem(row.id(), row.lawId(), row.title(), row.effectiveDate(), row.notifyBefore(),
                    row.createdAt(), row.read());
        }
    }

    public record NotificationListResponse(List<NotificationItem> items, int unreadCount) {}
}
