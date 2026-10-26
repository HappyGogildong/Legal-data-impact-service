package com.lia.core.web;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/** Law Catalog API 웹 슬라이스 — 익명 공개·검증 400·없음 404. */
@SpringJUnitWebConfig(ApiSliceTestConfig.class)
class LawApiTest {

    MockMvc mvc;

    @BeforeEach
    void setup(WebApplicationContext context) {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("목록 — 익명 200, 항목·다음 커서")
    void 목록() throws Exception {
        mvc.perform(get("/api/v1/laws").param("from", "2026-10-25").param("to", "2026-11-24").param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].lawId").value("001809"))
                .andExpect(jsonPath("$.items[0].title").value("주택법"))
                .andExpect(jsonPath("$.items[0].effectiveDate").value("2026-11-01"))
                .andExpect(jsonPath("$.nextCursor").value("next-cursor"));
    }

    @Test
    @DisplayName("목록 — 날짜 형식·달력·from>to·limit 범위·커서가 잘못되면 400")
    void 목록_400() throws Exception {
        List<MockHttpServletRequestBuilder> bad = List.of(
                get("/api/v1/laws").param("limit", "0"),
                get("/api/v1/laws").param("limit", "51"),
                get("/api/v1/laws").param("limit", "abc"),
                get("/api/v1/laws").param("from", "20261101"),
                get("/api/v1/laws").param("to", "2026-13-01"),
                get("/api/v1/laws").param("from", "2026-12-01").param("to", "2026-11-01"),
                get("/api/v1/laws").param("cursor", "!!!"));
        for (MockHttpServletRequestBuilder request : bad) {
            mvc.perform(request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("bad_request"));
        }
    }

    @Test
    @DisplayName("시행예정본 목록 — 200, 없는 법령은 404 not_found")
    void 시행예정본_목록() throws Exception {
        mvc.perform(get("/api/v1/laws/001809"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending[0].effectiveDate").value("2026-11-01"))
                .andExpect(jsonPath("$.current.effectiveDate").value("2020-01-01"));
        mvc.perform(get("/api/v1/laws/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }

    @Test
    @DisplayName("상세 — 200(현행 대비·기준선 시행일), 없는 버전 404, 날짜 형식 400")
    void 상세() throws Exception {
        mvc.perform(get("/api/v1/laws/001809/2026-11-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("주택법"))
                .andExpect(jsonPath("$.status").value("시행예정"))
                .andExpect(jsonPath("$.changedArticles[0].previousText").value("① 심의한다."))
                .andExpect(jsonPath("$.baselineEffectiveDate").value("2020-01-01"));
        mvc.perform(get("/api/v1/laws/001809/2027-01-01"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
        mvc.perform(get("/api/v1/laws/001809/2026-13-01"))
                .andExpect(status().isBadRequest());
    }
}
