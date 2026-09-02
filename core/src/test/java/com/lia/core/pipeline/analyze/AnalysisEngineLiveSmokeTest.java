package com.lia.core.pipeline.analyze;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.law.Addendum;
import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Article.ChangeType;
import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.plan.QueryType;

/**
 * 실 Opus 해석 스모크 — <b>비용 발생</b>. 기본 {@code ./gradlew test}에서 절대 안 돈다:
 * 명시적 옵트인 {@code LIA_ANALYZE_LIVE=1}(그때 {@code ANTHROPIC_API_KEY} 필요). 사용자가 직접:
 * <pre>LIA_ANALYZE_LIVE=1 ./gradlew test --tests "*AnalysisEngineLiveSmokeTest"</pre>
 *
 * <p>단위는 FakeReasoner로 오케스트레이션만 본다. 여기서는 실 Opus가 <b>주어진 근거만으로 그라운딩을
 * 지키는 SUMMARY</b>를 3회 이내에 생성하는지 실측한다(못 지키면 근거부족 폴백=예외).
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "LIA_ANALYZE_LIVE", matches = "(?i)(1|true|yes)")
class AnalysisEngineLiveSmokeTest {

    @Autowired AnalysisEngine engine;

    @Test
    void summary_실생성이_주입_source_id만_인용한다() {
        AnalyzeResponse resp = engine.analyze(new AnalyzeRequest(QueryType.SUMMARY, housing(), null));
        ImpactResult r = resp.result();

        System.out.printf("[live] claims=%d summary=%s%n", r.claims().size(),
                r.summary() == null ? "" : r.summary().substring(0, Math.min(40, r.summary().length())));

        assertFalse(r.claims().isEmpty(), "주장이 없다");
        for (ImpactResult.Claim c : r.claims()) {
            assertFalse(c.citations().isEmpty(), "무인용 주장");
            assertTrue(resp.injectedSourceIds().containsAll(c.citations()),
                    "환각 인용(주입 안 된 source_id): " + c.citations());
        }
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
