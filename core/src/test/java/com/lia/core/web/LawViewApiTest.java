package com.lia.core.web;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Trending API 웹 슬라이스 — 익명 순위·조회 신호(CSRF 토큰 없이)·400·404. */
@SpringJUnitWebConfig(ApiSliceTestConfig.class)
class LawViewApiTest {

    MockMvc mvc;

    @BeforeEach
    void setup(WebApplicationContext context) {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("순위 — 익명 200, 기본 week, 항목·previousRank(null 은 생략되지 않고 null)")
    void 순위() throws Exception {
        mvc.perform(get("/api/v1/laws/trending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.window").value("week"))
                .andExpect(jsonPath("$.items[0].rank").value(1))
                .andExpect(jsonPath("$.items[0].lawId").value("001809"))
                .andExpect(jsonPath("$.items[0].views").value(12));
        mvc.perform(get("/api/v1/laws/trending").param("window", "month").param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.window").value("month"));
    }

    @Test
    @DisplayName("순위 — window·limit 가 잘못되면 400")
    void 순위_400() throws Exception {
        List<MockHttpServletRequestBuilder> bad = List.of(
                get("/api/v1/laws/trending").param("window", "year"),
                get("/api/v1/laws/trending").param("limit", "0"),
                get("/api/v1/laws/trending").param("limit", "51"));
        for (MockHttpServletRequestBuilder request : bad) {
            mvc.perform(request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("bad_request"));
        }
    }

    @Test
    @DisplayName("조회 신호 — 익명·CSRF 토큰 없이 204, 없는 버전 404, 날짜 형식 400")
    void 조회_신호() throws Exception {
        mvc.perform(post("/api/v1/laws/001809/2026-11-01/views")).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/laws/999999/2026-11-01/views"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
        mvc.perform(post("/api/v1/laws/001809/2026-13-01/views")).andExpect(status().isBadRequest());
    }
}
