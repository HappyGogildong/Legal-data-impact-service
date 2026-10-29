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
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.lia.core.auth.CurrentUser;
import com.lia.core.testsupport.InMemoryAccountStore;

/** Bookmark API 웹 슬라이스 — 204 멱등·404·409·목록 형식(상태 라벨)·401·403. */
@SpringJUnitWebConfig(ApiSliceTestConfig.class)
class BookmarkApiTest {

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

    @Test
    @DisplayName("PUT → 204, 다시 PUT → 204(멱등), GET 에 한 건")
    void 추가_멱등() throws Exception {
        mvc.perform(signedIn(put("/api/v1/bookmarks/001809")).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(signedIn(put("/api/v1/bookmarks/001809")).with(csrf())).andExpect(status().isNoContent());

        mvc.perform(signedIn(get("/api/v1/bookmarks")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].lawId").value("001809"))
                .andExpect(jsonPath("$.items[0].title").value("주택법"))
                .andExpect(jsonPath("$.items[0].status").value("시행예정"))
                .andExpect(jsonPath("$.items[0].effectiveDate").value("2026-11-01"))
                .andExpect(jsonPath("$.items[0].bookmarkedAt").exists());
    }

    @Test
    @DisplayName("목록 상태 라벨 — 시행중은 시행됨, 철회는 철회, 최근 추가가 먼저")
    void 상태_라벨() throws Exception {
        mvc.perform(signedIn(put("/api/v1/bookmarks/000001")).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(signedIn(put("/api/v1/bookmarks/000002")).with(csrf())).andExpect(status().isNoContent());

        mvc.perform(signedIn(get("/api/v1/bookmarks")))
                .andExpect(jsonPath("$.items[0].lawId").value("000002"))
                .andExpect(jsonPath("$.items[0].status").value("철회"))
                .andExpect(jsonPath("$.items[1].lawId").value("000001"))
                .andExpect(jsonPath("$.items[1].status").value("시행됨"));
    }

    @Test
    @DisplayName("없는 법령 PUT → 404 not_found")
    void 없는법령_404() throws Exception {
        mvc.perform(signedIn(put("/api/v1/bookmarks/999999")).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }

    @Test
    @DisplayName("한도(2) 초과 PUT → 409 bookmark_limit + 한도 메시지")
    void 한도_409() throws Exception {
        mvc.perform(signedIn(put("/api/v1/bookmarks/001809")).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(signedIn(put("/api/v1/bookmarks/000001")).with(csrf())).andExpect(status().isNoContent());

        mvc.perform(signedIn(put("/api/v1/bookmarks/000002")).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("bookmark_limit"))
                .andExpect(jsonPath("$.message").value("북마크는 최대 2개까지 저장할 수 있어요."));
    }

    @Test
    @DisplayName("DELETE → 204, 없어도 204")
    void 해제_204() throws Exception {
        mvc.perform(signedIn(put("/api/v1/bookmarks/001809")).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(signedIn(delete("/api/v1/bookmarks/001809")).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(signedIn(delete("/api/v1/bookmarks/001809")).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(signedIn(get("/api/v1/bookmarks"))).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    @DisplayName("CSRF 없는 PUT·DELETE → 403")
    void csrf_403() throws Exception {
        mvc.perform(signedIn(put("/api/v1/bookmarks/001809"))).andExpect(status().isForbidden());
        mvc.perform(signedIn(delete("/api/v1/bookmarks/001809"))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("익명 → GET·PUT 401")
    void 익명_401() throws Exception {
        mvc.perform(get("/api/v1/bookmarks")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/bookmarks/001809").with(csrf())).andExpect(status().isUnauthorized());
    }
}
