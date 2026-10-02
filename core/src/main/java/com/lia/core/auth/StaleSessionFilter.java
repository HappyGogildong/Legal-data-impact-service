package com.lia.core.auth;

import java.io.IOException;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 삭제된 계정을 가리키는 세션 정리(Auth, D61).
 *
 * <p>계정 삭제는 현재 세션만 파기한다(인메모리 세션 저장소는 사용자별 세션 조회가 안 됨). 다른 기기의 세션은
 * 삭제된 {@code userId} 를 들고 인증된 채로 남는다. {@code /api/**} 요청에서 그 계정이 없으면 세션을 파기하고
 * SecurityContext 를 비워 <b>익명으로 계속 진행</b>한다 — 보호 API 는 인가 단계에서 401, 공개 API 는 그대로 처리.
 * 엔드포인트마다 따로 처리하지 않아도 된다. 로그인 흐름({@code /api/**} 밖)은 재로그인을 방해하지 않게 건너뛴다.
 *
 * <p>{@code @Component} 가 아니다 — Boot 가 서블릿 필터로 한 번 더 등록하지 않도록 SecurityConfig 가 체인에 직접 넣는다.
 * 설계: docs/components/auth/SecurityConfig.md §세션-계정 정합
 */
public class StaleSessionFilter extends OncePerRequestFilter {

    private final AccountStore accountStore;

    public StaleSessionFilter(AccountStore accountStore) {
        this.accountStore = accountStore;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CurrentUser.userId(request)
                .filter(userId -> accountStore.find(userId).isEmpty())
                .ifPresent(userId -> {
                    request.getSession(false).invalidate();   // userId 를 읽었으니 세션은 있다
                    SecurityContextHolder.clearContext();
                });
        chain.doFilter(request, response);
    }
}
