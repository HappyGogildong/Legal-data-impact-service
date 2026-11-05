package com.lia.core.pipeline.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** 번역기·API 가 주는 조문 번호를 {@code Article.no} 형식으로 맞춘다(D82). */
class LawRefTest {

    private static LawRef refWith(String articleNo) {
        return new LawRef("001809", LocalDate.of(2026, 8, 4), articleNo);
    }

    @ParameterizedTest
    @CsvSource({
            "18, 18",
            "18조, 18",
            "제18조, 18",
            "제 18 조, 18",
            "105의2, 105의2",
            "제105조의2, 105의2",
            "제18조제1항, 18",
            "제18조의2 제3항 제1호, 18의2",
    })
    void 조문_번호를_정본_형식으로_맞춘다(String raw, String expected) {
        assertEquals(expected, refWith(raw).articleNo());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  ", "제18조, 제19조", "제18조와 제19조", "18-2", "부칙", "18개"})
    void 한_조문으로_해석되지_않으면_지목하지_않은_것으로_본다(String raw) {
        assertNull(refWith(raw).articleNo());
    }
}
