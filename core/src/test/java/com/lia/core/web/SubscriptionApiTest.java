package com.lia.core.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.lia.core.auth.CurrentUser;
import com.lia.core.testsupport.InMemoryAccountStore;

/** Subscription API 웹 슬라이스 — PUT 만들기·변경, 400·404·409, DELETE 멱등, 목록(다음 시행일 생략), 401·403. */
@SpringJUnitWebConfig(ApiSliceTestConfig.class)
class SubscriptionApiTest {

    @Autowired InMemoryAccountStore accounts;
    MockMvc mvc;
    UUID userId;

    @BeforeEach
    void setup(WebApplicationContext context) {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        userId = accounts.findOrCreate("kakao", UUID.randomUUID().toString()).userId();
    }

    private MockHttpServletRequestBuilder signedIn(MockHttpServletRequestBuilder request) {
        return request.with(user("u")).sessionAttr(CurrentUser.SESSION_KEY, userId);
    }

    private MockHttpServletRequestBuilder putSubscription(String lawId, String json) {
        return signedIn(put("/api/v1/subscriptions/" + lawId)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    @Test
    @DisplayName("PUT → 204, 다시 PUT 다른 시점 → 204, 목록에 바뀐 시점·다음 시행일")
    void 만들기_변경_목록() throws Exception {
        mvc.perform(putSubscription("001809", "{\"notifyBefore\":7}")).andExpect(status().isNoContent());
        mvc.perform(putSubscription("001809", "{\"notifyBefore\":1}")).andExpect(status().isNoContent());
        mvc.perform(putSubscription("000001", "{\"notifyBefore\":30}")).andExpect(status().isNoContent());

        mvc.perform(signedIn(get("/api/v1/subscriptions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].lawId").value("001809"))
                .andExpect(jsonPath("$.items[0].title").value("주택법"))
                .andExpect(jsonPath("$.items[0].notifyBefore").value(1))
                .andExpect(jsonPath("$.items[0].nextEffectiveDate").value("2026-11-01"))
                .andExpect(jsonPath("$.items[1].lawId").value("000001"))
                .andExpect(jsonPath("$.items[1].nextEffectiveDate").doesNotExist());
    }

    @Test
    @DisplayName("허용 밖·누락 시점 → 400, 없는 법령 → 404, 한도(2) → 409")
    void 오류() throws Exception {
        mvc.perform(putSubscription("001809", "{\"notifyBefore\":3}")).andExpect(status().isBadRequest());
        mvc.perform(putSubscription("001809", "{}")).andExpect(status().isBadRequest());
        mvc.perform(putSubscription("999999", "{\"notifyBefore\":7}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
        mvc.perform(putSubscription("001809", "{\"notifyBefore\":7}")).andExpect(status().isNoContent());
        mvc.perform(putSubscription("000001", "{\"notifyBefore\":7}")).andExpect(status().isNoContent());
        mvc.perform(putSubscription("000002", "{\"notifyBefore\":7}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("subscription_limit"))
                .andExpect(jsonPath("$.message").value("구독은 최대 2개까지 할 수 있어요."));
    }

    @Test
    @DisplayName("DELETE → 204, 없어도 204")
    void 해지() throws Exception {
        mvc.perform(putSubscription("001809", "{\"notifyBefore\":7}")).andExpect(status().isNoContent());
        mvc.perform(signedIn(delete("/api/v1/subscriptions/001809")).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(signedIn(delete("/api/v1/subscriptions/001809")).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(signedIn(get("/api/v1/subscriptions"))).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    @DisplayName("CSRF 없음 → 403, 익명 → 401")
    void 인증_CSRF() throws Exception {
        mvc.perform(signedIn(put("/api/v1/subscriptions/001809"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"notifyBefore\":7}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/subscriptions")).andExpect(status().isUnauthorized());
    }
}
