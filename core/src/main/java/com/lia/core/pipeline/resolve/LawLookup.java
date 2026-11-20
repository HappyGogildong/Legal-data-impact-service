package com.lia.core.pipeline.resolve;

import java.util.List;

import com.lia.core.pipeline.connector.RawLaw;

/**
 * 해소(resolve)가 법령을 찾을 때 쓰는 <b>아웃바운드 포트</b>.
 *
 * <p>아키텍처 v0.8 §3.2 — 해소는 출처 API 가 아니라 <b>적재분</b>(Law Store)을 읽는다.
 * 구현은 {@code store.LawStoreLookup}(시행예정본 제목 검색, D87). SourceAnalyzer 는 구현을 모른다.
 */
public interface LawLookup {

    /** 법령명·키워드로 시행예정 법령 후보 검색. 실패 시 빈 목록(fail-closed). */
    List<RawLaw> searchByName(String query, int limit);
}
