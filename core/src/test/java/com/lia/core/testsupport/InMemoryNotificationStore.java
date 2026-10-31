package com.lia.core.testsupport;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.lia.core.store.NotificationStore;

/** 테스트용 인메모리 알림 저장소 — {@link #add} 로 알림을 넣는다. 단일 스레드 전용. */
public class InMemoryNotificationStore extends NotificationStore {

    private record Stored(UUID userId, NotificationRow row) {}

    private final List<Stored> rows = new ArrayList<>();
    private long nextId = 1;

    public InMemoryNotificationStore() {
        super(null);
    }

    /** 알림 하나를 넣고 id 를 돌려준다(최근에 넣은 것이 목록 앞). */
    public long add(UUID userId, String lawId, String title, LocalDate effectiveDate, int notifyBefore) {
        long id = nextId++;
        rows.add(new Stored(userId, new NotificationRow(id, lawId, title, effectiveDate, notifyBefore,
                OffsetDateTime.of(2026, 10, 31, 0, 0, 0, 0, ZoneOffset.UTC).plusSeconds(id), false)));
        return id;
    }

    @Override
    public int createDue(LocalDate today) {
        throw new UnsupportedOperationException("웹 슬라이스에서는 배치 생성을 쓰지 않는다");
    }

    @Override
    public int countDue(LocalDate today) {
        throw new UnsupportedOperationException("웹 슬라이스에서는 배치 생성을 쓰지 않는다");
    }

    @Override
    public List<NotificationRow> list(UUID userId, int limit) {
        List<NotificationRow> mine = new ArrayList<>();
        for (int i = rows.size() - 1; i >= 0 && mine.size() < limit; i--) {
            if (rows.get(i).userId().equals(userId)) mine.add(rows.get(i).row());
        }
        return mine;
    }

    @Override
    public int unreadCount(UUID userId) {
        return (int) rows.stream().filter(s -> s.userId().equals(userId) && !s.row().read()).count();
    }

    @Override
    public void markRead(UUID userId, long id) {
        rows.replaceAll(s -> s.userId().equals(userId) && s.row().id() == id ? read(s) : s);
    }

    @Override
    public void markAllRead(UUID userId) {
        rows.replaceAll(s -> s.userId().equals(userId) ? read(s) : s);
    }

    private static Stored read(Stored s) {
        NotificationRow r = s.row();
        return new Stored(s.userId(), new NotificationRow(r.id(), r.lawId(), r.title(), r.effectiveDate(),
                r.notifyBefore(), r.createdAt(), true));
    }
}
