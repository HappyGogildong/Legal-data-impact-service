package com.lia.core.pipeline.batch;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.transaction.support.TransactionTemplate;

import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.connector.PendingLawFeed;
import com.lia.core.pipeline.connector.RawLaw;
import com.lia.core.pipeline.ingest.IngestService;
import com.lia.core.pipeline.ingest.IngestService.IngestResult;
import com.lia.core.store.LawStore;

/**
 * SYNC — 본문·기준선 fetch(트랜잭션 밖) → <b>한 트랜잭션</b>: 정본 조립(revision·상태 같으면 저장 생략) ·
 * 더 오래된 시행중본은 과거로 · 사라짐 해제 · 시행예정본이 바뀌었으면 INDEX 등록(D62).
 * 저장과 등록을 묶는 이유: 저장만 되고 등록이 빠지면 재시도 때 revision 이 같아 영영 색인되지 않는다.
 *
 * <p>가드: 시행일이 오늘 이전이거나 저장된 행이 {@code 시행중}·{@code 과거}면 아무것도 하지 않는다(fetch 도 없음) —
 * 그 버전은 TRANSITION 이 맡는다. 실패로 남은 오래된 SYNC·{@code requeue-dead}·수동 {@code sync}가 전환된 버전을
 * 시행예정으로 되돌리고 다시 색인하지 않게({@code LawStore.upsert}는 상태를 덮어쓴다).
 */
public class SyncHandler implements TaskHandler {

    private final PendingLawFeed feed;
    private final IngestService ingest;
    private final LawStore lawStore;
    private final TaskQueue queue;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /**
     * @param transactions LawStore·TaskQueue 와 같은 DataSource 의 트랜잭션
     * @param clock        "오늘"(KST) — 시행일이 지난 버전은 건드리지 않는다
     */
    public SyncHandler(PendingLawFeed feed, IngestService ingest, LawStore lawStore, TaskQueue queue,
                       TransactionTemplate transactions, Clock clock) {
        this.feed = feed;
        this.ingest = ingest;
        this.lawStore = lawStore;
        this.queue = queue;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public TaskKind kind() {
        return TaskKind.SYNC;
    }

    @Override
    public void handle(Task task) {
        if (alreadyInForce(task)) {
            return;
        }
        RawLaw pending = feed.fetchPending(task.mst(), task.effectiveDate());   // 외부 API — 트랜잭션 밖
        RawLaw baseline = feed.fetchCurrent(task.lawId());                      // null = 제정
        transactions.executeWithoutResult(status -> {
            IngestResult result = ingest.store(pending, baseline);
            if (result.baselineEffectiveDate() != null) {
                lawStore.supersedeOlderInForce(result.lawId(), result.baselineEffectiveDate());
            }
            lawStore.clearMissing(result.lawId(), result.effectiveDate());
            if (result.pendingChanged()) {
                queue.enqueue(TaskKind.INDEX, result.lawId(), result.effectiveDate(), null);
            }
        });
    }

    /** 시행일이 지났거나(오늘 포함) 이미 시행중·과거인 버전 — SYNC 대상이 아니다. */
    private boolean alreadyInForce(Task task) {
        if (!task.effectiveDate().isAfter(LocalDate.now(clock))) {
            return true;
        }
        return lawStore.find(task.lawId(), task.effectiveDate())
                .map(law -> law.status() == Law.Status.시행중 || law.status() == Law.Status.과거)
                .orElse(false);
    }
}
