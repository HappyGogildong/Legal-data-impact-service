package com.lia.core.eval;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.lia.core.domain.law.Article;
import com.lia.core.eval.EvalCase.Kind;
import com.lia.core.pipeline.plan.QueryType;

/**
 * 커밋된 정답 세트(eval/offline-gold.json)의 형식 규칙(D83). 분모가 작으면 한 문항 허용 규칙 때문에 회귀 게이트가
 * 사실상 꺼지므로, 지표마다 최소 문항 수를 지킨다.
 */
class OfflineGoldTest {

    static final List<EvalCase> GOLD = GoldenSet.cases("eval/offline-gold.json");
    static final int MIN_PER_GROUP = 5;
    static final int MIN_PER_DIMENSION = 3;

    static long count(Predicate<EvalCase> filter) {
        return GOLD.stream().filter(filter).count();
    }

    @Test
    @DisplayName("문항 종류마다 최소 문항 수를 지킨다")
    void 종류별_최소_문항() {
        assertTrue(count(c -> c.kind() == Kind.ANSWERABLE && c.lawNamed()) >= MIN_PER_GROUP, "법령 이름을 댄 문항");
        assertTrue(count(c -> c.kind() == Kind.ANSWERABLE && !c.lawNamed()) >= MIN_PER_GROUP, "주제만 말한 문항");
        assertTrue(count(c -> c.kind() == Kind.NO_MATCH) >= MIN_PER_GROUP, "관련 법령 없음 문항");
        assertTrue(count(c -> c.kind() == Kind.REFUSE) >= MIN_PER_GROUP, "거부 문항");
        assertTrue(count(c -> c.articleNo() != null) >= MIN_PER_GROUP, "조문 지목 문항");
    }

    @ParameterizedTest
    @EnumSource(value = QueryType.class, names = {"SUMMARY", "DIFF", "IMPACT", "ACTION"})
    @DisplayName("의도 차원마다 최소 문항 수를 지킨다")
    void 차원별_최소_문항(QueryType dimension) {
        assertTrue(count(c -> c.expectedDimension() == dimension) >= MIN_PER_DIMENSION, dimension.name());
    }

    @Test
    @DisplayName("질문은 겹치지 않는다")
    void 질문_중복_없음() {
        assertEquals(GOLD.size(), new HashSet<>(GOLD.stream().map(EvalCase::query).toList()).size());
    }

    @Test
    @DisplayName("조문 번호는 비교 형식(no)으로 적는다")
    void 조문_번호_형식() {
        GOLD.stream().filter(c -> c.articleNo() != null)
                .forEach(c -> assertEquals(c.articleNo(), Article.normalizeNo(c.articleNo()), c.query()));
    }

    @Test
    @DisplayName("의도 차원은 법령을 지목한 답 있는 문항에만 둔다 — 발견 질문은 후보만 내고 차원을 정하지 않는다")
    void 차원은_지목_문항에만() {
        GOLD.stream().filter(c -> c.expectedDimension() != null)
                .forEach(c -> assertTrue(c.kind() == Kind.ANSWERABLE && c.lawNamed(), c.query()));
    }

    @Test
    @DisplayName("허용 법령은 정답 법령과 겹치지 않는다")
    void 허용_법령은_정답과_다르다() {
        GOLD.forEach(c -> c.acceptableLawIds().forEach(id ->
                assertFalse(c.expectedLawIds().contains(id), c.query())));
    }
}
