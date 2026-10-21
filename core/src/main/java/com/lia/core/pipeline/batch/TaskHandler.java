package com.lia.core.pipeline.batch;

/** 작업 종류 하나를 처리한다. 실패는 예외로 — 분류·재시도·격리는 IngestJob 이 한다. */
public interface TaskHandler {

    TaskKind kind();

    void handle(Task task);
}
