package com.lia.core.eval;

import com.lia.core.pipeline.discover.LawDiscovery;

/**
 * 평가 당시 법령 발견 설정(D83) — 보고서에 함께 실어, 설정이 다른 기준선과 비교하지 않게 사람이 본다.
 * 회귀 평가는 한 번에 이 값 하나만 바꾼다. 동작에 영향이 없는 노브는 두지 않는다.
 *
 * @param chunkFetch    법령 단위로 묶기 전에 가져오는 청크 수
 * @param maxCandidates 후보 최대 장수
 * @param minSimilarity 코사인 유사도 하한
 */
public record RagConfig(int chunkFetch, int maxCandidates, double minSimilarity) {

    public static RagConfig of(LawDiscovery.Settings settings) {
        return new RagConfig(settings.chunkFetch(), settings.maxCandidates(), settings.minSimilarity());
    }
}
