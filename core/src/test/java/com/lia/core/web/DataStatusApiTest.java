package com.lia.core.web;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Data Status API 웹 슬라이스 — 익명 공개·응답 필드. */
@SpringJUnitWebConfig(ApiSliceTestConfig.class)
class DataStatusApiTest {

    MockMvc mvc;

    @BeforeEach
    void setup(WebApplicationContext context) {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("익명 GET → 200, 갱신 시각(문자열)·시행예정 수")
    void 익명_공개() throws Exception {
        mvc.perform(get("/api/v1/data-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lawsUpdatedAt").isString())
                .andExpect(jsonPath("$.pendingLawCount").value(912));
    }
}
