package com.lia.core.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;
import com.lia.core.application.history.HistoryUseCase;
import com.lia.core.auth.CurrentUser;
import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.analysis.SituationQuestion;
import com.lia.core.domain.analysis.StepNote;
import com.lia.core.pipeline.dispatch.Unmet;
import com.lia.core.pipeline.plan.LawRef;
import com.lia.core.store.AnalysisHistoryStore;
import com.lia.core.testsupport.InMemoryAccountStore;

/** History API 웹 슬라이스 — 세션 결과로 저장(201/200 멱등)·만료 409·한도 409·목록·상세(남의 것 404)·삭제 204·400·401·403. */
@SpringJUnitWebConfig(ApiSliceTestConfig.class)
class HistoryApiTest {

    static final LawRef HOUSING = new LawRef("001809", ApiSliceTestConfig.LAW_EFFECTIVE, null);
    static final String SET_ID = "set-1";

    @Autowired InMemoryAccountStore accounts;
    @Autowired HistoryUseCase history;
    MockMvc mvc;
    MockHttpSession session;

    @BeforeEach
    void setup(WebApplicationContext context) {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        session = signedInSession();
    }

    private MockHttpSession signedInSession() {
        MockHttpSession created = new MockHttpSession();
        created.setAttribute(CurrentUser.SESSION_KEY, accounts.findOrCreate("kakao", UUID.randomUUID().toString()).userId());
        return created;
    }

    private String remember(MockHttpSession target, LawRef ref) {
        AnalysisApiResponse body = AnalysisApiResponse.analyzed(
                new AnalysisApiResponse.LawHeader("LAW:" + ref.lawId() + "@" + ref.effectiveDate(), ref.lawId(), "주택법",
                        ref.effectiveDate(), "법률", "국토교통부"),
                List.of(), Map.of(), Map.of(), List.of(), "참고용",
                new AnalysisApiResponse.SituationBody(SET_ID, List.of(), List.of()));
        return new HttpSessionRecentAnalyses(target).remember(userIdOf(target), "주택법 바뀌면?", ref, body);
    }

    private static UUID userIdOf(MockHttpSession target) {
        return (UUID) target.getAttribute(CurrentUser.SESSION_KEY);
    }

    private MockHttpServletRequestBuilder signedIn(MockHttpServletRequestBuilder request, MockHttpSession target) {
        return request.with(user("u")).session(target);
    }

