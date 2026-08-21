package com.lia.core.pipeline.connector;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 국가법령정보(law.go.kr DRF) 응답 봉투 파싱 — 순수 함수(단위 테스트 대상).
 *
 * <p>목록: {@code {"LawSearch": {"law": [...] | {...}, "totalCnt": n, ...}}}<br>
 * 본문: {@code {"법령": {기본정보, 조문, 부칙, 개정문, 제개정이유, 법령키}}}<br>
 * 오류: HTTP 200 + {@code {"result": "사용자 정보 검증에 실패...", "msg": "..."}}
 *
 * <p><b>실측 함정(2026-08-01, tools/probe_eflaw.py):</b>
 * <ol>
 *   <li>{@code display=1} 이면 {@code law} 가 배열이 아니라 <b>단일 객체</b>로 온다.</li>
 *   <li>오류도 HTTP 200 으로 오고 {@code LawSearch} 키가 없다 — 상태코드로 판별 불가.</li>
 *   <li>{@code 조문내용} 만 읽으면 본문이 빈다. 실제 내용은 {@code 항 → 호 → 목} 중첩.</li>
 *   <li>{@code 기본정보.소관부처} 등 일부 필드가 문자열이 아니라 중첩 객체다.</li>
 *   <li>{@code 부칙} 은 이력 전체가 온다. 이번 개정분은 공포번호로 걸러야 한다.</li>
 * </ol>
 */
public final class LawEnvelope {

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("uuuuMMdd");

    private LawEnvelope() {}

    // --- 오류 판별 -------------------------------------------------------

    /**
     * 오류 응답이면 예외. 국가법령정보는 인증 실패도 HTTP 200 으로 돌려주므로
     * 본문에 {@code result} 키가 있는지로 판별한다.
     */
    public static void checkError(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            throw new LawApiException("빈 응답");
        }
        Object result = payload.get("result");
        if (result != null && !payload.containsKey("LawSearch") && !payload.containsKey("법령")) {
            throw new LawApiException(str(result) + " / " + str(payload.get("msg")));
        }
    }

    // --- 목록 ------------------------------------------------------------

    /** {@code LawSearch.law} 목록 추출. 단건이면 객체로 오므로 리스트로 감싼다(함정 1). */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> extractRows(Map<String, Object> payload) {
        Object search = payload.get("LawSearch");
        if (!(search instanceof Map<?, ?> body)) return List.of();
        Object law = ((Map<String, Object>) body).get("law");
        if (law instanceof List<?> list) {
            List<Map<String, Object>> rows = new ArrayList<>(list.size());
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) rows.add((Map<String, Object>) m);
            }
            return rows;
        }
        if (law instanceof Map<?, ?> single) return List.of((Map<String, Object>) single);
        return List.of();
    }

    /** 전체 건수(페이징 종료 판정용). 없으면 -1. */
    public static int totalCount(Map<String, Object> payload) {
        Object search = payload.get("LawSearch");
        if (search instanceof Map<?, ?> body) {
            return intOf(((Map<?, ?>) body).get("totalCnt"), -1);
        }
        return -1;
    }

    // --- 본문 ------------------------------------------------------------

    /**
     * 본문에 {@code 법령} 블록이 있는가. 없으면 <b>현행본 없음</b>을 뜻한다 —
     * 제정(신규) 법령을 {@code target=law} 로 조회하면 국가법령정보가
     * {@code {"Law": "일치하는 법령이 없습니다..."}} 를 돌려주기 때문이다(영문 루트).
     * fetchCurrent 는 이걸로 "diff 기준선 부재"를 판별한다(도메인 상태이지 오류가 아님).
     */
    public static boolean hasLawBody(Map<String, Object> payload) {
        return payload != null && payload.get("법령") instanceof Map;
    }

    /** 본문 응답의 {@code 법령} 블록. 없으면 예외(현행본 부재 판별은 {@link #hasLawBody}). */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> lawRoot(Map<String, Object> payload) {
        Object root = payload.get("법령");
        if (root instanceof Map<?, ?> m) return (Map<String, Object>) m;
        throw new LawApiException("본문 응답에 '법령' 블록이 없다: " + payload.keySet());
    }

    /** {@code 법령.기본정보}. 없으면 빈 맵. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> basicInfo(Map<String, Object> lawRoot) {
        Object bi = lawRoot.get("기본정보");
        return bi instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    /** {@code 법령.조문.조문단위[]}. 단건이면 감싸서 반환. */
    public static List<Map<String, Object>> articles(Map<String, Object> lawRoot) {
        return nestedList(lawRoot, "조문", "조문단위");
    }

    /**
     * {@code 법령.부칙.부칙단위[]} — <b>이력 전체</b>(실측 42개). 단건이면 감싸서 반환.
     *
     * <p>"이번 개정의 부칙만 고른다"는 <b>도메인 규칙이지 파싱이 아니므로</b> 여기서 하지 않는다.
     * 필터는 {@code Normalizer.parseAddenda} 가 {@code 부칙공포번호}로 수행하고,
     * 그 결과가 {@code Law.addenda} 다.
     */
    public static List<Map<String, Object>> addenda(Map<String, Object> lawRoot) {
        return nestedList(lawRoot, "부칙", "부칙단위");
    }

    // --- 텍스트 평탄화 ---------------------------------------------------

    /**
     * 중첩 구조(항/호/목, 소관부처 객체 등)를 텍스트로 재귀 병합(함정 3·4).
     * {@code 조문내용} 만 읽으면 제목 줄만 나오는 문제를 여기서 흡수한다.
     */
    public static String text(Object node) {
        StringBuilder sb = new StringBuilder();
        flatten(node, sb);
        return sb.toString().strip();
    }

    private static void flatten(Object node, StringBuilder sb) {
        switch (node) {
            case null -> { }
            case String s -> append(sb, s);
            case Number n -> append(sb, n.toString());
            case List<?> list -> list.forEach(o -> flatten(o, sb));
            case Map<?, ?> map -> map.values().forEach(o -> flatten(o, sb));
            default -> append(sb, node.toString());
        }
    }

    private static void append(StringBuilder sb, String s) {
        String t = s.strip();
        if (t.isEmpty()) return;
        if (!sb.isEmpty()) sb.append('\n');
        sb.append(t);
    }

    // --- 스칼라 헬퍼 -----------------------------------------------------

    /** 중첩 객체로 올 수 있는 필드를 문자열로(함정 4). 비어 있으면 null. */
    public static String flatString(Object node) {
        String t = text(node);
        return t.isEmpty() ? null : t;
    }

    /** {@code "20260804"} → LocalDate. 형식이 아니면 null(예외 대신 관대하게). */
    public static LocalDate date(Object value) {
        String s = str(value);
        if (s == null) return null;
        s = s.replaceAll("[^0-9]", "");
        if (s.length() != 8) return null;
        try {
            return LocalDate.parse(s, YMD);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static String str(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value).strip();
        return s.isEmpty() ? null : s;
    }

    private static int intOf(Object value, int fallback) {
        String s = str(value);
        if (s == null) return fallback;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> nestedList(Map<String, Object> root, String outer, String inner) {
        Object block = root.get(outer);
        if (!(block instanceof Map<?, ?> m)) return List.of();
        Object items = ((Map<String, Object>) m).get(inner);
        if (items instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>(list.size());
            for (Object o : list) {
                if (o instanceof Map<?, ?> im) out.add((Map<String, Object>) im);
            }
            return out;
        }
        if (items instanceof Map<?, ?> single) {
            return List.of(new LinkedHashMap<>((Map<String, Object>) single));
        }
        return List.of();
    }
}
