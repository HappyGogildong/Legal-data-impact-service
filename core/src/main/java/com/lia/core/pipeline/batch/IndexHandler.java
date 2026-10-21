package com.lia.core.pipeline.batch;

import java.util.Optional;

import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.index.RAGIndexer;
import com.lia.core.store.LawStore;

/**
 * INDEX — 저장된 정본을 읽어 청킹·임베딩·색인 교체(재수집 없음). 그 사이 시행예정이 아니게 됐으면
 * (전환·철회) 할 일이 없다. 임베딩 API 호출 간격은 {@code embeddingLimiter}로 제한한다(D62).
 */
public class IndexHandler implements TaskHandler {

    private final LawStore lawStore;
    private final RAGIndexer indexer;
    private final MinIntervalLimiter embeddingLimiter;

    public IndexHandler(LawStore lawStore, RAGIndexer indexer, MinIntervalLimiter embeddingLimiter) {
        this.lawStore = lawStore;
        this.indexer = indexer;
        this.embeddingLimiter = embeddingLimiter;
    }

    @Override
    public TaskKind kind() {
        return TaskKind.INDEX;
    }

    @Override
    public void handle(Task task) {
        Optional<Law> law = lawStore.find(task.lawId(), task.effectiveDate());
        if (law.isEmpty() || !law.get().pending()) {
            return;
        }
        // ponytail: 법령 1건당 1회만 잰다 — 한 법령의 청크 묶음이 여러 임베딩 호출로 나뉠 수 있다.
        //           호출 단위 제한이 필요하면 EmbeddingModel 데코레이터로 옮긴다.
        embeddingLimiter.acquire();
        indexer.index(law.get());
    }
}
