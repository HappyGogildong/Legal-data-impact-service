package com.lia.core.auth;

import java.io.IOException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 소셜 로그인 성공 → provider 신원을 내부 {@code userId} 로 매핑해 세션에 심는다(Auth, D60).
 *
 * <p>{@code (provider, subject)} 로 {@link AccountStore#findOrCreate} → {@code userId} 를 세션 속성
 * ({@link CurrentUser#SESSION_KEY})에 저장 → 프론트({@code lia.auth.frontend-url})로 리다이렉트.
 * SavedRequest 를 쓰지 않는다 — 저장된 요청은 API URL(401 을 받은)이라 브라우저를 JSON 으로 보내게 된다.
 * 이메일은 저장하지 않는다(로그인 중엔 principal 에만 있고 알림 동의 시 저장, D61).
 *
 * <p>provider별 추출은 {@link OAuth2Identity}. subject 가 없으면 fail-closed: 세션을 파기하고
 * 프론트 로그인 오류로 보낸다.
 */
@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AccountStore accountStore;
    private final String frontendUrl;

    public OAuth2LoginSuccessHandler(AccountStore accountStore,
            @Value("${lia.auth.frontend-url}") String frontendUrl) {
        this.accountStore = accountStore;
        this.frontendUrl = frontendUrl;
        setDefaultTargetUrl(frontendUrl);
        setAlwaysUseDefaultTargetUrl(true);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        var token = (OAuth2AuthenticationToken) authentication;
        OAuth2Identity id = OAuth2Identity.of(token);

        if (!id.hasSubject()) {
            // 인증 필터가 이미 SecurityContext 를 세션에 저장했으므로 세션째 파기해야 로그인 상태가 남지 않는다
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) session.invalidate();
            getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/login?error=identity");
            return;
        }

        UUID userId = accountStore.findOrCreate(token.getAuthorizedClientRegistrationId(), id.subject())
                .userId();   // email 은 저장하지 않는다 — 알림 동의로만(D61)
        request.getSession().setAttribute(CurrentUser.SESSION_KEY, userId);

        super.onAuthenticationSuccess(request, response, authentication);
    }
}
