package com.lia.core.pipeline.batch;

import java.util.List;

import org.springframework.transaction.support.TransactionTemplate;

import com.lia.core.domain.law.Law;
import com.lia.core.store.ChunkStore;
import com.lia.core.store.LawStore;

/**
 * PURGE — 목록에서 두 번 연속 사라진 시행예정본을 철회로, 색인 제거. 행은 남긴다(인용 추적, D62).
 *
 * <p>상태 변경과 색인 제거를 <b>한 트랜잭션</b>으로 실행한다 — 색인 제거가 실패하면 철회 처리도 롤백돼
 * 철회 행의 pending 청크가 검색에 남지 않는다. 색인 제거의 자체 트랜잭션은 같은 DataSource 라 합류한다.
 *
 * <p>가드: 대상 버전이 시행예정이 아니면(행이 없거나, 이미 시행중으로 전환됐거나 철회된 경우) 아무것도
 * 하지 않고 끝낸다 — 오래된 PURGE 가 시행중본을 철회시키지 못하게.
 */
public class PurgeHandler implements TaskHandler {

    private final LawStore lawStore;
    private final ChunkStore chunkStore;
    private final TransactionTemplate transactions;

    public PurgeHandler(LawStore lawStore, ChunkStore chunkStore, TransactionTemplate transactions) {
        this.lawStore = lawStore;
        this.chunkStore = chunkStore;
        this.transactions = transactions;
    }

    @Override
    public TaskKind kind() {
        return TaskKind.PURGE;
    }

    @Override
    public void handle(Task task) {
        transactions.executeWithoutResult(status -> {
            var law = lawStore.find(task.lawId(), task.effectiveDate());
            if (law.isEmpty() || !law.get().pending()) {
                return;
            }
            lawStore.updateStatus(task.lawId(), task.effectiveDate(), Law.Status.철회);
            chunkStore.replaceVersion(task.lawId(), task.effectiveDate().toString(), List.of());
        });
    }
}
