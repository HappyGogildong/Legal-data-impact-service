package com.lia.core.pipeline.index;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Law;
import com.lia.core.store.Chunk;
import com.lia.core.store.ChunkStore;

/**
 * 시행예정 코퍼스 색인기(오프라인, D30·D55) — <b>변경 조문 + 제개정이유 요약</b>을 청킹해
 * {@link ChunkStore}에 적재. 임베딩은 하지 않는다(ChunkStore 안 PgVectorStore가 수행).
 *
 * <p>스펙: docs/components/RAGIndexer.md.
 */
public class RAGIndexer {

    /**
     * 단일 청크 최대 문자수 — 초과 시 오버랩 분할(D55). 임베딩 입력 토큰 한도를 넘지 않도록
     * <b>보수적으로 잡은 문자 기준</b>이다(토크나이저 의존 회피).
     *
     * <p>주의: 문자수 ≠ 토큰수. `text-embedding-3-small`(cl100k)에서 <b>한국어는 음절당 토큰이 많아</b>
     * (대략 2~3토큰/음절) 6000자면 8191토큰 한도를 넘길 수 있다. 그래서 영어 기준이 아니라
     * 한국어 기준으로 보수적으로 2500자로 둔다. 정밀 제한이 필요하면 jtokkit(cl100k) 토큰 카운트로 교체.
     */
    static final int MAX_CHARS = 2500;

    /** 분할 시 인접 청크가 겹치는 문자수 — 경계에서 잘린 문맥 손실 완화. */
    static final int OVERLAP = 200;

    private static final String NAMESPACE = "pending";

    private final ChunkStore chunkStore;

    public RAGIndexer(ChunkStore chunkStore) {
        this.chunkStore = chunkStore;
    }

    /** 시행예정 {@code Law}를 청킹해 적재. 변경 조문 청크 + 법령단위 요약 청크. */
    public void index(Law pending) {
        List<Chunk> chunks = new ArrayList<>();

        for (Article article : pending.changedArticles()) {
            addChunks(chunks, pending.sourceId(article), article.text(),
                    meta(pending, "article", article.no(), article.changed()));
        }

        String amendReason = pending.amendReason();
        if (amendReason != null && !amendReason.isBlank()) {
            addChunks(chunks, pending.ref(), amendReason, meta(pending, "summary", null, null));
        }

        // 정본 단위 완전 교체 — 재색인 시 저장 상태 == 현재 법령 상태(stale 청크 없음).
        chunkStore.replaceVersion(pending.lawId(), pending.effectiveDate().toString(), chunks);
    }

    /** content가 한도 이내면 단일 청크, 초과면 오버랩 분할해 {@code sourceId#k} 로 담는다. */
    private static void addChunks(List<Chunk> out, String sourceId, String content, Map<String, Object> meta) {
        if (content == null) return;
        if (content.length() <= MAX_CHARS) {
            out.add(new Chunk(sourceId, content, meta));
            return;
        }
        int step = MAX_CHARS - OVERLAP;
        int part = 0;
        for (int start = 0; start < content.length(); start += step) {
            int end = Math.min(start + MAX_CHARS, content.length());
            out.add(new Chunk(sourceId + "#" + part, content.substring(start, end), meta));
            part++;
            if (end == content.length()) break;
        }
    }

    private static Map<String, Object> meta(Law law, String kind, String articleNo, Boolean changed) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", kind);
        m.put("namespace", NAMESPACE);
        m.put("lawId", law.lawId());
        m.put("title", law.title());            // 의미검색 히트를 RawLaw 후보로 되살릴 때 필요
        m.put("efYd", law.effectiveDate().toString());
        if (articleNo != null) m.put("articleNo", articleNo);
        if (changed != null) m.put("changed", changed);
        return m;
    }
}
