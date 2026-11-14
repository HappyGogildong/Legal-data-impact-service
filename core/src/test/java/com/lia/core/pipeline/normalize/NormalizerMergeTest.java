package com.lia.core.pipeline.normalize;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.lia.core.domain.law.Addendum;
import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.connector.RawLaw;

/**
 * 같은 (법령ID, 시행일)의 공포본 병합(D85). 실측: 운영 dry-run 952행 중 48키 — 본문은 같고 변경 표시·개정문·
 * 제개정이유·부칙이 공포본마다 다르다(평생교육법 000851@2026-12-03: MST 286437 타법개정 · 286439 일부개정).
 */
class NormalizerMergeTest {

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final Normalizer normalizer = new Normalizer();

    private static Map<String, Object> article(String no, String title, String text, Set<String> changed) {
        return Map.of("조문번호", no, "조문제목", title, "조문내용", text, "조문여부", "조문",
                "조문변경여부", changed.contains(no) ? "Y" : "N", "조문시행일자", "20261203");
    }

    private static RawLaw promulgation(String mst, LocalDate promulgated, String no, String kind, Set<String> changed,
                                       String amendText, String reason, String article18) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("기본정보", Map.of(
                "법령ID", "000851", "법령일련번호", mst, "법령명_한글", "평생교육법",
                "제개정구분", kind, "법종구분", "법률", "소관부처", Map.of("content", "교육부"),
                "공포일자", promulgated.format(YMD), "공포번호", no, "시행일자", "20261203"));
        root.put("조문", Map.of("조문단위", List.of(
                article("1", "목적", "제1조(목적) 이 법은 평생교육을 진흥함을 목적으로 한다.", changed),
                article("18", "평생교육사", article18, changed),
                article("24", "위임", "제24조(위임) 세부 사항은 대통령령으로 정한다.", changed))));
        root.put("부칙", Map.of("부칙단위", List.of(Map.of(
                "부칙공포번호", no, "부칙공포일자", promulgated.format(YMD),
                "부칙내용", "부칙\n제1조(시행일) 이 법은 2026년 12월 3일부터 시행한다.\n제2조(경과조치) 종전의 규정에 따른다."))));
        root.put("개정문", amendText);
        root.put("제개정이유", reason);
        return new RawLaw("000851", mst, "평생교육법", "시행예정", LocalDate.of(2026, 12, 3), promulgated, no, root);
    }

    private static final String ARTICLE_18 = "제18조(평생교육사) 평생교육사의 자격을 정한다.";

    /** 타법개정 — 먼저 공포(같은 날 번호가 작다), 변경 표시가 여기에만 붙는다(실측 모양). */
    private static RawLaw byOtherLaw() {
        return promulgation("286437", LocalDate.of(2026, 6, 2), "21720", "타법개정", Set.of("18"),
                "평생교육법 일부를 다음과 같이 개정한다. 제18조 중 …", "[타법개정] 정부조직 개편", ARTICLE_18);
    }

    /** 일부개정 — 같은 날 나중 번호, 변경 표시 없음. */
    private static RawLaw partial() {
        return promulgation("286439", LocalDate.of(2026, 6, 2), "21721", "일부개정", Set.of(),
                "평생교육법 일부를 다음과 같이 개정한다. 제24조 중 …", "[일부개정] 위임 근거 정비", ARTICLE_18);
    }

    @Test
    void 공포본이_하나면_normalize와_같다() {
        Law single = normalizer.normalizeAll(List.of(byOtherLaw()));

        Law expected = normalizer.normalize(byOtherLaw());
        assertEquals(expected.revision(), single.revision());
        assertEquals(expected.mst(), single.mst());
        assertEquals(expected.amendText(), single.amendText());
        assertEquals(expected.addenda(), single.addenda());
    }

    @Test
    void 변경_표시는_공포본의_합집합이다() {
        RawLaw also24 = promulgation("286439", LocalDate.of(2026, 6, 2), "21721", "일부개정", Set.of("24"),
                "개정문", "이유", ARTICLE_18);

        Law merged = normalizer.normalizeAll(List.of(byOtherLaw(), also24));

        assertEquals(List.of("18", "24"), merged.changedArticles().stream().map(Article::no).toList());
    }

    @Test
    void 변경_표시가_없는_공포본을_먼저_줘도_바뀐_조문이_남는다() {
        Law merged = normalizer.normalizeAll(List.of(partial(), byOtherLaw()));

        assertEquals(List.of("18"), merged.changedArticles().stream().map(Article::no).toList());
    }

    @Test
    void 개정문과_제개정이유는_공포_순으로_머리말을_붙여_잇는다() {
        Law merged = normalizer.normalizeAll(List.of(partial(), byOtherLaw()));

        assertEquals("[2026-06-02 공포 제21720호 타법개정]\n평생교육법 일부를 다음과 같이 개정한다. 제18조 중 …\n\n"
                + "[2026-06-02 공포 제21721호 일부개정]\n평생교육법 일부를 다음과 같이 개정한다. 제24조 중 …",
                merged.amendText());
        assertEquals("[2026-06-02 공포 제21720호 타법개정]\n[타법개정] 정부조직 개편\n\n"
                + "[2026-06-02 공포 제21721호 일부개정]\n[일부개정] 위임 근거 정비", merged.amendReason());
    }

    @Test
    void 공포_순서는_공포일이_먼저고_같은_날이면_공포번호_숫자_순이다() {
        RawLaw earlierDayBiggerNo = promulgation("280111", LocalDate.of(2025, 12, 2), "99999", "일부개정", Set.of(),
                "먼저", "먼저", ARTICLE_18);
        RawLaw laterDaySmallerNo = promulgation("284089", LocalDate.of(2026, 3, 5), "100000", "타법개정", Set.of(),
                "나중", "나중", ARTICLE_18);

        Law merged = normalizer.normalizeAll(List.of(laterDaySmallerNo, earlierDayBiggerNo));

        assertTrue(merged.amendText().startsWith("[2025-12-02 공포 제99999호 일부개정]\n먼저"), merged.amendText());
    }

    @Test
    void 헤더는_마지막_공포본이고_mst는_오름차순_쉼표_목록이다() {
        RawLaw older = promulgation("91326", LocalDate.of(2007, 12, 31), "8826", "일부개정", Set.of(), "a", "a", ARTICLE_18);
        RawLaw newer = promulgation("251531", LocalDate.of(2023, 12, 31), "19929", "타법개정", Set.of(), "b", "b", ARTICLE_18);

        Law merged = normalizer.normalizeAll(List.of(newer, older));

        assertEquals("91326,251531", merged.mst(), "문자열이 아니라 숫자 순");
        assertEquals("19929", merged.promulgateNo());
        assertEquals(LocalDate.of(2023, 12, 31), merged.promulgateDate());
        assertEquals(Law.AmendKind.타법개정, merged.amendKind());
    }

    @Test
    void 부칙은_공포_순으로_모두_모으고_번호에_공포번호를_붙인다() {
        Law merged = normalizer.normalizeAll(List.of(partial(), byOtherLaw()));

        assertEquals(List.of("제21720호-제1조", "제21720호-제2조", "제21721호-제1조", "제21721호-제2조"),
                merged.addenda().stream().map(Addendum::no).toList());
        assertEquals(4, merged.addenda().stream().map(merged::addendumSourceId).distinct().count(), "인용키가 겹치지 않는다");
        assertEquals(List.of("21720", "21720", "21721", "21721"),
                merged.addenda().stream().map(Addendum::promulgateNo).toList());
    }

    @Test
    void 위임조항은_중복_없이_모은다() {
        Law merged = normalizer.normalizeAll(List.of(byOtherLaw(), partial()));

        assertEquals(normalizer.normalize(byOtherLaw()).delegationClauses(), merged.delegationClauses());
    }

    @Test
    void revision은_병합_결과로_계산하고_입력_순서와_무관하다() {
        Law forward = normalizer.normalizeAll(List.of(byOtherLaw(), partial()));
        Law backward = normalizer.normalizeAll(List.of(partial(), byOtherLaw()));

        assertEquals(forward.revision(), backward.revision());
        assertEquals(normalizer.computeRevision(forward), forward.revision());
        assertNotEquals(normalizer.normalize(byOtherLaw()).revision(), forward.revision());
    }

    @Test
    void 조문_본문이_다르면_추측해_고르지_않는다() {
        RawLaw differentText = promulgation("286439", LocalDate.of(2026, 6, 2), "21721", "일부개정", Set.of(),
                "개정문", "이유", "제18조(평생교육사) 다른 본문.");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> normalizer.normalizeAll(List.of(byOtherLaw(), differentText)));
        assertTrue(e.getMessage().contains("000851"), e.getMessage());
    }

    @Test
    void 법령ID나_시행일이_다른_공포본은_병합하지_않는다() {
        RawLaw otherDay = new RawLaw("000851", "300000", "평생교육법", "시행예정", LocalDate.of(2027, 1, 1),
                LocalDate.of(2026, 6, 2), "21722", partial().raw());

        assertThrows(IllegalArgumentException.class, () -> normalizer.normalizeAll(List.of(byOtherLaw(), otherDay)));
        assertThrows(IllegalArgumentException.class, () -> normalizer.normalizeAll(List.of()));
    }
}
