package com.lia.core.pipeline.batch;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lia.core.pipeline.connector.PendingLawFeed;
import com.lia.core.pipeline.connector.PendingLawFeed.PendingList;
import com.lia.core.pipeline.connector.RawLaw;
import com.lia.core.store.LawStore;
import com.lia.core.store.PendingVersion;

/**
 * 탐색 — 시행예정 목록 ⟷ 저장 상태 비교 → 작업 등록(D62, IngestJob.md §탐색 분류 규칙).
 *
 * <ul>
 *   <li>목록에 있고 저장 안 됨 / 저장된 MST 와 다름(또는 {@code full}) → SYNC</li>
 *   <li>저장된 시행예정본의 시행일 ≤ 오늘 → TRANSITION(날짜 기준 — 목록 실패여도)</li>
 *   <li>목록 완주 실행에서 안 보임 → 첫 번째는 missing_since 기록, <b>다른 날</b>의 두 번째는 PURGE
 *       (같은 날 재실행은 같은 API 상태라 새 증거가 아니다). 조회 범위 밖 버전은 판정하지 않는다</li>
 *   <li>lawId·시행일·MST 중 하나라도 없는 행은 건너뛰고 세며, 그런 행이 있으면 목록을 미완주로 본다
 *       (그 행이 어느 법령인지 몰라 "안 보임"을 판정할 수 없다). 행 하나로 탐색 전체가 실패하지 않게</li>
 * </ul>
 */
public class Discoverer {

    private static final Logger log = LoggerFactory.getLogger(Discoverer.class);

    private final PendingLawFeed feed;
    private final LawStore lawStore;
    private final TaskQueue queue;
    private final Clock clock;
    private final int lookaheadYears;

    /**
     * @param lookaheadYears 목록 조회 범위(오늘+1일 ~ 오늘+N년). 저장된 시행예정본을 모두 덮을 만큼 넉넉해야
     *                       범위 밖 법령을 "사라짐"으로 오판하지 않는다
     */
    public Discoverer(PendingLawFeed feed, LawStore lawStore, TaskQueue queue, Clock clock, int lookaheadYears) {
        this.feed = feed;
        this.lawStore = lawStore;
        this.queue = queue;
        this.clock = clock;
        this.lookaheadYears = lookaheadYears;
    }

    public DiscoveryResult discover(boolean full, boolean dryRun) {
        LocalDate today = LocalDate.now(clock);
        LocalDate horizon = today.plusYears(lookaheadYears);
        Map<String, PendingVersion> stored = new LinkedHashMap<>();
        for (PendingVersion version : lawStore.listPendingVersions()) {
            stored.put(key(version.lawId(), version.effectiveDate()), version);
        }

        PendingList list = fetchList(today, horizon);
        List<PlannedTask> plan = new ArrayList<>();
        Set<String> listed = new HashSet<>();
        int duplicateKeys = 0;
        int malformedRows = 0;
        if (list != null) {
            for (RawLaw head : list.heads()) {
                if (malformed(head)) {
                    malformedRows++;                         // 등록하면 NOT NULL·mst CHECK 위반 — 탐색 전체가 실패한다
                    continue;
                }
                String key = key(head.lawId(), head.effectiveDate());
                if (!listed.add(key)) {
                    duplicateKeys++;                         // 식별 키 가정(D43) 위반 — 첫 행만 쓴다
                    continue;
                }
                PendingVersion saved = stored.get(key);      // 시행예정만 — 철회된 행은 신규로 보여 SYNC 된다
                if (full || saved == null || !Objects.equals(saved.mst(), head.mst())) {
                    plan.add(new PlannedTask(TaskKind.SYNC, head.lawId(), head.effectiveDate(), head.mst()));
                } else if (saved.missingSince() != null && !dryRun) {
                    lawStore.clearMissing(saved.lawId(), saved.effectiveDate());   // 보였다 = 확실한 증거(미완주여도)
                }
            }
            if (duplicateKeys > 0) {
                log.warn("[batch] 같은 (lawId, 시행일)이 목록에 중복 {}건 — 식별 키 가정 확인 필요", duplicateKeys);
            }
            if (malformedRows > 0) {
                log.warn("[batch] lawId·시행일·MST 가 빠진 목록 행 {}건 건너뜀 — 목록 미완주로 취급(사라짐 판정 안 함)",
                        malformedRows);
            }
        }

        boolean complete = list != null && list.complete() && malformedRows == 0;
        int markedMissing = 0;
        for (PendingVersion saved : stored.values()) {
            LocalDate effectiveDate = saved.effectiveDate();
            if (!effectiveDate.isAfter(today)) {
                plan.add(new PlannedTask(TaskKind.TRANSITION, saved.lawId(), effectiveDate, null));
                continue;
            }
            boolean judgeable = complete && !effectiveDate.isAfter(horizon);     // 끝까지 받은 목록 + 조회 범위 안
            if (!judgeable || listed.contains(key(saved.lawId(), effectiveDate))) {
                continue;
            }
            if (saved.missingSince() == null) {
                if (!dryRun) lawStore.markMissing(saved.lawId(), effectiveDate, today);
                markedMissing++;
            } else if (saved.missingSince().isBefore(today)) {
                plan.add(new PlannedTask(TaskKind.PURGE, saved.lawId(), effectiveDate, null));
            }
            // missingSince == today: 같은 날 다시 돈 실행 — 새 증거가 아니므로 아무것도 안 한다
        }

        Map<TaskKind, Integer> planned = new EnumMap<>(TaskKind.class);
        Map<TaskKind, Integer> enqueued = new EnumMap<>(TaskKind.class);
        for (PlannedTask task : plan) {
            planned.merge(task.kind(), 1, Integer::sum);
            if (!dryRun && queue.enqueue(task.kind(), task.lawId(), task.effectiveDate(), task.mst())) {
                enqueued.merge(task.kind(), 1, Integer::sum);
            }
        }
        return new DiscoveryResult(list == null ? 0 : list.heads().size(), complete, planned, enqueued, markedMissing,
                duplicateKeys, malformedRows);
    }

    private static boolean malformed(RawLaw head) {
        return head.lawId() == null || head.lawId().isBlank() || head.effectiveDate() == null
                || head.mst() == null || head.mst().isBlank();
    }

    /** 목록 전량. 실패하면 null — 날짜 기준 TRANSITION 만 진행한다. */
    private PendingList fetchList(LocalDate today, LocalDate horizon) {
        try {
            return feed.listAllPending(today.plusDays(1), horizon);
        } catch (RuntimeException e) {
            log.warn("[batch] 시행예정 목록 조회 실패 — 시행 전환만 진행: {}", e.toString());
            return null;
        }
    }

    private static String key(String lawId, LocalDate effectiveDate) {
        return lawId + "@" + effectiveDate;
    }

    private record PlannedTask(TaskKind kind, String lawId, LocalDate effectiveDate, String mst) {}
}
