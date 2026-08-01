package com.lia.core.pipeline.resolve;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.lia.core.pipeline.connector.RawLaw;

/**
 * fail-closed 불변식이 <b>타입 수준에서</b> 강제되는지 확인한다(D23).
 *
 * <p>규율이 문서와 관례에만 있으면 언젠가 우회된다. 여기서 막는 것은
 * "확인되지 않은 입력이 분석으로 흘러가는" 경로다.
 */
class ResolutionResultTest {

    private static final RawLaw LAW = new RawLaw(
            "001809", "283191", "주택법", "시행예정",
            LocalDate.of(2026, 8, 4), LocalDate.of(2026, 2, 3), "21323", Map.of());

    @Test
    void RESOLVED인데_결과가_없으면_생성_불가() {
        var e = assertThrows(IllegalArgumentException.class, () ->
                new ResolutionResult(ResolutionState.RESOLVED, null, List.of(), List.of(), null));
        assertTrue(e.getMessage().contains("fail-closed"));
    }

    @Test
    void 미해소_상태에_결과가_딸리면_생성_불가() {
        for (ResolutionState s : List.of(ResolutionState.AMBIGUOUS,
                                         ResolutionState.NOT_FOUND_YET,
                                         ResolutionState.UNVERIFIED)) {
            assertThrows(IllegalArgumentException.class, () ->
                            new ResolutionResult(s, LAW, List.of(LAW), List.of(), "안내"),
                    s + " 상태에 해소 결과가 새어들어갔다");
        }
    }

    @Test
    void RESOLVED에_후보를_함께_둘_수_없다() {
        assertThrows(IllegalArgumentException.class, () ->
                new ResolutionResult(ResolutionState.RESOLVED, LAW, List.of(LAW), List.of(), null));
    }

    @Test
    void AMBIGUOUS인데_후보가_없으면_생성_불가() {
        assertThrows(IllegalArgumentException.class, () ->
                ResolutionResult.ambiguous(List.of(), "어느 것인가요?"));
    }

    @Test
    void 미해소_상태는_안내_문구가_필수다() {
        assertThrows(IllegalArgumentException.class, () -> ResolutionResult.notFoundYet(null));
        assertThrows(IllegalArgumentException.class, () -> ResolutionResult.unverified(List.of(), "  "));
    }

    @Test
    void 후보_목록은_생성_후_변경할_수_없다() {
        List<RawLaw> mutable = new ArrayList<>(List.of(LAW));
        var r = ResolutionResult.ambiguous(mutable, "어느 것인가요?");

        mutable.add(LAW);   // 원본을 건드려도
        assertEquals(1, r.candidates().size(), "생성 시점 스냅샷이 아니다");
        assertThrows(UnsupportedOperationException.class, () -> r.candidates().add(LAW));
    }

    @Test
    void 정상_경로는_그대로_동작한다() {
        assertTrue(ResolutionResult.resolved(LAW).analyzable());
        assertFalse(ResolutionResult.notFoundYet("미등록").analyzable());
        assertFalse(ResolutionResult.ambiguous(List.of(LAW), "후보 다수").analyzable());
        assertFalse(ResolutionResult.unverified(List.of(), "확인 불가").analyzable());
    }
}
