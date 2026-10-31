package com.lia.core.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.analysis.SituationQuestion;
import com.lia.core.domain.analysis.StepNote;
import com.lia.core.pipeline.dispatch.Unmet;
import com.lia.core.web.HistoryApiDtos.HistorySnapshot;

import tools.jackson.core.JacksonException;

/**
 * 이력 스냅샷(D73) 읽기·쓰기 — 전용 관대한 매퍼(Boot 매퍼와 무관). 옛 행이 DTO 변경 뒤에도 읽혀야 한다.
 * {@code history/snapshot-v1.json} 은 버전 1 DTO 로 만든 고정 파일 — DTO 를 바꿔 이 파일을 못 읽게 되면 이 테스트가 실패한다.
 */
class HistorySnapshotsTest {

    static final LocalDate EFFECTIVE = LocalDate.of(2026, 11, 1);

    /** 지금 DTO 로 만드는 풍부한 스냅샷 — 결과·상황 질문·미충족·근거·재생성본·답이 다 있다. */
    static HistorySnapshot rich() {
        ImpactResult summary = new ImpactResult("LAW:001809@2026-11-01", "SUMMARY", "절차가 짧아져요.",
                List.of(new ImpactResult.Claim("통합심의가 넓어진다", List.of("LAW:001809@2026-11-01:art:18"), 0.9)),
                List.of(), List.of(), new ImpactResult.EffectiveInfo("시행예정", EFFECTIVE, null),
                List.of("시행령 위임 내용은 확정되지 않았어요."), "참고용", null);
        ImpactResult revisedAction = new ImpactResult("LAW:001809@2026-11-01", "ACTION", "대응",
                List.of(), List.of(),
                List.of(new ImpactResult.Action("신고한다", "시행 전", List.of("LAW:001809@2026-11-01:art:18"))),
                null, List.of(), "참고용", null);
        AnalysisApiResponse body = AnalysisApiResponse.analyzed(
                new AnalysisApiResponse.LawHeader("LAW:001809@2026-11-01", "001809", "주택법", EFFECTIVE, "법률", "국토교통부"),
                List.of(new AnalysisApiResponse.LawBrief("001809", "주택법", LocalDate.of(2027, 2, 1))),
                Map.of("summary", summary),
                Map.of("action", new Unmet(Unmet.Reason.PROFILE_REQUIRED, "프로필 필요")),
                List.of(new AnalysisApiResponse.CitedSource("LAW:001809@2026-11-01:art:18", "제18조", "원문", "이전 원문",
                        "개정", EFFECTIVE)),
                "참고용",
                new AnalysisApiResponse.SituationBody("set-1",
                        List.of(new SituationQuestion("q1", "계약했나요?", "제18조", SituationQuestion.AnswerType.YES_NO, List.of())),
                        List.of(new StepNote(0, "계약 여부에 따라 달라요"))));
        return HistorySnapshots.snapshot(body,
                ActionRevisionApiResponse.revised(revisedAction, List.of(new StepNote(0, "신고하세요")), List.of()),
                List.of(new HttpSessionRecentAnalyses.AnsweredQuestion("계약했나요?", "예")));
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = HistorySnapshotsTest.class.getResourceAsStream(name)) {
            assertNotNull(in, name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void 새로_쓰는_스냅샷에는_version_1_이_들어간다() {
        String json = HistorySnapshots.write(rich());

        assertTrue(json.contains("\"version\":1"), json);
        assertEquals(1, HistorySnapshots.read(json).version());
    }

    @Test
    void 지금_DTO_로_쓴_스냅샷은_그대로_다시_읽힌다() {
        HistorySnapshot written = rich();

        assertEquals(written, HistorySnapshots.read(HistorySnapshots.write(written)));
    }

    @Test
    void 고정_파일_snapshot_v1_은_지금도_읽힌다() throws IOException {
        HistorySnapshot snapshot = HistorySnapshots.read(resource("/history/snapshot-v1.json"));

        assertEquals(1, snapshot.version());
        assertEquals("001809", snapshot.body().law().lawId());
        assertEquals("주택법", snapshot.body().law().title());
        assertEquals(EFFECTIVE, snapshot.body().law().effectiveDate());
        assertEquals("절차가 짧아져요.", snapshot.body().answer().get("summary").summary());
        assertEquals("통합심의가 넓어진다", snapshot.body().answer().get("summary").claims().getFirst().statement());
        assertEquals(List.of("LAW:001809@2026-11-01:art:18"),
                snapshot.body().answer().get("summary").claims().getFirst().citations());
        assertEquals("제18조", snapshot.body().sources().getFirst().label());
        assertEquals("이전 원문", snapshot.body().sources().getFirst().previousText());
        assertEquals(Unmet.Reason.PROFILE_REQUIRED, snapshot.body().unmet().get("action").reason());
        assertEquals(SituationQuestion.AnswerType.YES_NO, snapshot.body().situation().questions().getFirst().answerType());
        assertEquals("신고한다", snapshot.revision().action().actions().getFirst().what());
        assertEquals("신고하세요", snapshot.revision().stepNotes().getFirst().note());
        assertEquals(List.of(new HttpSessionRecentAnalyses.AnsweredQuestion("계약했나요?", "예")), snapshot.answers());
    }

    @Test
    void 알_수_없는_필드가_있어도_읽힌다() {
        HistorySnapshot snapshot = HistorySnapshots.read("""
                {"version":1,"newTopLevel":{"a":1},
                 "body":{"resolution":"RESOLVED","futureField":[1,2],
                         "law":{"lawId":"001809","title":"주택법","extra":"x"}},
                 "answers":[{"question":"q","answer":"예","note":"새 필드"}]}
                """);

        assertEquals("RESOLVED", snapshot.body().resolution());
        assertEquals("주택법", snapshot.body().law().title());
        assertEquals("예", snapshot.answers().getFirst().answer());
    }

    @Test
    void 모르는_enum_값은_null_로_읽힌다() {
        HistorySnapshot snapshot = HistorySnapshots.read("""
                {"version":1,"body":{"resolution":"RESOLVED",
                  "unmet":{"action":{"reason":"SOME_FUTURE_REASON","message":"m"}},
                  "situation":{"setId":"s","questions":[{"id":"q1","text":"t","answerType":"SLIDER"}]}},
                 "answers":[]}
                """);

        assertNull(snapshot.body().unmet().get("action").reason());
        assertEquals("m", snapshot.body().unmet().get("action").message());
        assertNull(snapshot.body().situation().questions().getFirst().answerType());
    }

    @Test
    void primitive_자리의_null_은_기본값으로_읽힌다() {
        HistorySnapshot snapshot = HistorySnapshots.read("""
                {"version":1,"body":{"resolution":"RESOLVED",
                  "answer":{"summary":{"summary":"s","claims":[{"statement":"x","citations":[],"confidence":null}]}},
                  "discovery":{"candidates":[],"profileApplied":null}},
                 "answers":[]}
                """);

        assertEquals(0.0, snapshot.body().answer().get("summary").claims().getFirst().confidence());
        assertEquals(false, snapshot.body().discovery().profileApplied());
    }

    @Test
    void version_이_없는_옛_스냅샷은_1_로_본다() {
        assertEquals(1, HistorySnapshots.read("{\"body\":{\"resolution\":\"RESOLVED\"},\"answers\":[]}").version());
    }

    @Test
    void JSON_이_깨졌으면_JacksonException() {
        assertThrows(JacksonException.class, () -> HistorySnapshots.read("{\"body\":"));
        assertThrows(JacksonException.class, () -> HistorySnapshots.read("[1,2]"));
    }
}
