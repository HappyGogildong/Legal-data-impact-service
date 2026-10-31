package com.lia.core.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.lia.core.auth.CurrentUser;
import com.lia.core.testsupport.InMemoryAccountStore;
import com.lia.core.testsupport.InMemoryNotificationStore;

/** Notification API 웹 슬라이스 — 목록(최근 순·읽지 않은 수)·읽음(남의 것 무시)·모두 읽음·401·403. */
@SpringJUnitWebConfig(ApiSliceTestConfig.class)
class NotificationApiTest {

    @Autowired InMemoryAccountStore accounts;
    @Autowired InMemoryNotificationStore notifications;
    MockMvc mvc;
    UUID userId;

    @BeforeEach
    void setup(WebApplicationContext context) {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        userId = accounts.findOrCreate("kakao", UUID.randomUUID().toString()).userId();
    }

    private MockHttpServletRequestBuilder signedIn(MockHttpServletRequestBuilder request, UUID user) {
        return request.with(user("u")).sessionAttr(CurrentUser.SESSION_KEY, user);
    }

    @Test
    @DisplayName("목록은 최근 순·읽지 않은 수, 읽음 → 수 감소, 남의 알림 읽음은 무시, 모두 읽음")
    void 목록과_읽음() throws Exception {
        long older = notifications.add(userId, "001809", "주택법", LocalDate.of(2026, 11, 9), 7);
        long newer = notifications.add(userId, "000001", "근로기준법", LocalDate.of(2026, 11, 10), 1);
        UUID other = accounts.findOrCreate("kakao", UUID.randomUUID().toString()).userId();
        long others = notifications.add(other, "001809", "주택법", LocalDate.of(2026, 11, 9), 7);

        mvc.perform(signedIn(get("/api/v1/notifications"), userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2))
                .andExpect(jsonPath("$.items[0].id").value(newer))
                .andExpect(jsonPath("$.items[0].title").value("근로기준법"))
                .andExpect(jsonPath("$.items[0].read").value(false))
                .andExpect(jsonPath("$.items[1].id").value(older));

        mvc.perform(signedIn(post("/api/v1/notifications/" + newer + "/read"), userId).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(signedIn(post("/api/v1/notifications/" + others + "/read"), userId).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(signedIn(get("/api/v1/notifications"), userId)).andExpect(jsonPath("$.unreadCount").value(1));
        mvc.perform(signedIn(get("/api/v1/notifications"), other)).andExpect(jsonPath("$.unreadCount").value(1));

        mvc.perform(signedIn(post("/api/v1/notifications/read-all"), userId).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(signedIn(get("/api/v1/notifications"), userId)).andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    @DisplayName("CSRF 없음 → 403, 익명 → 401")
    void 인증_CSRF() throws Exception {
        mvc.perform(signedIn(post("/api/v1/notifications/read-all"), userId)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
    }
}
