package com.lia.core.pipeline.batch;

import java.util.List;

import com.lia.core.domain.law.Law;
import com.lia.core.store.ChunkStore;
import com.lia.core.store.LawStore;

/** PURGE — 목록에서 두 번 연속 사라진 시행예정본을 철회로, 색인 제거. 행은 남긴다(인용 추적, D62). */
public class PurgeHandler implements TaskHandler {

    private final LawStore lawStore;
    private final ChunkStore chunkStore;

    public PurgeHandler(LawStore lawStore, ChunkStore chunkStore) {
        this.lawStore = lawStore;
        this.chunkStore = chunkStore;
    }

    @Override
    public TaskKind kind() {
        return TaskKind.PURGE;
    }

    @Override
    public void handle(Task task) {
        lawStore.updateStatus(task.lawId(), task.effectiveDate(), Law.Status.철회);
        chunkStore.replaceVersion(task.lawId(), task.effectiveDate().toString(), List.of());
    }
}
