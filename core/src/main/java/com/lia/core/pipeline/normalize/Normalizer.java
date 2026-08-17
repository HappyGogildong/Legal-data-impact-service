package com.lia.core.pipeline.normalize;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import com.lia.core.domain.law.Addendum;
import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Law;
import com.lia.core.observability.Obs;
import com.lia.core.pipeline.connector.LawEnvelope;
import com.lia.core.pipeline.connector.RawLaw;

/**
 * {@code RawLaw} → 표준 {@link Law} 정규화.
 *
 * <p><b>출처 API의 기벽을 여기서 끝낸다.</b> 하류(diff·색인·분석)는 {@code Law} 만 보고
 * {@code Map<String,Object>} 를 다시 열지 않는다 — Anti-Corruption Layer 의 안쪽 절반이다.
 *
 * <p>실측 함정 3가지를 흡수한다:
 * <ol>
 *   <li>{@code 조문내용} 만 읽으면 본문이 빈다 → {@code 항 → 호 → 목} 재귀 병합</li>
 *   <li>부칙은 제정 이후 이력 전체가 온다 → {@code 부칙공포번호 == 공포번호} 로 필터</li>
 *   <li>{@code 기본정보.소관부처} 등이 중첩 객체다 → 평탄화</li>
 * </ol>
 *
 * <p>설계: docs/components/Normalizer.md
 */
public class Normalizer {

    /** 해시 필드 구분자 — 필드 경계가 뭉개져 서로 다른 법령이 같은 해시가 되는 것을 막는다. */
    private static final char SEP = 31;   // US(unit separator)

    /** 부칙 덩어리를 조 단위로 쪼갠다: "제1조(시행일) …". */
    private static final Pattern ADDENDUM_CLAUSE =
            Pattern.compile("제(\\d+)조\\s*\\(([^)]*)\\)");

    /** 하위법령 위임 — 실질 영향이 시행령으로 넘어가는 지점. '정한다'와 '정하는' 은 겹치는 글자가 없어 둘 다 명시해야 한다. */
    private static final Pattern DELEGATION =
            Pattern.compile("[^.。\\n]*(?:대통령령|총리령|부령|[가-힣]+부령)으로\\s*정[하한][^.。\\n]*");

    /** 시행일 조항에서 규칙 문장만 뽑는다. */
    private static final Pattern EFFECTIVE_SENTENCE =
            Pattern.compile("(이 (?:법|영|규칙)은.*?시행한다\\.(?:\\s*다만,.*?시행한다\\.)?)", Pattern.DOTALL);

    /** 계측 레지스트리 — 미주입 시 NOOP(단위 테스트의 {@code new Normalizer()} 무영향). */
    private final ObservationRegistry observations;

    public Normalizer() {
        this(ObservationRegistry.NOOP);
    }

    public Normalizer(ObservationRegistry observations) {
        this.observations = observations == null ? ObservationRegistry.NOOP : observations;
    }

    // --- 공개 API --------------------------------------------------------

    public Law normalize(RawLaw raw) {
        if (raw == null || !raw.hasBody()) {
            throw new IllegalArgumentException("본문 없는 RawLaw 는 정규화할 수 없다 — fetchPending/fetchCurrent 결과를 넘길 것.");
        }
        return Observation.createNotStarted(Obs.NORMALIZE, observations).observe(() -> doNormalize(raw));
    }

    private Law doNormalize(RawLaw raw) {
        Map<String, Object> root = raw.raw();
        Map<String, Object> info = LawEnvelope.basicInfo(root);

        String promulgateNo = firstNonBlank(raw.promulgateNo(), LawEnvelope.str(info.get("공포번호")));
        List<Article> articles = parseArticles(root);
        List<Addendum> addenda = parseAddenda(root, promulgateNo);
        EffectiveRule rule = parseEffectiveRule(addenda);

        Law law = new Law(
                firstNonBlank(raw.lawId(), LawEnvelope.str(info.get("법령ID"))),
                firstNonBlank(raw.mst(), LawEnvelope.str(info.get("법령일련번호"))),
                firstNonBlank(raw.title(), LawEnvelope.flatString(info.get("법령명_한글"))),
                status(raw.status()),
                amendKind(LawEnvelope.flatString(info.get("제개정구분"))),
                lawType(LawEnvelope.flatString(info.get("법종구분"))),
                LawEnvelope.flatString(info.get("소관부처")),          // ⚠️ 중첩 객체 → 평탄화
                firstNonNull(raw.promulgateDate(), LawEnvelope.date(info.get("공포일자"))),
                promulgateNo,
                firstNonNull(raw.effectiveDate(), LawEnvelope.date(info.get("시행일자"))),
                rule.text(),
                rule.type(),
                LawEnvelope.flatString(root.get("제개정이유")),
                LawEnvelope.flatString(root.get("개정문")),
                addenda,
                articles,
                detectDelegations(articles),
                null,                                                  // baselineLawId — Diff Builder 가 채움
                null,
                "",                                                    // revision — 아래에서 계산
                Instant.now());

        return law.withRevision(computeRevision(law));
    }

