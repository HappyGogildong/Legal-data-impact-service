package com.lia.core.pipeline.batch;

import java.util.List;

import com.lia.core.domain.law.Law;
import com.lia.core.store.ChunkStore;
import com.lia.core.store.LawStore;
import com.lia.core.store.PendingVersion;

/**
 * TRANSITION — 시행일이 지난 시행예정본을 시행중으로, 더 오래된 시행중본은 과거로, pending 색인 제거,
 * 같은 법령의 남은 시행예정본에 SYNC(기준선이 바뀌어 diff 재생성 필요, D43·D62).
 */
public class TransitionHandler implements TaskHandler {

    private final LawStore lawStore;
    private final ChunkStore chunkStore;
    private final TaskQueue queue;

    public TransitionHandler(LawStore lawStore, ChunkStore chunkStore, TaskQueue queue) {
        this.lawStore = lawStore;
        this.chunkStore = chunkStore;
        this.queue = queue;
    }

    @Override
    public TaskKind kind() {
        return TaskKind.TRANSITION;
    }

    @Override
    public void handle(Task task) {
        lawStore.updateStatus(task.lawId(), task.effectiveDate(), Law.Status.시행중);
        lawStore.supersedeOlderInForce(task.lawId(), task.effectiveDate());
        chunkStore.replaceVersion(task.lawId(), task.effectiveDate().toString(), List.of());
        for (PendingVersion sibling : lawStore.findPendingVersions(task.lawId())) {
            queue.enqueue(TaskKind.SYNC, sibling.lawId(), sibling.effectiveDate(), sibling.mst());
        }
    }
}
