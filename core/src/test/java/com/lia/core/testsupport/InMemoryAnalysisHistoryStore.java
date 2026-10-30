package com.lia.core.testsupport;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.lia.core.store.AnalysisHistoryStore;

/**
 * 테스트용 인메모리 이력 저장소 — JdbcClient 없이 공개 메서드를 대체. 단일 스레드 테스트 전용.
 * {@code knownVersions} = "lawId@yyyy-MM-dd" — 저장 가능한 법령 버전. lawUpdated 는 늘 false, 최신 시행예정본은 저장한 버전.
 */
public class InMemoryAnalysisHistoryStore extends AnalysisHistoryStore {

    private record Stored(UUID userId, String analysisId, HistoryRow row, String snapshotJson) {}

    private final InMemoryAccountStore accounts;
    private final Set<String> knownVersions;
    private final Map<Long, Stored> byId = new LinkedHashMap<>();
    private long nextId = 1;

    public InMemoryAnalysisHistoryStore(InMemoryAccountStore accounts, int maxPerUser, Set<String> knownVersions) {
        super(null, null, maxPerUser);
        this.accounts = accounts;
        this.knownVersions = knownVersions;
    }

    @Override
    public SaveResult save(UUID userId, NewEntry entry) {
        if (accounts.find(userId).isEmpty()) return new SaveResult(SaveStatus.ACCOUNT_NOT_FOUND, null);
        for (Stored stored : byId.values()) {
            if (stored.userId().equals(userId) && stored.analysisId().equals(entry.analysisId())) {
                return new SaveResult(SaveStatus.ALREADY, stored.row().id());
            }
        }
        if (!knownVersions.contains(entry.lawId() + "@" + entry.effectiveDate())) return new SaveResult(SaveStatus.LAW_NOT_FOUND, null);
        long mine = byId.values().stream().filter(stored -> stored.userId().equals(userId)).count();
        if (mine >= maxPerUser()) return new SaveResult(SaveStatus.LIMIT, null);
        long id = nextId++;
        HistoryRow row = new HistoryRow(id, entry.query(), entry.lawId(), entry.lawTitle(), entry.effectiveDate(),
                OffsetDateTime.of(2026, 10, 30, 0, 0, 0, 0, ZoneOffset.UTC).plusSeconds(id), false);
        byId.put(id, new Stored(userId, entry.analysisId(), row, entry.snapshotJson()));
        return new SaveResult(SaveStatus.SAVED, id);
    }

    @Override
    public List<HistoryRow> list(UUID userId) {
        List<HistoryRow> rows = new ArrayList<>();
        byId.values().stream().filter(stored -> stored.userId().equals(userId)).forEach(stored -> rows.addFirst(stored.row()));
        return rows;
    }

    @Override
    public Optional<HistoryEntry> find(UUID userId, long id) {
        Stored stored = byId.get(id);
        if (stored == null || !stored.userId().equals(userId)) return Optional.empty();
        LocalDate latest = stored.row().effectiveDate();
        return Optional.of(new HistoryEntry(stored.row(), stored.snapshotJson(), latest));
    }

    @Override
    public void delete(UUID userId, long id) {
        Stored stored = byId.get(id);
        if (stored != null && stored.userId().equals(userId)) byId.remove(id);
    }
}
