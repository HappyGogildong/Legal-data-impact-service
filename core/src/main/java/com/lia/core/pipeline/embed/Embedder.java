package com.lia.core.pipeline.embed;

import java.util.List;

/**
 * 텍스트 → 벡터. <b>적재·검색이 공유</b>하는 외부 임베딩 API 추상화(D32). 벤더 교체 지점.
 *
 * <p>이 포트가 존재하는 이유: 적재(passage)와 검색(query)이 <b>같은 모델·같은 dim</b>을 쓰도록
 * 인터페이스로 강제한다 — 인덱스↔쿼리 모델 불일치는 가장 흔한 RAG 붕괴다. 벤더 확정은
 * 벤치(Recall@5·MRR, D33) 후이며, 그때 같은 포트에 구현체만 추가한다(통제변인=모델 하나만 교체).
 *
 * <p>스펙: docs/components/Embedder.md.
 */
public interface Embedder {

    /** 임베딩 모드. 분리 모델(Upstage 등)은 query/passage를 다르게 처리, 대칭 모델(OpenAI)은 흡수. */
    enum Mode { PASSAGE, QUERY }

    /** 고정 차원(1536). {@link com.lia.core.store.LawStore} chunks(pgvector) 차원과 일치해야 한다. */
    int dim();

    /**
     * 텍스트들 → 벡터들. 입력 <b>순서대로</b> {@link #dim} 차원, 코사인용 단위 정규화.
     * 각 텍스트는 모델 max input tokens 이내여야 한다(초과분 분할은 호출자 책임).
     */
    List<float[]> embed(List<String> texts, Mode mode);

    /** 단건 편의 — {@link #embed(List, Mode)} 위임. */
    default float[] embed(String text, Mode mode) {
        return embed(List.of(text), mode).get(0);
    }
}
