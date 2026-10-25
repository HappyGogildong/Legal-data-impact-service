package com.lia.core.pipeline.normalize;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.lia.core.domain.law.Addendum;
import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.connector.LawEnvelope;
import com.lia.core.pipeline.connector.RawLaw;

/**
 * Normalizer 단위 테스트 — 각 케이스가 실측 함정 하나에 대응한다.
 * 실측 근거: 주택법 MST=283191, 시행 2026-08-04 (tools/probe_eflaw.py)
 */
class NormalizerTest {

    private final Normalizer normalizer = new Normalizer();

    // --- 실측 응답을 축약한 픽스처 ------------------------------------------

    private static Map<String, Object> lawRoot() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("기본정보", Map.of(
                "법령ID", "001809", "법령일련번호", "283191", "법령명_한글", "주택법",
                "제개정구분", "일부개정", "법종구분", "법률",
                "소관부처", Map.of("content", "국토교통부"),          // ⚠️ 중첩 객체
                "공포일자", "20260203", "공포번호", "21323", "시행일자", "20260804"));
        root.put("조문", Map.of("조문단위", List.of(
                Map.of("조문번호", "1", "조문제목", "총칙", "조문내용", "제1장 총칙",
                        "조문여부", "전문", "조문변경여부", "N"),
                // 조문내용이 제목 줄뿐이고 실제 정의는 항/호 중첩에 있는 케이스
                Map.of("조문번호", "2", "조문제목", "정의",
                        "조문내용", "제2조(정의) 이 법에서 사용하는 용어의 뜻은 다음과 같다.",
                        "항", List.of(Map.of("호", List.of(
                                Map.of("호내용", "1. \"주택\"이란 세대의 구성원이 장기간 독립된 주거생활을 할 수 있는 건축물을 말한다."),
                                Map.of("호내용", "2. \"준주택\"이란 주택 외의 건축물로서 대통령령으로 정하는 것을 말한다.")))),
                        "조문여부", "조문", "조문변경여부", "N", "조문시행일자", "20260804"),
                Map.of("조문번호", "18", "조문제목", "사업계획의 통합심의 등",
                        "조문내용", "제18조(사업계획의 통합심의 등) 통합하여 검토할 수 있다.",
                        "조문여부", "조문", "조문변경여부", "Y", "조문시행일자", "20260804"),
                Map.of("조문번호", "104", "조문제목", "벌칙",
                        "조문내용", "제104조(벌칙) 2년 이하의 징역에 처한다.",
                        "조문여부", "조문", "조문변경여부", "Y", "조문시행일자", "20260804"),
                Map.of("조문번호", "57", "조문제목", "이동된 조문",
                        "조문내용", "제57조 …", "조문여부", "조문",
                        "조문변경여부", "Y", "조문이동이전", "56"))));
        root.put("부칙", Map.of("부칙단위", List.of(
                Map.of("부칙공포번호", "13782", "부칙공포일자", "20160119",
                        "부칙내용", "제1조(시행일) 이 법은 2016년 9월 1일부터 시행한다."),
                Map.of("부칙공포번호", "21323", "부칙공포일자", "20260203",
                        "부칙내용", "부칙\n제1조(시행일) 이 법은 공포 후 6개월이 경과한 날부터 시행한다. "
                                + "다만, 제57조제2항제7호의 개정규정은 공포한 날부터 시행한다.\n"
                                + "제2조(통합심의에 관한 적용례) 제18조제1항의 개정규정은 이 법 시행 이후 "
                                + "최초로 사업계획승인을 신청하는 경우부터 적용한다."))));
        root.put("개정문", "주택법 일부를 다음과 같이 개정한다. 제18조제1항 각 호 외의 부분 중 …");
        root.put("제개정이유", "[일부개정]\n◇ 개정이유 및 주요내용\n주택건설사업 관련 심의를 효율화하여 …");
        return root;
    }

    private static RawLaw rawPending() {
        return new RawLaw("001809", "283191", "주택법", "시행예정",
                LocalDate.of(2026, 8, 4), LocalDate.of(2026, 2, 3), "21323", lawRoot());
    }

    // --- 헤더 --------------------------------------------------------------

    @Nested
    @DisplayName("헤더 매핑")
    class Header {

        @Test
        void 기본정보를_도메인_타입으로_옮긴다() {
            Law law = normalizer.normalize(rawPending());

            assertEquals("001809", law.lawId());
            assertEquals("283191", law.mst());
            assertEquals("주택법", law.title());
            assertEquals(Law.Status.시행예정, law.status());
            assertEquals(Law.AmendKind.일부개정, law.amendKind());
            assertEquals(Law.LawType.법률, law.lawType());
            assertEquals(LocalDate.of(2026, 8, 4), law.effectiveDate());
            assertEquals("21323", law.promulgateNo());
        }

        @Test
        void 중첩_객체로_오는_소관부처를_평탄화한다() {
            assertEquals("국토교통부", normalizer.normalize(rawPending()).ministry());
        }

        @Test
        void 소관부처에_부처_코드가_함께_와도_이름만_남긴다() {
            Map<String, Object> root = lawRoot();
            Map<String, Object> info = new LinkedHashMap<>(LawEnvelope.basicInfo(root));
            info.put("소관부처", Map.of("content", "국토교통부", "소관부처코드", "1613000"));
            root.put("기본정보", info);
            RawLaw raw = new RawLaw("001809", "283191", "주택법", "시행예정",
                    LocalDate.of(2026, 8, 4), LocalDate.of(2026, 2, 3), "21323", root);

            assertEquals("국토교통부", normalizer.normalize(raw).ministry());
        }

        @Test
        void 본문_없는_RawLaw는_거부한다() {
            RawLaw listOnly = new RawLaw("001809", "283191", "주택법", "시행예정",
                    LocalDate.of(2026, 8, 4), null, "21323", Map.of("법령ID", "001809"));

            assertThrows(IllegalArgumentException.class, () -> normalizer.normalize(listOnly));
        }
    }

    // --- 조문 --------------------------------------------------------------

    @Nested
    @DisplayName("조문 — 조문내용만 읽으면 본문이 빈다")
    class Articles {

        @Test
        void 항_호_목을_재귀_병합해_본문을_만든다() {
            Law law = normalizer.normalize(rawPending());
            Article a2 = law.article("2");

            assertTrue(a2.text().contains("제2조(정의)"), "머리 줄이 빠졌다");
            assertTrue(a2.text().contains("\"주택\"이란"), "항/호 중첩이 병합되지 않았다 — 본문이 빈 채로 나간다");
            assertTrue(a2.text().contains("\"준주택\"이란"));
        }

        @Test
        void 장절_제목은_실조문에서_제외된다() {
            Law law = normalizer.normalize(rawPending());

            assertEquals(5, law.articles().size(), "전개 항목까지 모두 보존해야 한다");
            assertEquals(4, law.realArticles().size(), "조문여부='조문' 만 실조문이다");
            assertEquals(1, law.articles().stream().filter(a -> !a.isArticle()).count());
            assertNull(law.article("1"), "제목 행은 조문 조회 대상이 아니다");
        }

        @Test
        void 변경_조문만_골라낸다() {
            Law law = normalizer.normalize(rawPending());

            assertEquals(List.of("18", "104", "57"),
                    law.changedArticles().stream().map(Article::no).toList());
            assertTrue(law.changedArticles().size() < law.articles().size(),
                    "변경 조문 선별이 비용 레버다 — 실측 137개 중 6개");
        }

        @Test
        void 이동_조문은_changeType이_이동이다() {
            Law law = normalizer.normalize(rawPending());

            assertEquals(Article.ChangeType.이동, law.article("57").changeType());
            assertEquals("56", law.article("57").movedFrom());
        }

        /** 조문단위 하나만 담은 법령 본문 — 조문번호·가지번호 같은 필드 단위 케이스용. */
        private List<Article> parseSingle(Map<String, Object> articleUnit) {
            Map<String, Object> root = lawRoot();
            root.put("조문", Map.of("조문단위", List.of(articleUnit)));
            return normalizer.parseArticles(root);
        }

        @Test
        void 조문가지번호가_있으면_조문번호에_의를_붙인다() {
            Article article = parseSingle(Map.of("조문번호", "105", "조문가지번호", "2",
                    "조문제목", "허가 유효기간 및 재허가", "조문여부", "조문", "조문변경여부", "Y")).get(0);

            assertEquals("105의2", article.no());
            assertEquals("제105조의2", article.label());
        }

        @Test
        void 조문가지번호가_없거나_0이면_조문번호를_그대로_둔다() {
            Map<String, Object> base = Map.of("조문번호", "105", "조문제목", "저작권위탁관리업의 허가 등",
                    "조문여부", "조문", "조문변경여부", "Y");

            assertEquals("105", parseSingle(base).get(0).no());
            for (String noBranch : List.of("0", "00", "", " ")) {
                Map<String, Object> unit = new LinkedHashMap<>(base);
                unit.put("조문가지번호", noBranch);
                assertEquals("105", parseSingle(unit).get(0).no(), "가지번호 '" + noBranch + "' 는 가지 없음이다");
                assertEquals("제105조", parseSingle(unit).get(0).label());
            }
        }

        @Test
        void 본조와_가지조는_같은_번호로_겹치지_않는다() {
            Map<String, Object> root = lawRoot();
            root.put("조문", Map.of("조문단위", List.of(
                    Map.of("조문번호", "105", "조문제목", "본조", "조문여부", "조문", "조문변경여부", "Y"),
                    Map.of("조문번호", "105", "조문가지번호", "2", "조문제목", "가지 2", "조문여부", "조문", "조문변경여부", "Y"),
                    Map.of("조문번호", "105", "조문가지번호", "4", "조문제목", "가지 4", "조문여부", "조문", "조문변경여부", "Y"))));

            List<Article> articles = normalizer.parseArticles(root);

            assertEquals(List.of("105", "105의2", "105의4"), articles.stream().map(Article::no).toList());
        }

        @Test
        void 이동_자리표시_000000은_이동으로_보지_않는다() {
            // 실측: 변경 조문이면 전부 "000000" 이 채워져 와서 모두 이동으로 오인됐다.
            Article article = parseSingle(Map.of("조문번호", "18", "조문제목", "사업계획의 통합심의 등",
                    "조문여부", "조문", "조문변경여부", "Y",
                    "조문이동이전", "000000", "조문이동이후", "000000")).get(0);

            assertEquals(Article.ChangeType.개정, article.changeType());
            assertNull(article.movedFrom());
            assertNull(article.movedTo());
        }

        @Test
        void 이동_값이_공백이거나_0으로만_이뤄져도_없는_값이다() {
            for (String placeholder : List.of("0", "000000", "", "  ")) {
                Article article = parseSingle(Map.of("조문번호", "18", "조문여부", "조문", "조문변경여부", "Y",
                        "조문이동이전", placeholder, "조문이동이후", placeholder)).get(0);

                assertEquals(Article.ChangeType.개정, article.changeType(), "'" + placeholder + "'");
                assertNull(article.movedFrom());
                assertNull(article.movedTo());
            }
        }

        @Test
        void 한쪽만_실제_이동값이면_이동이고_자리표시쪽은_null이다() {
            Article article = parseSingle(Map.of("조문번호", "57", "조문여부", "조문", "조문변경여부", "Y",
                    "조문이동이전", "005600", "조문이동이후", "000000")).get(0);

            assertEquals(Article.ChangeType.이동, article.changeType());
            assertEquals("56", article.movedFrom());
            assertNull(article.movedTo());
        }

        @Test
        void 이동_값_6자리는_조문번호_형식으로_바꾼다() {
            // 실측: 4자리 조문번호 + 2자리 가지번호 — 000703 은 제7조의3, 005600 은 제56조.
            Article article = parseSingle(Map.of("조문번호", "57", "조문여부", "조문", "조문변경여부", "Y",
                    "조문이동이전", "000703", "조문이동이후", "005600")).get(0);

            assertEquals(Article.ChangeType.이동, article.changeType());
            assertEquals("7의3", article.movedFrom());
            assertEquals("56", article.movedTo());
            assertEquals("제7조의3", Article.labelOf(article.movedFrom()));
        }

        @Test
        void 이동_값이_6자리가_아니면_그대로_둔다() {
            Article article = parseSingle(Map.of("조문번호", "57", "조문여부", "조문", "조문변경여부", "Y",
                    "조문이동이전", "56", "조문이동이후", "7의3")).get(0);

            assertEquals("56", article.movedFrom());
            assertEquals("7의3", article.movedTo());
        }

        @Test
        void 이동_값이_바뀌면_revision이_바뀐다() {
            // 이미 저장된 행(원값 000703)이 재적재에서 다시 쓰이려면 revision 에 이동 값이 들어가야 한다.
            // 이동 여부(changeType)·본문은 같고 이동 값만 다르다.
            Map<String, Object> fromSeventhThird = Map.of("조문번호", "57", "조문여부", "조문", "조문변경여부", "Y",
                    "조문이동이전", "000703");
            Map<String, Object> fromSeventhFourth = Map.of("조문번호", "57", "조문여부", "조문", "조문변경여부", "Y",
                    "조문이동이전", "000704");

            assertNotEquals(revisionOf(fromSeventhThird), revisionOf(fromSeventhFourth));
        }

        private String revisionOf(Map<String, Object> articleUnit) {
            Map<String, Object> root = lawRoot();
            root.put("조문", Map.of("조문단위", List.of(articleUnit)));
            RawLaw raw = new RawLaw("001809", "283191", "주택법", "시행예정",
                    LocalDate.of(2026, 8, 4), LocalDate.of(2026, 2, 3), "21323", root);
            return normalizer.normalize(raw).revision();
        }

        @Test
        void 신설_삭제는_판정하지_않는다() {
            // 기준선 없이는 알 수 없다 — Diff Builder 가 조문번호 대조로 확정한다.
            Law law = normalizer.normalize(rawPending());

            assertEquals(Article.ChangeType.개정, law.article("18").changeType());
            assertEquals(Article.ChangeType.없음, law.article("2").changeType());
        }
    }

    // --- 부칙 --------------------------------------------------------------

    @Nested
    @DisplayName("부칙 — 이력 전체가 오므로 공포번호로 걸러야 한다")
    class Addenda {

        @Test
        void 이번_개정분만_남긴다() {
            Law law = normalizer.normalize(rawPending());

            assertEquals(2, law.addenda().size(), "2016년 부칙이 섞이면 옛 경과조치를 이번 개정으로 오인한다");
            assertTrue(law.addenda().stream().allMatch(a -> "21323".equals(a.promulgateNo())));
        }

        @Test
        void 조_단위로_쪼개고_종류를_분류한다() {
            Law law = normalizer.normalize(rawPending());

            assertEquals("제1조", law.addenda().get(0).no());
            assertEquals(Addendum.Kind.시행일, law.addenda().get(0).kind());
            assertEquals("제2조", law.addenda().get(1).no());
            assertEquals(Addendum.Kind.적용례, law.addenda().get(1).kind());
        }

        @Test
        void 조_구분이_없는_단문_부칙도_시행일_조항이다() {
            // 실측: 통상환경변화 대응 및 지원 등에 관한 법률 시행령 MST=291359, 시행 2026-10-22.
            // 조항이 하나뿐인 부칙은 "제1조(시행일)" 머리 없이 시행 문장만 온다.
            Map<String, Object> root = Map.of("부칙", Map.of("부칙단위", List.of(
                    Map.of("부칙공포번호", "36775", "부칙공포일자", "20261020",
                            "부칙내용", "부칙 <제36775호,2026.10.20>\n이 영은 2026년 10월 22일부터 시행한다."))));

            List<Addendum> addenda = normalizer.parseAddenda(root, "36775");

            assertEquals(1, addenda.size());
            assertEquals(Addendum.Kind.시행일, addenda.get(0).kind(),
                    "본문엔 '시행일'이란 낱말이 없다 — 본문으로 분류하면 기타가 된다");
            var rule = normalizer.parseEffectiveRule(addenda);
            assertEquals("이 영은 2026년 10월 22일부터 시행한다.", rule.text());
            assertEquals(Law.EnforcementType.유예, rule.type());
        }

        @Test
        void 시행일_조항에서_시행규칙을_뽑는다() {
            Law law = normalizer.normalize(rawPending());

            assertNotNull(law.effectiveRule());
            assertTrue(law.effectiveRule().contains("공포 후 6개월이 경과한 날"),
                    "실제 규칙: " + law.effectiveRule());
            assertNotNull(law.effectiveClause());
        }

        @Test
        void 단서조항이_있으면_단계적_시행이다() {
            // "다만, …개정규정은 공포한 날부터 시행한다" → 조문마다 시행일이 갈린다
            assertEquals(Law.EnforcementType.단계적, normalizer.normalize(rawPending()).enforcementType());
        }

        @Test
        void 단서가_없으면_유예_또는_즉시다() {
            var immediate = normalizer.parseEffectiveRule(List.of(new Addendum(
                    "제1조", "시행일", Addendum.Kind.시행일,
                    "제1조(시행일) 이 법은 공포한 날부터 시행한다.", "1", null)));
            assertEquals(Law.EnforcementType.즉시, immediate.type());

            var deferred = normalizer.parseEffectiveRule(List.of(new Addendum(
                    "제1조", "시행일", Addendum.Kind.시행일,
                    "제1조(시행일) 이 법은 2027년 1월 1일부터 시행한다.", "1", null)));
            assertEquals(Law.EnforcementType.유예, deferred.type());
        }
    }

    // --- 위임조항 · revision ------------------------------------------------

    @Nested
    @DisplayName("위임조항과 revision")
    class Derived {

        @Test
        void 하위법령_위임을_감지한다() {
            Law law = normalizer.normalize(rawPending());

            assertFalse(law.delegationClauses().isEmpty(),
                    "'대통령령으로 정하는' 이 잡히지 않으면 uncertainties 표기가 누락된다");
            assertTrue(law.delegationClauses().get(0).contains("제2조"));
        }

        @Test
        void revision은_본문이_같으면_같다() {
            assertEquals(normalizer.normalize(rawPending()).revision(),
                    normalizer.normalize(rawPending()).revision());
        }

        @Test
        void 조문_본문이_바뀌면_revision이_바뀐다() {
            Law before = normalizer.normalize(rawPending());

            Map<String, Object> root = new LinkedHashMap<>(lawRoot());
            root.put("조문", Map.of("조문단위", List.of(
                    Map.of("조문번호", "18", "조문내용", "제18조 완전히 다른 내용",
                            "조문여부", "조문", "조문변경여부", "Y"))));
            Law after = normalizer.normalize(new RawLaw("001809", "283191", "주택법", "시행예정",
                    LocalDate.of(2026, 8, 4), LocalDate.of(2026, 2, 3), "21323", root));

            assertNotEquals(before.revision(), after.revision());
        }

        @Test
        void revision은_16자_해시다() {
            assertEquals(16, normalizer.normalize(rawPending()).revision().length());
        }
    }

    // --- 도메인 불변식·질의 --------------------------------------------------

    @Nested
    @DisplayName("Law 도메인")
    class Domain {

        @Test
        void 인용키는_시행일을_포함한다() {
            // 같은 법령ID에 시행예정본이 복수일 수 있다(D43)
            Law law = normalizer.normalize(rawPending());

            assertEquals("LAW:001809@2026-08-04", law.ref());
            assertEquals("LAW:001809@2026-08-04:art:18", law.sourceId(law.article("18")));
        }

        @Test
        void 시행중본은_인용키에_시행일이_없다() {
            RawLaw current = new RawLaw("001809", "276995", "주택법", "시행중",
                    LocalDate.of(2026, 7, 1), LocalDate.of(2026, 3, 5), "21447", lawRoot());

            assertEquals("LAW:001809", normalizer.normalize(current).ref());
        }

        @Test
        void 법령ID와_시행일은_필수다() {
            assertThrows(IllegalArgumentException.class, () -> new Law(
                    null, "1", "x", Law.Status.시행예정, Law.AmendKind.일부개정, Law.LawType.법률,
                    null, null, "1", LocalDate.now(), null, null, null, null,
                    List.of(), List.of(), List.of(), null, null, "r", null));

            assertThrows(IllegalArgumentException.class, () -> new Law(
                    "001809", "1", "x", Law.Status.시행예정, Law.AmendKind.일부개정, Law.LawType.법률,
                    null, null, "1", null, null, null, null, null,
                    List.of(), List.of(), List.of(), null, null, "r", null));
        }

        @Test
        void 전문은_실조문만_병합한다() {
            Law law = normalizer.normalize(rawPending());

            assertFalse(law.fullText().contains("제1장 총칙"), "장 제목이 전문에 섞였다");
            assertTrue(law.fullText().contains("제18조"));
        }
    }
}
