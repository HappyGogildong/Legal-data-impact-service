package com.lia.core.web;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.lia.core.auth.CurrentUser;
import com.lia.core.testsupport.InMemoryAccountStore;

/** Account API 웹 슬라이스 — 조회(userId·email 없음)·계정 삭제(세션 무효화·쿠키 삭제). */
@SpringJUnitWebConfig(ApiSliceTestConfig.class)
class AccountApiTest {

    @Autowired InMemoryAccountStore accounts;
    MockMvc mvc;

    @BeforeEach
    void setup(WebApplicationContext context) {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("GET → provider·createdAt 만(userId·email 없음)")
    void 조회() throws Exception {
        UUID userId = accounts.findOrCreate("kakao", "k-get").userId();
        accounts.setNotificationEmail(userId, "user@kakao.com");

        mvc.perform(get("/api/v1/account").with(user("u")).sessionAttr(CurrentUser.SESSION_KEY, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("kakao"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    @DisplayName("세션이 가리키는 계정이 없으면 GET 401(로그인 상태 아님)")
    void 조회_계정없음_401() throws Exception {
        mvc.perform(get("/api/v1/account").with(user("u")).sessionAttr(CurrentUser.SESSION_KEY, UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE → 204, 계정 삭제, 세션 무효화, JSESSIONID 만료")
    void 계정삭제() throws Exception {
        UUID userId = accounts.findOrCreate("naver", "n-delete").userId();
        var session = new MockHttpSession();
        session.setAttribute(CurrentUser.SESSION_KEY, userId);

        mvc.perform(delete("/api/v1/account").with(user("u")).session(session).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("JSESSIONID", 0));

        assertTrue(session.isInvalid(), "세션 무효화");
        assertTrue(accounts.find(userId).isEmpty(), "계정 삭제");
    }
}