    private MockHttpServletRequestBuilder save(String analysisId, MockHttpSession target) {
        return signedIn(post("/api/v1/history"), target).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"analysisId\":\"" + analysisId + "\"}");
    }

    /** 새로 저장하고(201) 돌려받은 id — 인메모리 저장소는 컨텍스트 공유라 id 가 테스트마다 이어진다. */
    private long saveAndGetId(String analysisId, MockHttpSession target) throws Exception {
        return savedId(mvc.perform(save(analysisId, target)).andExpect(status().isCreated()).andReturn());
    }

    private static long savedId(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    @Test
    @DisplayName("POST → 201 {id}, 같은 analysisId 다시 → 200 같은 id, 목록·상세에 보인다")
    void 저장_멱등_목록_상세() throws Exception {
        String analysisId = remember(session, HOUSING);

        long id = saveAndGetId(analysisId, session);
        MvcResult again = mvc.perform(save(analysisId, session)).andExpect(status().isOk()).andReturn();
        assertEquals(id, savedId(again));

        mvc.perform(signedIn(get("/api/v1/history"), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.items[0].query").value("주택법 바뀌면?"))
                .andExpect(jsonPath("$.items[0].lawTitle").value("주택법"))
                .andExpect(jsonPath("$.items[0].lawUpdated").value(false));

        mvc.perform(signedIn(get("/api/v1/history/" + id), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.body.law.lawId").value("001809"))
                .andExpect(jsonPath("$.body.analysisId").doesNotExist())
                .andExpect(jsonPath("$.answers").isArray())
                .andExpect(jsonPath("$.revision").doesNotExist())
                .andExpect(jsonPath("$.latestEffectiveDate").value("2026-11-01"));
    }

    @Test
    @DisplayName("저장 뒤 대응 방안을 재생성하고 다시 저장하면 200 같은 id, 상세가 재생성본·답으로 바뀐다")
    void 다시_저장하면_스냅샷_갱신() throws Exception {
        String analysisId = remember(session, HOUSING);
        long id = saveAndGetId(analysisId, session);
        mvc.perform(signedIn(get("/api/v1/history/" + id), session))
                .andExpect(jsonPath("$.revision").doesNotExist())
                .andExpect(jsonPath("$.answers.length()").value(0));

        ImpactResult revisedAction = new ImpactResult("LAW:001809@2026-11-01", "ACTION", "대응",
                List.of(), List.of(), List.of(new ImpactResult.Action("신고한다", "시행 전", List.of())),
                null, List.of(), "참고용", null);
        new HttpSessionRecentAnalyses(session).attachRevision(analysisId, userIdOf(session), HOUSING, SET_ID,
                ActionRevisionApiResponse.revised(revisedAction, List.of(), List.of()),
                List.of(new HttpSessionRecentAnalyses.AnsweredQuestion("계약했나요?", "예")));

        MvcResult again = mvc.perform(save(analysisId, session)).andExpect(status().isOk()).andReturn();
        assertEquals(id, savedId(again));
        mvc.perform(signedIn(get("/api/v1/history"), session)).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(signedIn(get("/api/v1/history/" + id), session))
                .andExpect(jsonPath("$.revision.action.actions[0].what").value("신고한다"))
                .andExpect(jsonPath("$.answers[0].answer").value("예"));
    }

    @Test
    @DisplayName("결과·상황 질문·재생성본·답이 채워진 스냅샷도 상세에서 그대로 복원된다(Jackson 왕복)")
    void 풍부한_스냅샷_왕복() throws Exception {
        ImpactResult summary = new ImpactResult("LAW:001809@2026-11-01", "SUMMARY", "절차가 짧아져요.",
                List.of(new ImpactResult.Claim("통합심의가 넓어진다", List.of("LAW:001809@2026-11-01:art:18"), 0.9)),
                List.of(), List.of(), new ImpactResult.EffectiveInfo("시행예정", ApiSliceTestConfig.LAW_EFFECTIVE, null),
                List.of(), "참고용", null);
        ImpactResult revisedAction = new ImpactResult("LAW:001809@2026-11-01", "ACTION", "대응",
                List.of(), List.of(), List.of(new ImpactResult.Action("신고한다", "시행 전", List.of("LAW:001809@2026-11-01:art:18"))),
                null, List.of(), "참고용", null);
        AnalysisApiResponse body = AnalysisApiResponse.analyzed(
                new AnalysisApiResponse.LawHeader("LAW:001809@2026-11-01", "001809", "주택법",
                        ApiSliceTestConfig.LAW_EFFECTIVE, "법률", "국토교통부"),
                List.of(), Map.of("summary", summary),
                Map.of("action", new Unmet(Unmet.Reason.PROFILE_REQUIRED, "프로필 필요")),
                List.of(new AnalysisApiResponse.CitedSource("LAW:001809@2026-11-01:art:18", "제18조", "원문", null, "개정",
                        ApiSliceTestConfig.LAW_EFFECTIVE)),
                "참고용",
                new AnalysisApiResponse.SituationBody("set-1",
                        List.of(new SituationQuestion("q1", "계약했나요?", "제18조", SituationQuestion.AnswerType.YES_NO, List.of())),
                        List.of(new StepNote(0, "계약 여부에 따라 달라요"))));
        HttpSessionRecentAnalyses recent = new HttpSessionRecentAnalyses(session);
        String analysisId = recent.remember(userIdOf(session), "주택법 바뀌면?", HOUSING, body);
        recent.attachRevision(analysisId, userIdOf(session), HOUSING, "set-1",
                ActionRevisionApiResponse.revised(revisedAction, List.of(new StepNote(0, "신고하세요")), List.of()),
                List.of(new HttpSessionRecentAnalyses.AnsweredQuestion("계약했나요?", "예")));

        long id = saveAndGetId(analysisId, session);

        mvc.perform(signedIn(get("/api/v1/history/" + id), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.answer.summary.claims[0].statement").value("통합심의가 넓어진다"))
                .andExpect(jsonPath("$.body.unmet.action.reason").value("PROFILE_REQUIRED"))
                .andExpect(jsonPath("$.body.sources[0].label").value("제18조"))
                .andExpect(jsonPath("$.body.situation.questions[0].answerType").value("YES_NO"))
                .andExpect(jsonPath("$.revision.action.actions[0].what").value("신고한다"))
                .andExpect(jsonPath("$.revision.stepNotes[0].note").value("신고하세요"))
                .andExpect(jsonPath("$.answers[0].question").value("계약했나요?"))
                .andExpect(jsonPath("$.answers[0].answer").value("예"));
    }

    @Test
    @DisplayName("읽을 수 없는 스냅샷은 500 이 아니라 메타데이터만 + unreadable: true, 읽히면 키가 없다")
    void 읽을_수_없는_스냅샷() throws Exception {
        UUID userId = (UUID) session.getAttribute(CurrentUser.SESSION_KEY);
        long broken = history.save(userId, new AnalysisHistoryStore.NewEntry("broken-1", "주택법 바뀌면?", "001809",
                ApiSliceTestConfig.LAW_EFFECTIVE, "주택법", "{\"body\":")).id();
        long fine = saveAndGetId(remember(session, HOUSING), session);

        mvc.perform(signedIn(get("/api/v1/history/" + broken), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(broken))
                .andExpect(jsonPath("$.query").value("주택법 바뀌면?"))
                .andExpect(jsonPath("$.lawTitle").value("주택법"))
                .andExpect(jsonPath("$.unreadable").value(true))
                .andExpect(jsonPath("$.body").doesNotExist())
                .andExpect(jsonPath("$.revision").doesNotExist())
                .andExpect(jsonPath("$.answers.length()").value(0));
        mvc.perform(signedIn(get("/api/v1/history/" + fine), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadable").doesNotExist())
                .andExpect(jsonPath("$.body.law.lawId").value("001809"));
    }

    @Test
    @DisplayName("다른 계정으로 로그인한 같은 세션이 앞 사용자의 analysisId 로 저장하려 하면 409 analysis_expired")
    void 다른_사용자의_세션_항목은_저장할_수_없다() throws Exception {
        String analysisId = remember(session, HOUSING);
        MockHttpSession sameBrowserOtherAccount = signedInSession();
        sameBrowserOtherAccount.setAttribute(HttpSessionRecentAnalyses.ATTRIBUTE,
                session.getAttribute(HttpSessionRecentAnalyses.ATTRIBUTE));

        mvc.perform(save(analysisId, sameBrowserOtherAccount))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("analysis_expired"));
    }

    @Test
    @DisplayName("세션에 없는 analysisId → 409 analysis_expired")
    void 만료_409() throws Exception {
        mvc.perform(save("없는-id", session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("analysis_expired"))
                .andExpect(jsonPath("$.message").value("저장할 결과가 만료됐어요. 다시 분석한 뒤 저장해 주세요."));
    }

    @Test
    @DisplayName("한도(2) 초과 → 409 history_limit")
    void 한도_409() throws Exception {
        saveAndGetId(remember(session, HOUSING), session);
        saveAndGetId(remember(session, HOUSING), session);

        mvc.perform(save(remember(session, HOUSING), session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("history_limit"))
                .andExpect(jsonPath("$.message").value("이력은 최대 2개까지 저장할 수 있어요."));
    }

    @Test
    @DisplayName("analysisId 없음 → 400")
    void 요청_400() throws Exception {
        mvc.perform(signedIn(post("/api/v1/history"), session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("남의 기록 상세 → 404 not_found, 삭제해도 남는다")
    void 남의_기록_404() throws Exception {
        long id = saveAndGetId(remember(session, HOUSING), session);
        MockHttpSession other = signedInSession();

        mvc.perform(signedIn(get("/api/v1/history/" + id), other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
        mvc.perform(signedIn(delete("/api/v1/history/" + id), other).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(signedIn(get("/api/v1/history/" + id), session)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE → 204, 다시 → 204, 이후 상세 404")
    void 삭제_204() throws Exception {
        long id = saveAndGetId(remember(session, HOUSING), session);

        mvc.perform(signedIn(delete("/api/v1/history/" + id), session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(signedIn(delete("/api/v1/history/" + id), session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(signedIn(get("/api/v1/history/" + id), session)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CSRF 없는 POST·DELETE → 403, 익명 → 401")
    void 인증_CSRF() throws Exception {
        mvc.perform(signedIn(post("/api/v1/history"), session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"analysisId\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(signedIn(delete("/api/v1/history/1"), session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/history")).andExpect(status().isUnauthorized());
    }
}
