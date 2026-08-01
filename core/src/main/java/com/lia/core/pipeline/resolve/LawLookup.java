package com.lia.core.pipeline.resolve;

import java.util.List;

import com.lia.core.pipeline.connector.RawLaw;

/**
 * 해소(resolve)가 법령을 찾을 때 쓰는 <b>아웃바운드 포트</b>.
 *
 * <p>SourceAnalyzer 를 커넥터에서 떼어내기 위한 경계다. 아키텍처 v0.8 §3.2 에서
 * 해소는 <b>Law Store·Vector Index(오프라인 적재분)</b> 를 읽는 것이지 출처 API 를
 * 직접 부르는 것이 아니다. 저장소가 생기기 전까지는 LawConnector 를 어댑터로 꽂고,
 * 이후 구현만 갈아끼우면 SourceAnalyzer 는 손대지 않는다.
 */
public interface LawLookup {

    /** 법령명·키워드로 시행예정 법령 후보 검색. 실패 시 빈 목록(fail-closed). */
    List<RawLaw> searchByName(String query, int limit);
}
