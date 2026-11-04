package com.lia.core.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** 운영 첫 배포 모양(D78) — 실제 전체 컨텍스트 + oauth 프로파일 + 구글 키만. Boot 자동 설정과 함께 부팅되고 로그인이 시작된다. DB 불필요(지연 연결). */
@SpringBootTest(properties = {
        "GOOGLE_CLIENT_ID=test-google-id", "GOOGLE_CLIENT_SECRET=test-google-secret",
        "KAKAO_CLIENT_ID=", "KAKAO_CLIENT_SECRET=", "NAVER_CLIENT_ID=", "NAVER_CLIENT_SECRET="})
@ActiveProfiles("oauth")
class OAuthGoogleOnlyBootTest {

    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("켜진 목록은 [google]")
    void 목록() throws Exception {
        mvc.perform(get("/api/v1/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers.length()").value(1))
                .andExpect(jsonPath("$.providers[0]").value("google"));
    }

    @Test
    @DisplayName("구글 로그인 시작 → 구글로 302")
    void 구글_시작() throws Exception {
        String location = mvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().isFound())
                .andReturn().getResponse().getRedirectedUrl();
        assertNotNull(location);
        assertTrue(location.startsWith("https://accounts.google.com/"), location);
    }

    @Test
    @DisplayName("꺼진 provider 로 로그인을 시작해도 그 provider 로 보내지 않는다")
    void 카카오_꺼짐() throws Exception {
        String location = mvc.perform(get("/oauth2/authorization/kakao"))
                .andReturn().getResponse().getRedirectedUrl();
        assertTrue(location == null || !location.contains("kakao.com"), location);
    }
}