    // --- 조문 -----------------------------------------------------------

    public List<Article> parseArticles(Map<String, Object> lawRoot) {
        List<Article> out = new ArrayList<>();
        for (Map<String, Object> a : LawEnvelope.articles(lawRoot)) {
            boolean isArticle = "조문".equals(LawEnvelope.str(a.get("조문여부")));
            boolean changed = "Y".equalsIgnoreCase(String.valueOf(a.get("조문변경여부")));
            String movedFrom = LawEnvelope.str(a.get("조문이동이전"));
            String movedTo = LawEnvelope.str(a.get("조문이동이후"));

            out.add(new Article(
                    LawEnvelope.str(a.get("조문번호")),
                    LawEnvelope.str(a.get("조문제목")),
                    mergeArticleText(a),                    // ★ 항/호/목 재귀 병합
                    changed,
                    changeType(changed, movedFrom, movedTo),
                    movedFrom,
                    movedTo,
                    LawEnvelope.date(a.get("조문시행일자")),
                    isArticle,
                    null));                                 // diffVsCurrent — Diff Builder 가 채움
        }
        return out;
    }

    /**
     * {@code 조문내용} 만 읽으면 본문이 빈다 — 실측에서 제2조(정의)의 {@code 조문내용} 은
     * 제목 줄뿐이고 실제 정의는 {@code 항 → 호 → 목} 중첩에 있었다.
     */
    private static String mergeArticleText(Map<String, Object> article) {
        StringBuilder sb = new StringBuilder();
        String head = LawEnvelope.text(article.get("조문내용"));
        if (!head.isBlank()) sb.append(head);
        String body = LawEnvelope.text(article.get("항"));
        if (!body.isBlank()) {
            if (!sb.isEmpty()) sb.append('\n');
            sb.append(body);
        }
        return sb.toString().strip();
    }

    /**
     * <b>신설·삭제는 여기서 정하지 않는다.</b> 기준선(시행중본) 없이는 알 수 없다 —
     * Diff Builder 가 조문번호 대조로 확정한다.
     */
    private static Article.ChangeType changeType(boolean changed, String movedFrom, String movedTo) {
        if (movedFrom != null || movedTo != null) return Article.ChangeType.이동;
        return changed ? Article.ChangeType.개정 : Article.ChangeType.없음;
    }

    // --- 부칙 -----------------------------------------------------------

    /**
     * 이번 개정분 부칙만 조 단위로 쪼갠다.
     * 필터하지 않으면 10년 전 경과조치를 이번 개정 내용으로 오인한다(실측 42개 중 1개).
     */
    public List<Addendum> parseAddenda(Map<String, Object> lawRoot, String promulgateNo) {
        List<Addendum> out = new ArrayList<>();
        for (Map<String, Object> block : LawEnvelope.addenda(lawRoot)) {
            String blockNo = LawEnvelope.str(block.get("부칙공포번호"));
            if (promulgateNo != null && !promulgateNo.equals(blockNo)) continue;   // ★ 이번 개정분만

            LocalDate date = LawEnvelope.date(block.get("부칙공포일자"));
            String text = LawEnvelope.text(block.get("부칙내용"));
            out.addAll(splitClauses(text, blockNo, date));
        }
        return out;
    }

