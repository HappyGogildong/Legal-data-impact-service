package com.lia.core.web;

/** 요청한 법령·버전이 저장소에 없음 — 404(service-api-spec §3.2·§3.3). */
public class LawNotFoundException extends RuntimeException {

    public LawNotFoundException(String what) {
        super("법령을 찾을 수 없습니다: " + what);
    }
}
