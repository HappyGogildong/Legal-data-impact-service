package com.lia.core.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.lia.core.auth.CurrentUser;
import com.lia.core.auth.OAuth2LoginSuccessHandler;
import com.lia.core.testsupport.InMemoryAccountStore;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.Cookie;

/**
 * SecurityConfig 인가 슬라이스 테스트 — 실제 필터체인 + 스텁 컨트롤러(MockMvc).
 *
 * <p>Boot 4 는 {@code @WebMvcTest} 가 별도 모듈이라, Boot 없이 최소 웹 컨텍스트
 * ({@code @EnableWebMvc} + {@code @EnableWebSecurity} + {@link SecurityConfig})를 띄운다.
 * oauth 프로파일이 아니므로 {@code ClientRegistrationRepository} 가 없어 oauth2Login 은 붙지 않는다
 * (= 키 없는 dev/CI 부팅 경로 그대로). DB·IdP 불필요.
 */
@SpringJUnitWebConfig(SecurityConfigTest.TestConfig.class)
class SecurityConfigTest {

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfig.class)
    static class TestConfig {
        @Bean
        InMemoryAccountStore accountStore() {
            return new InMemoryAccountStore();
        }

        @Bean
        OAuth2LoginSuccessHandler loginSuccessHandler(InMemoryAccountStore accounts) {
            return new OAuth2LoginSuccessHandler(accounts, "http://localhost:3000");
        }

        @Bean
        StubController stubController() {
            return new StubController();
        }
    }

    /** 인가 규칙만 보려는 스텁 — 통과하면 200. */
    @RestController
    static class StubController {
        @GetMapping("/api/v1/profile") String getProfile() { return "ok"; }
        @PutMapping("/api/v1/profile") String putProfile() { return "ok"; }
        @PostMapping("/api/v1/analyses") String analyze() { return "ok"; }
        @GetMapping("/api/v1/laws/001809") String law() { return "ok"; }
        @GetMapping("/api/v1/unmapped-rule") String unmapped() { return "ok"; }
        @GetMapping("/actuator/health") String health() { return "ok"; }
        @GetMapping("/error") String error() { return "error"; }
    }

    MockMvc mvc;

    @BeforeEach
    void setup(WebApplicationContext context) {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // --- 인증 ------------------------------------------------------------

    @Test
    @DisplayName("익명 → 보호 API 는 401 (403·302 아님), 본문은 {error: unauthenticated}")
    void 익명_보호API_401() throws Exception {
        mvc.perform(get("/api/v1/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthenticated"));
    }

    @Test
    @DisplayName("익명 → 규칙에 없는 API 도 401 (anyRequest 화이트리스트 종결)")
    void 익명_미등록API_401() throws Exception {
        mvc.perform(get("/api/v1/unmapped-rule")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("인증 사용자 → 보호 API 200")
    void 인증_보호API_200() throws Exception {
        mvc.perform(get("/api/v1/profile").with(user("u"))).andExpect(status().isOk());
    }

    // --- 세션-계정 정합 (StaleSessionFilter) -----------------------------

    @Autowired InMemoryAccountStore accounts;

    /** 다른 기기에서 계정이 삭제돼, accounts 에 없는 userId 를 들고 있는 세션. */
    private static MockHttpSession staleSession() {
        var session = new MockHttpSession();
        session.setAttribute(CurrentUser.SESSION_KEY, UUID.randomUUID());
        return session;
    }

    @Test
    @DisplayName("삭제된 계정을 가리키는 세션 → 보호 API 401 + 세션 파기")
    void 낡은세션_보호API_401() throws Exception {
        MockHttpSession session = staleSession();

        mvc.perform(get("/api/v1/profile").with(user("u")).session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthenticated"));
        assertTrue(session.isInvalid(), "낡은 세션은 파기");
    }

    @Test
    @DisplayName("삭제된 계정을 가리키는 세션 → 공개 API 는 익명으로 정상 처리")
    void 낡은세션_공개API_통과() throws Exception {
        MockHttpSession session = staleSession();

        mvc.perform(post("/api/v1/analyses").with(user("u")).session(session)).andExpect(status().isOk());
        assertTrue(session.isInvalid());
    }

    @Test
    @DisplayName("살아 있는 계정을 가리키는 세션 → 그대로 통과")
    void 유효세션_통과() throws Exception {
        UUID userId = accounts.findOrCreate("kakao", "k-live").userId();

        mvc.perform(get("/api/v1/profile").with(user("u")).sessionAttr(CurrentUser.SESSION_KEY, userId))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/api/** 밖(로그인 흐름 등)은 검사하지 않는다 — 낡은 세션도 파기하지 않음")
    void 낡은세션_API밖_검사안함() throws Exception {
        MockHttpSession session = staleSession();

        mvc.perform(get("/actuator/health").session(session)).andExpect(status().isOk());
        assertFalse(session.isInvalid(), "API 밖에서는 세션을 건드리지 않는다");
    }

    // --- 공개 ------------------------------------------------------------

    @Test
    @DisplayName("익명 → laws 조회·health 공개")
    void 익명_공개조회() throws Exception {
        mvc.perform(get("/api/v1/laws/001809")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("익명 → POST analyses 는 CSRF 토큰 없이 통과(공개 무상태)")
    void 익명_analyses_CSRF예외() throws Exception {
        mvc.perform(post("/api/v1/analyses")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("익명 → /error 는 ERROR 디스패치에서도 통과(공개 API 오류가 403 으로 바뀌지 않음)")
    void 익명_error_ERROR디스패치() throws Exception {
        mvc.perform(get("/error").with(request -> {
            request.setDispatcherType(DispatcherType.ERROR);
            return request;
        })).andExpect(status().isOk());
    }

    // --- CSRF (SPA) ------------------------------------------------------

    @Test
    @DisplayName("인증 사용자라도 CSRF 토큰 없는 상태변경은 403")
    void 상태변경_토큰없음_403() throws Exception {
        mvc.perform(put("/api/v1/profile").with(user("u"))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SPA 흐름 — GET 으로 받은 XSRF-TOKEN 쿠키값을 그대로 헤더에 실으면 상태변경 통과")
    void SPA_쿠키원문토큰_통과() throws Exception {
        Cookie xsrf = mvc.perform(get("/api/v1/profile").with(user("u")))
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(xsrf, "GET 응답에 XSRF-TOKEN 쿠키가 써져야 SPA 가 토큰을 얻는다");

        mvc.perform(put("/api/v1/profile").with(user("u"))
                        .cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그아웃은 리다이렉트 대신 204")
    void 로그아웃_204() throws Exception {
        Cookie xsrf = mvc.perform(get("/api/v1/profile").with(user("u")))
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(xsrf);

        mvc.perform(post("/logout").with(user("u"))
                        .cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isNoContent());
    }
}
