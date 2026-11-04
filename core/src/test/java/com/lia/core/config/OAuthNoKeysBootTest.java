package com.lia.core.config;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** oauth 프로파일이어도 키가 하나도 없으면 로그인 비활성으로 부팅한다(D78) — 예전엔 빈 client-id 로 부팅이 깨졌다. */
@SpringBootTest(properties = {
        "GOOGLE_CLIENT_ID=", "GOOGLE_CLIENT_SECRET=",
        "KAKAO_CLIENT_ID=", "KAKAO_CLIENT_SECRET=", "NAVER_CLIENT_ID=", "NAVER_CLIENT_SECRET="})
@ActiveProfiles("oauth")
class OAuthNoKeysBootTest {

    @Autowired WebApplicationContext context;

    @Test
    @DisplayName("부팅 성공 + 켜진 목록은 빈 목록")
    void 빈_목록() throws Exception {
        MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()
                .perform(get("/api/v1/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers.length()").value(0));
    }
}
