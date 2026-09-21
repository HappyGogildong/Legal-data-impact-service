package com.lia.core.auth;

import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * 세션에서 내부 {@code userId} 추출 헬퍼(컨트롤러용, Auth D60).
 *
 * <p>{@link OAuth2LoginSuccessHandler} 가 로그인 성공 시 세션에 심은 {@code userId} 를 읽는다.
 * Spring 의 principal(OAuth2User)은 인증용, {@code userId} 는 우리 도메인 키(프롬프트 미주입, D41).
 */
public final class CurrentUser {

    /** 세션 속성 키 — 핸들러(쓰기)와 헬퍼(읽기)의 단일 소스. */
    public static final String SESSION_KEY = "USER_ID";

    private CurrentUser() {}

    public static Optional<UUID> userId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return Optional.empty();
        return Optional.ofNullable((UUID) session.getAttribute(SESSION_KEY));
    }
}
