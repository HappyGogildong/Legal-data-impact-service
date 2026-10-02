package com.lia.core.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.lia.core.auth.CurrentUser;
import com.lia.core.testsupport.InMemoryAccountStore;
import com.lia.core.testsupport.InMemoryUserProfileStore;

/** Consent API 웹 슬라이스 — 프로필 동의(14세·버전)·알림 이메일 동의(세션 principal 이메일)·철회·상태. */
@SpringJUnitWebConfig(ApiSliceTestConfig.class)
class ConsentApiTest {

    @Autowired InMemoryUserProfileStore profiles;
    @Autowired InMemoryAccountStore accounts;
    MockMvc mvc;

    @BeforeEach
    void setup(WebApplicationContext context) {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static OAuth2AuthenticationToken oauth(String provider, Map<String, Object> attrs, String nameKey) {
        var principal = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")), attrs, nameKey);
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), provider);
    }

    @Test
    @DisplayName("프로필 동의 → 200 현재 버전·upToDate, 상태에 반영(알림은 null)")
    void 프로필동의() throws Exception {
        UUID userId = accounts.findOrCreate("kakao", UUID.randomUUID().toString()).userId();

        mvc.perform(put("/api/v1/consents/profile").with(user("u")).sessionAttr(CurrentUser.SESSION_KEY, userId)
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"over14\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyVersion").value(ApiSliceTestConfig.POLICY))
                .andExpect(jsonPath("$.upToDate").value(true));

        mvc.perform(get("/api/v1/consents").with(user("u")).sessionAttr(CurrentUser.SESSION_KEY, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.upToDate").value(true))
                .andExpect(jsonPath("$.notificationEmail").isEmpty());
    }

    @Test
    @DisplayName("over14 false·누락 → 400")
    void 프로필동의_14세미확인_400() throws Exception {
        UUID userId = accounts.findOrCreate("kakao", UUID.randomUUID().toString()).userId();
        for (String body : List.of("{\"over14\":false}", "{}")) {
            mvc.perform(put("/api/v1/consents/profile").with(user("u")).sessionAttr(CurrentUser.SESSION_KEY, userId)
                            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("옛 버전 동의 → 상태 upToDate=false")
    void 옛버전_upToDateFalse() throws Exception {
        UUID userId = accounts.findOrCreate("kakao", UUID.randomUUID().toString()).userId();
        profiles.recordConsent(userId, "old-version");

        mvc.perform(get("/api/v1/consents").with(user("u")).sessionAttr(CurrentUser.SESSION_KEY, userId))
                .andExpect(jsonPath("$.profile.upToDate").value(false))
                .andExpect(jsonPath("$.profile.policyVersion").value("old-version"));
    }

    @Test
    @DisplayName("알림 동의 — 세션 principal 의 IdP 이메일 저장, 상태에 반영, 철회 204 후 null")
    void 알림동의_철회() throws Exception {
        UUID userId = accounts.findOrCreate("google", "g-consent").userId();
        var google = oauth("google", Map.of("sub", "g-consent", "email", "user@gmail.com"), "sub");

        mvc.perform(put("/api/v1/consents/notification-email").with(authentication(google))
                        .sessionAttr(CurrentUser.SESSION_KEY, userId).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@gmail.com"))
                .andExpect(jsonPath("$.consentedAt").exists());

        mvc.perform(get("/api/v1/consents").with(authentication(google)).sessionAttr(CurrentUser.SESSION_KEY, userId))
                .andExpect(jsonPath("$.notificationEmail.email").value("user@gmail.com"));

        mvc.perform(delete("/api/v1/consents/notification-email").with(authentication(google))
                        .sessionAttr(CurrentUser.SESSION_KEY, userId).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/consents").with(authentication(google)).sessionAttr(CurrentUser.SESSION_KEY, userId))
                .andExpect(jsonPath("$.notificationEmail").isEmpty());
    }

    @Test
    @DisplayName("세션이 가리키는 계정이 없음(다른 기기에서 계정 삭제) → 401 unauthenticated")
    void 알림동의_계정없음_401() throws Exception {
        UUID userId = UUID.randomUUID();   // accounts 에 없는 userId(계정이 삭제된 세션 흉내)
        var google = oauth("google", Map.of("sub", "g-gone", "email", "user@gmail.com"), "sub");

        mvc.perform(put("/api/v1/consents/notification-email").with(authentication(google))
                        .sessionAttr(CurrentUser.SESSION_KEY, userId).with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthenticated"));
    }

    @Test
    @DisplayName("계정이 삭제된 세션 → GET 상태도 401(200 + null 로 보이지 않음)")
    void 상태_계정없음_401() throws Exception {
        mvc.perform(get("/api/v1/consents").with(user("u")).sessionAttr(CurrentUser.SESSION_KEY, UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthenticated"));
    }

    @Test
    @DisplayName("익명 GET → 401")
    void 조회_익명_401() throws Exception {
        mvc.perform(get("/api/v1/consents")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("인증됐지만 CSRF 없음 → 403")
    void 프로필동의_CSRF없음_403() throws Exception {
        UUID userId = accounts.findOrCreate("kakao", UUID.randomUUID().toString()).userId();

        mvc.perform(put("/api/v1/consents/profile").with(user("u")).sessionAttr(CurrentUser.SESSION_KEY, userId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"over14\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IdP 가 이메일을 안 줬거나(OAuth2 인데 email 없음) OAuth2 가 아닌 principal → 400")
    void 알림동의_이메일없음_400() throws Exception {
        UUID userId = accounts.findOrCreate("kakao", "k-no-email").userId();
        var kakaoWithoutEmail = oauth("kakao", Map.of("id", 777L), "id");

        mvc.perform(put("/api/v1/consents/notification-email").with(authentication(kakaoWithoutEmail))
                        .sessionAttr(CurrentUser.SESSION_KEY, userId).with(csrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/consents/notification-email").with(user("u"))
                        .sessionAttr(CurrentUser.SESSION_KEY, userId).with(csrf()))
                .andExpect(status().isBadRequest());
    }
}
