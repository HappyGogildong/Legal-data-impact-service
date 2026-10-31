package com.lia.core.application.notification;

import java.util.List;
import java.util.UUID;

import com.lia.core.store.NotificationStore;
import com.lia.core.store.NotificationStore.NotificationRow;

/** 인앱 알림함 유스케이스(D74) — 최근 {@value #INBOX_SIZE}개 + 읽지 않은 수, 읽음 처리. */
public class NotificationUseCase {

    public static final int INBOX_SIZE = 50;

    public record Inbox(List<NotificationRow> items, int unreadCount) {}

    private final NotificationStore store;

    public NotificationUseCase(NotificationStore store) {
        this.store = store;
    }

    public Inbox inbox(UUID userId) {
        return new Inbox(store.list(userId, INBOX_SIZE), store.unreadCount(userId));
    }

    /** 멱등 — 남의 알림은 무시. */
    public void markRead(UUID userId, long id) {
        store.markRead(userId, id);
    }

    public void markAllRead(UUID userId) {
        store.markAllRead(userId);
    }
}
