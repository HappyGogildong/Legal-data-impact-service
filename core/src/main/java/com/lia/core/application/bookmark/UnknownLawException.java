package com.lia.core.application.bookmark;

/** 저장된 버전이 하나도 없는 법령을 북마크하려 함 — web 이 404 {@code not_found} 로 매핑. */
public class UnknownLawException extends RuntimeException {

    public UnknownLawException(String lawId) {
        super("법령을 찾을 수 없습니다: " + lawId);
    }
}
