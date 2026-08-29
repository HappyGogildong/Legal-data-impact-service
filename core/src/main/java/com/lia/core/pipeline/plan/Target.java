package com.lia.core.pipeline.plan;

/**
 * 질의 대상의 두 형태(D46) — 근본적으로 다른 연산이라 sealed로 나눠 dispatcher가 형태별 처리.
 * {@code Reference}=해소된 1건("주택법 바뀌면?"), {@code Discovery}=코퍼스 검색 N건("영향 있을 법 찾아줘").
 */
public sealed interface Target permits Target.Reference, Target.Discovery {

    record Reference(LawRef lawRef) implements Target {}

    record Discovery(DiscoveryCriteria criteria) implements Target {}
}
