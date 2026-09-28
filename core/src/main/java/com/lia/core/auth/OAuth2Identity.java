package com.lia.core.auth;

import java.util.Map;

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * provider별 사용자정보 → (opaque subject, email?) 정규화(Auth, D60·D61).
 *
 * <p>세 IdP의 구조가 다르다: google=OIDC top-level, kakao=id + kakao_account 중첩, naver=response 중첩.
 * 로그인 핸들러(subject)와 알림 이메일 동의(email)가 <b>이 한 곳의 분기</b>를 공유한다.
 * 없는 값은 null. 설계: docs/components/auth/OAuth2LoginSuccessHandler.md §provider 속성 분기
 */
public record OAuth2Identity(String subject, String email) {

    public static OAuth2Identity of(OAuth2AuthenticationToken token) {
        return from(token.getAuthorizedClientRegistrationId(), token.getPrincipal());
    }

    public static OAuth2Identity from(String provider, OAuth2User user) {
        Map<String, Object> attributes = user.getAttributes();
        return switch (provider) {
            case "google" -> new OAuth2Identity(
                    str(attributes.get("sub")), str(attributes.get("email")));
            case "kakao" -> new OAuth2Identity(
                    str(attributes.get("id")), nested(attributes, "kakao_account", "email"));
            case "naver" -> new OAuth2Identity(
                    nested(attributes, "response", "id"), nested(attributes, "response", "email"));
            default -> throw new IllegalStateException("지원하지 않는 provider: " + provider);
        };
    }

    public boolean hasSubject() {
        return subject != null && !subject.isBlank();
    }

    private static String nested(Map<String, Object> attributes, String outer, String key) {
        Object inner = attributes.get(outer);
        return inner instanceof Map<?, ?> map ? str(map.get(key)) : null;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
