package com.lia.core.pipeline.normalize;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.lia.core.config.LiaSourceProperties;
import com.lia.core.domain.law.Addendum;
import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.connector.LawConnector;
import com.lia.core.pipeline.connector.RawLaw;

/**
 * 실 API 응답으로 정규화까지 태우는 스모크 (이슈 #5).
 *
 * <p>단위 테스트 픽스처는 우리가 만든 것이라 "우리가 이해한 형식"만 검증한다.
 * 여기서는 <b>실제 국가법령정보 응답</b>이 그대로 도메인 모델로 떨어지는지 본다.
 */
@SpringBootTest
class NormalizerLiveSmokeTest {

    @Autowired LawConnector connector;
    @Autowired Normalizer normalizer;
    @Autowired LiaSourceProperties props;

    boolean hasOc() {
        String oc = props.law().oc();
        return oc != null && !oc.isBlank();
    }

    private static final LocalDate FROM = LocalDate.now().plusDays(1);
    private static final LocalDate TO = FROM.plusYears(1);

    @Test
    @EnabledIf("hasOc")
    void 실제_시행예정_법령이_도메인_모델로_정규화된다() {
        RawLaw head = connector.listPending(FROM, TO, 1).get(0);
        RawLaw raw = connector.fetchPending(head.mst(), head.effectiveDate());

        Law law = normalizer.normalize(raw);

        System.out.printf("[live] %s (%s) — 시행 %s%n", law.title(), law.ref(), law.effectiveDate());
        System.out.printf("        조문 %d개(실조문 %d, 변경 %d) · 부칙 %d개 · 위임 %d건 · revision %s%n",
                law.articles().size(), law.realArticles().size(), law.changedArticles().size(),
                law.addenda().size(), law.delegationClauses().size(), law.revision());
        System.out.printf("        시행규칙: %s (%s)%n", law.effectiveRule(), law.enforcementType());

        // 헤더
        assertEquals(head.lawId(), law.lawId());
        assertEquals(Law.Status.시행예정, law.status());
        assertTrue(law.notYetEffective(LocalDate.now()), "시행예정인데 시행일이 과거다");
        assertNotNull(law.ministry(), "소관부처 평탄화 실패 — 중첩 객체가 그대로 새고 있다");
        assertNotEquals(Law.LawType.기타, law.lawType(), "법종구분 매핑 실패");

        // 조문 — 실제 응답에서 본문이 비지 않아야 한다
        assertFalse(law.realArticles().isEmpty(), "실조문이 하나도 없다");
        List<Article> empty = law.realArticles().stream()
                .filter(a -> a.text() == null || a.text().isBlank()).toList();
        assertTrue(empty.isEmpty(),
                "본문이 빈 조문이 있다 — 항/호/목 병합 누락: "
                        + empty.stream().map(Article::label).limit(5).toList());

        // 부칙 — 이번 개정분만
        assertFalse(law.addenda().isEmpty(), "이번 개정 부칙을 못 찾았다 — 공포번호 필터 확인");
        assertTrue(law.addenda().stream().allMatch(a -> law.promulgateNo().equals(a.promulgateNo())),
                "다른 개정의 부칙이 섞였다");

        // 시행 규칙 — 부칙 제1조에서 나와야 한다
        Addendum clause = law.effectiveClause();
        assertNotNull(clause, "시행일 조항 분류 실패");
        assertNotNull(law.effectiveRule(), "effectiveRule 추출 실패 — ActionPlan 기한 산출 불가");
        assertNotNull(law.enforcementType());

        // 인용키 — 시행일 포함(D43)
        assertTrue(law.ref().contains("@" + law.effectiveDate()), "인용키에 시행일이 빠졌다");

        // revision — 재계산해도 동일
        assertEquals(law.revision(), normalizer.normalize(raw).revision());
    }

    @Test
    @EnabledIf("hasOc")
    void 시행중_기준선도_같은_경로로_정규화된다() {
        RawLaw pending = connector.listPending(FROM, TO, 1).get(0);
        Law current = normalizer.normalize(connector.fetchCurrent(pending.lawId()));

        assertEquals(Law.Status.시행중, current.status());
        assertEquals(pending.lawId(), current.lawId(), "법령ID 연결이 깨졌다");
        assertFalse(current.realArticles().isEmpty());
        assertFalse(current.ref().contains("@"), "시행중본 인용키에는 시행일이 붙지 않는다");

        System.out.printf("[live] 기준선 %s — 조문 %d개, revision %s%n",
                current.title(), current.realArticles().size(), current.revision());
    }
}
