package com.lia.core.pipeline.batch;

import org.springframework.transaction.support.TransactionTemplate;

import com.lia.core.pipeline.connector.PendingLawFeed;
import com.lia.core.pipeline.connector.RawLaw;
import com.lia.core.pipeline.ingest.IngestService;
import com.lia.core.pipeline.ingest.IngestService.IngestResult;
import com.lia.core.store.LawStore;

/**
 * SYNC — 본문·기준선 fetch(트랜잭션 밖) → <b>한 트랜잭션</b>: 정본 조립(revision·상태 같으면 저장 생략) ·
 * 더 오래된 시행중본은 과거로 · 사라짐 해제 · 시행예정본이 바뀌었으면 INDEX 등록(D62).
 * 저장과 등록을 묶는 이유: 저장만 되고 등록이 빠지면 재시도 때 revision 이 같아 영영 색인되지 않는다.
 */
public class SyncHandler implements TaskHandler {

    private final PendingLawFeed feed;
    private final IngestService ingest;
    private final LawStore lawStore;
    private final TaskQueue queue;
    private final TransactionTemplate transactions;

    /** @param transactions LawStore·TaskQueue 와 같은 DataSource 의 트랜잭션 */
    public SyncHandler(PendingLawFeed feed, IngestService ingest, LawStore lawStore, TaskQueue queue,
                       TransactionTemplate transactions) {
        this.feed = feed;
        this.ingest = ingest;
        this.lawStore = lawStore;
        this.queue = queue;
        this.transactions = transactions;
    }

    @Override
    public TaskKind kind() {
        return TaskKind.SYNC;
    }

    @Override
    public void handle(Task task) {
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
}
