package com.lia.core.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.lia.core.application.analysis.ActionRevisionUseCase;
import com.lia.core.application.analysis.RevisionOutcome;
import com.lia.core.auth.CurrentUser;
import com.lia.core.pipeline.dispatch.Unmet;
import com.lia.core.testsupport.InMemoryAccountStore;

/** 대응 방안 재생성 API 웹 슬라이스 — 익명 401 · CSRF 없음 403 · 로그인+CSRF 200(unmet 은 200 본문) · 답 검증 400. */
@SpringJUnitWebConfig(ApiSliceTestConfig.class)
class ActionRevisionApiTest {

    static final String PATH = "/api/v1/analyses/action-revisions";
    static final String BODY = """
            {"lawRef":{"lawId":"001809","effectiveDate":"2026-11-01"},"setId":"0123456789abcdef","answers":[{"questionId":"q1","answer":"예"}]}
            """;

    @Autowired InMemoryAccountStore accounts;
    @Autowired ActionRevisionUseCase useCase;
    MockMvc mvc;
    UUID userId;

    @BeforeEach
    void setup(WebApplicationContext context) {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        Mockito.reset(useCase);   // 컨텍스트 공유 싱글턴 목 — 앞선 테스트의 스텁을 지운다
        // 세션은 실제 계정을 가리켜야 한다 — 없는 계정이면 StaleSessionFilter 가 낡은 세션으로 파기한다
        userId = accounts.findOrCreate("kakao", UUID.randomUUID().toString()).userId();
    }

    private MockHttpServletRequestBuilder signedIn(MockHttpServletRequestBuilder request) {
        return request.with(user("u")).sessionAttr(CurrentUser.SESSION_KEY, userId);
    }

    private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON).content(BODY);
    }

    @Test
    @DisplayName("익명 POST → 401 (CSRF 토큰이 있어도 인증이 먼저)")
    void 익명은_401() throws Exception {
        mvc.perform(body(post(PATH)).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그인했지만 CSRF 토큰 없음 → 403 (CSRF 예외는 /analyses 만)")
    void csrf_없으면_403() throws Exception {
        mvc.perform(signedIn(body(post(PATH))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("로그인 + CSRF → 200, 못 만든 사유는 본문 unmet")
    void 로그인하면_200_unmet() throws Exception {
        when(useCase.revise(any(), any(), any(), any(), any())).thenReturn(new RevisionOutcome.NotRevised(
                new Unmet(Unmet.Reason.DAILY_LIMIT, "오늘 한도를 다 썼어요.")));

        mvc.perform(signedIn(body(post(PATH))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unmet.reason").value("DAILY_LIMIT"))
                .andExpect(jsonPath("$.action").doesNotExist());
    }

    @Test
    @DisplayName("질문 묶음 불일치 → 200, 본문 unmet.reason = SITUATION_EXPIRED")
    void 묶음_불일치는_200_만료() throws Exception {
        when(useCase.revise(any(), any(), any(), any(), any())).thenReturn(new RevisionOutcome.NotRevised(
                new Unmet(Unmet.Reason.SITUATION_EXPIRED, "상황 질문이 만료됐어요.")));

        mvc.perform(signedIn(body(post(PATH))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unmet.reason").value("SITUATION_EXPIRED"));
    }

    @Test
    @DisplayName("setId 누락 → 400 (유스케이스는 호출되지 않는다)")
    void setId_누락은_400() throws Exception {
        String withoutSetId = """
                {"lawRef":{"lawId":"001809","effectiveDate":"2026-11-01"},"answers":[{"questionId":"q1","answer":"예"}]}
                """;

        mvc.perform(signedIn(post(PATH)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(withoutSetId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"));
        Mockito.verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("답 검증 실패(IllegalArgumentException) → 400")
    void 답_검증_실패는_400() throws Exception {
        when(useCase.revise(any(), any(), any(), any(), any())).thenThrow(new IllegalArgumentException("답이 올바르지 않습니다."));

        mvc.perform(signedIn(body(post(PATH))).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"));
    }
}
