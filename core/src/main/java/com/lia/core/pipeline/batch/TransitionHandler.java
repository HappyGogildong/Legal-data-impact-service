package com.lia.core.pipeline.batch;

import java.util.List;

import org.springframework.transaction.support.TransactionTemplate;

import com.lia.core.domain.law.Law;
import com.lia.core.store.ChunkStore;
import com.lia.core.store.LawStore;
import com.lia.core.store.PendingVersion;

/**
 * TRANSITION — 시행일이 지난 시행예정본을 시행중으로, 더 오래된 시행중본은 과거로, pending 색인 제거,
 * 같은 법령의 남은 시행예정본에 SYNC(기준선이 바뀌어 diff 재생성 필요, D43·D62).
 *
 * <p>전체를 <b>한 트랜잭션</b>으로 실행한다 — 상태 변경·과거 처리·색인 제거·SYNC 등록이 함께 커밋되거나
 * 함께 롤백된다(색인 제거가 실패하면 상태도 되돌아가 재시도 때 다시 시도). 색인 제거의 자체 트랜잭션은
 * 같은 DataSource 라 이 트랜잭션에 합류한다.
 *
 * <p>가드: 대상 버전이 시행예정이 아니면(행이 없거나, 이미 시행중이거나 철회된 경우) 아무것도 하지 않고
 * 끝낸다 — 오래된 작업이 철회 행을 되살리지 못하게. 본문이 원자적이라 실패 후 재시도에서도 항상
 * 시행예정으로 보이므로 가드가 절반만 끝난 전환을 건너뛰는 일은 없다.
 */
public class TransitionHandler implements TaskHandler {

    private final LawStore lawStore;
    private final ChunkStore chunkStore;
    private final TaskQueue queue;
    private final TransactionTemplate transactions;

    public TransitionHandler(LawStore lawStore, ChunkStore chunkStore, TaskQueue queue,
                             TransactionTemplate transactions) {
        this.lawStore = lawStore;
        this.chunkStore = chunkStore;
        this.queue = queue;
        this.transactions = transactions;
    }

    @Override
    public TaskKind kind() {
        return TaskKind.TRANSITION;
    }

    @Override
    public void handle(Task task) {
        transactions.executeWithoutResult(status -> {
            var law = lawStore.find(task.lawId(), task.effectiveDate());
            if (law.isEmpty() || !law.get().pending()) {
                return;
            }
            lawStore.updateStatus(task.lawId(), task.effectiveDate(), Law.Status.시행중);
            lawStore.supersedeOlderInForce(task.lawId(), task.effectiveDate());
            chunkStore.replaceVersion(task.lawId(), task.effectiveDate().toString(), List.of());
            for (PendingVersion sibling : lawStore.findPendingVersions(task.lawId())) {
                queue.enqueue(TaskKind.SYNC, sibling.lawId(), sibling.effectiveDate(), sibling.mst());
            }
        });
    }
}
