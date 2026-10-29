package com.lia.core.pipeline.analyze;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.lia.core.domain.analysis.ConfirmedFact;
import com.lia.core.domain.analysis.SituationAnswer;
import com.lia.core.domain.analysis.SituationQuestion;
import com.lia.core.domain.law.Addendum;
import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Article.ChangeType;
import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.profile.HousingType;
import com.lia.core.profile.UserProfile;

/**
 * 대응 방안 상황 확인(D71) 실 Opus 스모크 — <b>비용 발생</b>(Layer B 1회 + 대응 방안 재생성 1회).
 * 기본 {@code ./gradlew test}에서 절대 안 돈다: 명시적 옵트인 {@code LIA_SITUATION_LIVE=1}(그때 {@code ANTHROPIC_API_KEY} 필요).
 * 사용자가 직접:
 * <pre>LIA_SITUATION_LIVE=1 ./gradlew test --tests "*ActionRevisionLiveSmokeTest"</pre>
 *
 * <p>단위는 FakeReasoner 로 오케스트레이션만 본다. 여기서는 실 Opus 가 (1) Layer B 에서 상황 질문을 근거 조문과 함께 내고,
 * (2) 그 질문에 답한 사실로 대응 방안을 다시 만들면 비지 않은 대응이 나오는지 실측한다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "LIA_SITUATION_LIVE", matches = "(?i)(1|true|yes)")
class ActionRevisionLiveSmokeTest {

    @Autowired AnalysisEngine engine;

    @Test
    void layerB_상황_질문을_내고_답하면_대응_방안을_다시_만든다() {
        Law law = housing();
        UserProfile profile = new UserProfile(null, 34, null, null, null, HousingType.전세, null);
        AnalyzeResponse first = engine.analyze(new AnalyzeRequest(QueryType.IMPACT, law, null,
                profile, "주택법 바뀌면 전세 사는 나는 뭘 해야 해?"));

        List<SituationQuestion> questions = first.situation().questions();
        questions.forEach(q -> System.out.printf("[live] 질문 %s (%s, 근거 %s): %s %s%n",
                q.id(), q.answerType(), q.basis(), q.text(), q.options()));
        questions.forEach(q -> assertTrue(first.injectedSourceIds().contains(q.basis()),
                "환각 근거(주입 안 된 source_id): " + q.basis()));
        if (questions.isEmpty()) {
            System.out.println("[live] 이 법령·프로필엔 상황 질문이 없다 — 재생성은 건너뛴다");
            return;
        }

        SituationQuestion asked = questions.get(0);
        List<ConfirmedFact> facts = ConfirmedFact.confirm(questions,
                List.of(new SituationAnswer(asked.id(), answerFor(asked))));
        AnalyzeResponse revised = engine.analyze(new AnalyzeRequest(QueryType.ACTION, law, null, profile, null, facts));

        System.out.printf("[live] 재생성 actions=%d stepNotes=%d%n",
                revised.result().actions().size(), revised.situation().stepNotes().size());
        assertFalse(revised.result().actions().isEmpty(), "재생성된 대응이 없다");
        revised.result().actions().forEach(a ->
                assertTrue(revised.injectedSourceIds().containsAll(a.basis()), "환각 근거: " + a.basis()));
    }

    /** 질문 형식에 맞는 답 — 첫 질문이 예/아니오가 아닐 수도 있다. */
    private static String answerFor(SituationQuestion question) {
        return switch (question.answerType()) {
            case YES_NO -> SituationQuestion.YES;
            case DATE -> "2026-10-01";
            case CHOICE -> question.options().get(0);
        };
    }

    private static Law housing() {
        List<Article> arts = List.of(
                new Article("18", "사업계획의 통합심의 등",
                        "제18조(사업계획의 통합심의 등) 관계 심의를 통합하여 검토할 수 있다.",
                        true, ChangeType.개정, null, null, LocalDate.of(2026, 8, 4), true, null),
                new Article("104", "벌칙", "제104조(벌칙) 2년 이하의 징역 또는 2천만원 이하의 벌금에 처한다.",
                        true, ChangeType.개정, null, null, LocalDate.of(2026, 8, 4), true, null));
        List<Addendum> addenda = List.of(
                new Addendum("제1조", "시행일", Addendum.Kind.시행일,
                        "이 법은 공포 후 6개월이 경과한 날부터 시행한다.", "21323", LocalDate.of(2026, 2, 3)));
        return new Law("001809", "283191", "주택법", Law.Status.시행예정,
                Law.AmendKind.일부개정, Law.LawType.법률, "국토교통부",
                LocalDate.of(2026, 2, 3), "21323", LocalDate.of(2026, 8, 4),
                "공포 후 6개월", Law.EnforcementType.유예,
                "[일부개정] 주택건설사업 심의를 효율화한다.", "주택법 일부를 다음과 같이 개정한다. 제18조…",
                addenda, arts, List.of(), null, null, "rev1", Instant.now());
    }
}
