package com.lia.core.pipeline.connector;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * LawEnvelope 단위 테스트 — 각 케이스가 실측으로 확인된 함정 하나에 대응한다.
 * 실측 근거: tools/probe_eflaw.py (2026-08-01), docs/components/SourceConnector.md
 */
class LawEnvelopeTest {

    @Nested
    @DisplayName("오류 판별 — 인증 실패도 HTTP 200 으로 온다")
    class ErrorDetection {

        @Test
        void 사용자검증_실패_응답을_예외로_바꾼다() {
            Map<String, Object> payload = Map.of(
                    "result", "사용자 정보 검증에 실패하였습니다.",
                    "msg", "OPEN API 호출 시 사용자 검증을 위하여...");

            LawApiException e = assertThrows(LawApiException.class, () -> LawEnvelope.checkError(payload));
            assertTrue(e.getMessage().contains("사용자 정보 검증에 실패"));
        }

        @Test
        void 정상_목록_응답은_통과한다() {
            assertDoesNotThrow(() -> LawEnvelope.checkError(Map.of("LawSearch", Map.of("law", List.of()))));
        }

        @Test
        void 빈_응답은_예외다() {
            assertThrows(LawApiException.class, () -> LawEnvelope.checkError(Map.of()));
        }
    }

    @Nested
    @DisplayName("목록 파싱 — display=1 이면 배열이 아니라 객체로 온다")
    class ListParsing {

        @Test
        void 단건이면_객체로_오는데_리스트로_감싼다() {
            Map<String, Object> payload = Map.of("LawSearch", Map.of(
                    "law", Map.of("법령ID", "001809", "법령명한글", "주택법"),
                    "totalCnt", "899"));

            List<Map<String, Object>> rows = LawEnvelope.extractRows(payload);

            assertEquals(1, rows.size(), "단일 객체 응답이 누락됐다 — display=1 함정");
            assertEquals("001809", rows.get(0).get("법령ID"));
            assertEquals(899, LawEnvelope.totalCount(payload));
        }

        @Test
        void 복수건은_그대로_리스트다() {
            Map<String, Object> payload = Map.of("LawSearch", Map.of(
                    "law", List.of(Map.of("법령ID", "001809"), Map.of("법령ID", "000243"))));

            assertEquals(2, LawEnvelope.extractRows(payload).size());
        }

        @Test
        void 결과가_없으면_빈_리스트다() {
            assertTrue(LawEnvelope.extractRows(Map.of("LawSearch", Map.of())).isEmpty());
            assertTrue(LawEnvelope.extractRows(Map.of()).isEmpty());
        }
    }

    @Nested
    @DisplayName("본문 파싱 — 조문·부칙·변경 플래그")
    class BodyParsing {

        private Map<String, Object> lawRoot() {
            return Map.of(
                    "기본정보", Map.of("법령ID", "001809", "법령명_한글", "주택법",
                            "공포번호", "21323", "공포일자", "20260203", "시행일자", "20260804"),
                    "조문", Map.of("조문단위", List.of(
                            Map.of("조문번호", "1", "조문변경여부", "N", "조문여부", "조문"),
                            Map.of("조문번호", "18", "조문변경여부", "Y", "조문여부", "조문"),
                            Map.of("조문번호", "104", "조문변경여부", "Y", "조문여부", "조문"))),
                    "부칙", Map.of("부칙단위", List.of(
                            Map.of("부칙공포번호", "13782", "부칙내용", "2016년 부칙"),
                            Map.of("부칙공포번호", "21323", "부칙내용", "제1조(시행일) 공포 후 6개월..."))));
        }

        @Test
        void 조문단위를_그대로_꺼낸다() {
            // 변경 조문 선별은 도메인 규칙이라 Law.changedArticles() 가 담당한다(파싱 아님).
            assertEquals(3, LawEnvelope.articles(lawRoot()).size());
        }

        @Test
        void 부칙은_이력_전체를_그대로_준다() {
            // 이번 개정분 필터도 도메인 규칙이라 Normalizer.parseAddenda 가 담당한다.
            List<Map<String, Object>> all = LawEnvelope.addenda(lawRoot());

            assertEquals(2, all.size(), "부칙 이력이 유실됐다");
            assertTrue(LawEnvelope.text(all.get(1).get("부칙내용")).contains("공포 후 6개월"));
        }

        @Test
        void 법령_블록이_없으면_예외다() {
            assertThrows(LawApiException.class, () -> LawEnvelope.lawRoot(Map.of("LawSearch", Map.of())));
        }

        @Test
        @DisplayName("현행본 없음 봉투(제정)는 법령블록이 없다고 판별한다")
        void 현행본_없음_봉투를_판별한다() {
            // 제정 법령을 target=law 로 조회하면 오는 영문 루트 에러 봉투(실측)
            Map<String, Object> noMatch = Map.of(
                    "Law", "일치하는 법령이 없습니다. 법령명을 확인하여 주십시오");
            assertFalse(LawEnvelope.hasLawBody(noMatch), "현행본 없음(제정)을 diff 기준선 부재로 판별해야 한다");
            assertTrue(LawEnvelope.hasLawBody(lawRoot() == null ? Map.of() : Map.of("법령", lawRoot())),
                    "정상 본문은 법령 블록이 있다");
            assertFalse(LawEnvelope.hasLawBody(null));
        }
    }

    @Nested
    @DisplayName("텍스트 평탄화 — 조문내용만 읽으면 본문이 빈다")
    class Flatten {

        @Test
        void 항_호_목_중첩을_재귀_병합한다() {
            Map<String, Object> article = Map.of(
                    "조문내용", "제2조(정의) 이 법에서 사용하는 용어의 뜻은 다음과 같다.",
                    "항", List.of(Map.of("호", List.of(
                            Map.of("호내용", "1. \"주택\"이란 세대의 구성원이..."),
                            Map.of("호내용", "2. \"준주택\"이란...")))));

            String text = LawEnvelope.text(article);

            assertTrue(text.contains("제2조(정의)"));
            assertTrue(text.contains("\"주택\"이란"), "항/호 중첩이 병합되지 않았다");
            assertTrue(text.contains("\"준주택\"이란"));
        }

        @Test
        void 중첩_객체로_오는_필드를_문자열로_만든다() {
            assertEquals("국토교통부", LawEnvelope.flatString(Map.of("content", "국토교통부")));
            assertNull(LawEnvelope.flatString(Map.of()), "빈 값은 null 이어야 한다");
            assertNull(LawEnvelope.flatString(null));
        }

        @Test
        void 빈_문자열은_결과에_섞이지_않는다() {
            assertEquals("가\n나", LawEnvelope.text(List.of("가", "", "  ", "나")));
        }
    }

    @Nested
    @DisplayName("스칼라 변환")
    class Scalars {

        @Test
        void 날짜는_yyyyMMdd_문자열에서_변환된다() {
            assertEquals(LocalDate.of(2026, 8, 4), LawEnvelope.date("20260804"));
            assertEquals(LocalDate.of(2026, 8, 4), LawEnvelope.date("2026.08.04"));
        }

        @Test
        void 날짜가_아니면_예외_대신_null이다() {
            assertNull(LawEnvelope.date(""));
            assertNull(LawEnvelope.date(null));
            assertNull(LawEnvelope.date("미정"));
        }

        @Test
        void 빈_문자열_필드는_null로_정규화된다() {
            assertNull(LawEnvelope.str("   "), "법령약칭명 등 빈 필드가 빈 문자열로 새면 안 된다");
            assertEquals("주택법", LawEnvelope.str(" 주택법 "));
        }
    }
}
