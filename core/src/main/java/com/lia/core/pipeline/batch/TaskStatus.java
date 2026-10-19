package com.lia.core.pipeline.batch;

/** 작업 상태 — READY(대기)·RUNNING(처리 중)·DONE(완료)·DEAD(격리, requeue-dead 로만 해제). */
public enum TaskStatus { READY, RUNNING, DONE, DEAD }