    /** "제1조(시행일) … 제2조(적용례) …" 한 덩어리를 조 단위로 분해. */
    private static List<Addendum> splitClauses(String text, String promulgateNo, LocalDate date) {
        List<Addendum> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;

        Matcher m = ADDENDUM_CLAUSE.matcher(text);
        List<int[]> spans = new ArrayList<>();
        List<String[]> heads = new ArrayList<>();
        while (m.find()) {
            spans.add(new int[]{m.start(), m.end()});
            heads.add(new String[]{m.group(1), m.group(2)});
        }
        if (spans.isEmpty()) {   // 조 구분이 없는 단문 부칙 — 통째로 시행일 조항 취급
            out.add(new Addendum("제1조", "시행일", Addendum.kindOf(text), text.strip(), promulgateNo, date));
            return out;
        }
        for (int i = 0; i < spans.size(); i++) {
            int from = spans.get(i)[0];
            int to = (i + 1 < spans.size()) ? spans.get(i + 1)[0] : text.length();
            String title = heads.get(i)[1];
            out.add(new Addendum(
                    "제" + heads.get(i)[0] + "조",
                    title,
                    Addendum.kindOf(title),
                    text.substring(from, to).strip(),
                    promulgateNo,
                    date));
        }
        return out;
    }

    /** 부칙 제1조(시행일)에서 규칙 문장과 시행 유형을 뽑는다. */
    public EffectiveRule parseEffectiveRule(List<Addendum> addenda) {
        for (Addendum a : addenda) {
            if (a.kind() != Addendum.Kind.시행일) continue;
            String text = a.text();
            Matcher m = EFFECTIVE_SENTENCE.matcher(text);
            String sentence = m.find() ? m.group(1).strip() : text;
            return new EffectiveRule(sentence, enforcementType(sentence));
        }
        return new EffectiveRule(null, null);
    }

    /**
     * 단서("다만 …")가 있으면 조문마다 시행일이 갈리므로 단계적이다.
     * 실측: "공포 후 6개월이 경과한 날부터 시행한다. 다만, 제57조제2항제7호의
     * 개정규정은 공포한 날부터 시행한다" → 단계적.
     */
    private static Law.EnforcementType enforcementType(String sentence) {
        if (sentence == null || sentence.isBlank()) return null;
        if (sentence.contains("다만")) return Law.EnforcementType.단계적;
        if (sentence.contains("공포한 날부터 시행")) return Law.EnforcementType.즉시;
        return Law.EnforcementType.유예;
    }

    /** 시행 규칙 추출 결과. */
    public record EffectiveRule(String text, Law.EnforcementType type) {}

    // --- 위임조항 --------------------------------------------------------

    /** "~는 대통령령으로 정한다" — 실질 영향이 하위법령으로 넘어가는 지점. 하류에서 uncertainties 로 승계. */
    public List<String> detectDelegations(List<Article> articles) {
        List<String> out = new ArrayList<>();
        for (Article a : articles) {
            if (a.text() == null) continue;
            Matcher m = DELEGATION.matcher(a.text());
            while (m.find()) {
                out.add(a.label() + " " + m.group().strip());
            }
        }
        return out;
    }

    // --- revision --------------------------------------------------------

    /**
     * 캐시 무효화 키. <b>분석 결과에 영향을 주는 필드만</b> 해시한다 —
     * {@code sourceUrl}·소관부처 연락처 같은 행정 메타는 제외(D16).
     */
    public String computeRevision(Law law) {
        StringBuilder sb = new StringBuilder();
        sb.append(nz(law.title())).append(SEP)
          .append(nz(law.amendReason())).append(SEP)
          .append(nz(law.amendText())).append(SEP)
          .append(nz(law.effectiveRule())).append(SEP)
          .append(law.effectiveDate()).append(SEP)
          .append(nz(law.promulgateNo())).append(SEP)
          .append(nz(law.baselineLawId())).append(SEP);
        for (Article a : law.articles()) {
            sb.append(nz(a.no())).append(':')
              .append(a.changed()).append(':')
              .append(a.changeType()).append(':')
              .append(nz(a.text())).append(SEP);
        }
        return sha256Hex(sb.toString()).substring(0, 16);
    }

    private static String sha256Hex(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 미지원", e);
        }
    }

    // --- 스칼라 -----------------------------------------------------------

    private static Law.Status status(String raw) {
        return "시행예정".equals(raw) ? Law.Status.시행예정 : Law.Status.시행중;
    }

    private static Law.AmendKind amendKind(String raw) {
        if (raw == null) return Law.AmendKind.기타;
        for (Law.AmendKind k : Law.AmendKind.values()) {
            if (raw.contains(k.name())) return k;
        }
        return Law.AmendKind.기타;
    }

    private static Law.LawType lawType(String raw) {
        if (raw == null) return Law.LawType.기타;
        for (Law.LawType t : Law.LawType.values()) {
            if (raw.contains(t.name())) return t;
        }
        return Law.LawType.기타;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }

    private static <T> T firstNonNull(T a, T b) {
        return a != null ? a : b;
    }
}
