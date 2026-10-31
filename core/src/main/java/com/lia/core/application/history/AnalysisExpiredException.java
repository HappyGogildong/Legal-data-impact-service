package com.lia.core.application.history;

/** 저장할 분석 결과가 세션에 없거나 그 법령 버전이 없어짐 — web 이 409 {@code analysis_expired} 로 매핑(D73). 메시지는 화면에 그대로. */
public class AnalysisExpiredException extends RuntimeException {

    public AnalysisExpiredException() {
        super("저장할 결과가 만료됐어요. 다시 분석한 뒤 저장해 주세요.");
    }
}
