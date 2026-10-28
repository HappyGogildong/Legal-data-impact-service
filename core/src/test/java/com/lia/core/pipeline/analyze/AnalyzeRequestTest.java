package com.lia.core.pipeline.analyze;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.profile.HousingType;
import com.lia.core.profile.UserProfile;
import com.lia.core.testsupport.TestLaws;

/** AnalyzeRequest — 프로필은 Layer B 에만(D69). */
class AnalyzeRequestTest {

    private static final Law LAW = TestLaws.law("A", "M1", Law.Status.시행예정, LocalDate.of(2027, 1, 1));
    private static final UserProfile PROFILE =
            new UserProfile(null, 34, null, null, null, HousingType.전세, null);

    @Test
    void LayerA_는_프로필_없이_만든다() {
        AnalyzeRequest req = new AnalyzeRequest(QueryType.SUMMARY, LAW, null);
        assertNull(req.profile());
        assertNull(req.question());
    }

    @Test
    void LayerB_는_프로필이_필수다() {
        assertThrows(IllegalArgumentException.class,
                () -> new AnalyzeRequest(QueryType.IMPACT, LAW, null, null, "내 영향"));
    }

    @Test
    void LayerA_에_프로필을_넣으면_거부한다() {
        assertThrows(IllegalArgumentException.class,
                () -> new AnalyzeRequest(QueryType.SUMMARY, LAW, null, PROFILE, "요약"));
    }

    @Test
    void LayerB_는_프로필과_질문을_싣는다() {
        AnalyzeRequest req = new AnalyzeRequest(QueryType.IMPACT, LAW, null, PROFILE, "주택법 내 영향");
        assertSame(PROFILE, req.profile());
        assertEquals("주택법 내 영향", req.question());
    }
}
