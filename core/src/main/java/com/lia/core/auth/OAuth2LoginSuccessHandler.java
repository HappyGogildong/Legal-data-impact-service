package com.lia.core.auth;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 소셜 로그인 성공 → provider 신원을 내부 {@code userId} 로 매핑해 세션에 심는다(Auth, D60).
 *
 * <p>{@code (provider, subject)} 로 {@link AccountStore} 조회 → 없으면 생성(최초 로그인) →
 * 회수/발급한 {@code userId} 를 세션 속성({@link CurrentUser#SESSION_KEY})에 저장. 이후
 * {@link CurrentUser} 로 컨트롤러가 읽는다. 리다이렉트는 원래 요청지(SavedRequest)나 {@code "/"}.
 *
 * <p>provider 별 사용자정보 구조가 달라 subject·email 추출을 분기한다(google=OIDC top-level,
 * kakao=id+kakao_account 중첩, naver=response 중첩). email 은 미동의 시 null(알림 채널, nullable).
 */
@Component
public class OAuth2LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final AccountStore accountStore;

    public OAuth2LoginSuccessHandler(AccountStore accountStore) {
        this.accountStore = accountStore;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        var token = (OAuth2AuthenticationToken) authentication;
        String provider = token.getAuthorizedClientRegistrationId();
        Identity id = extract(provider, token.getPrincipal());

        UUID userId = accountStore.findByProvider(provider, id.subject())
                .orElseGet(() -> accountStore.create(provider, id.subject(), id.email()))
                .userId();
        request.getSession().setAttribute(CurrentUser.SESSION_KEY, userId);

        super.onAuthenticationSuccess(request, response, authentication);
    }

    /** provider 별 사용자정보에서 (opaque subject, email?) 정규화. */
    private static Identity extract(String provider, OAuth2User user) {
        Map<String, Object> attributes = user.getAttributes();
        return switch (provider) {
            case "google" -> new Identity(
                    str(attributes.get("sub")), str(attributes.get("email")));
            case "kakao" -> new Identity(
                    str(attributes.get("id")), nested(attributes, "kakao_account", "email"));
            case "naver" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> resp = (Map<String, Object>) attributes.get("response");
                yield new Identity(str(resp.get("id")), str(resp.get("email")));
            }
            default -> throw new IllegalStateException("지원하지 않는 provider: " + provider);
        };
    }

    private static String nested(Map<String, Object> attributes, String outer, String key) {
        Object inner = attributes.get(outer);
        return inner instanceof Map<?, ?> map ? str(map.get(key)) : null;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record Identity(String subject, String email) {}
}
