package com.lia.core.eval;

import java.util.List;
import java.util.Set;

/**
 * Faithfulness 게이트 — <b>결정론 인용 존재성</b>(프롬프트 정의서 §6.2 승계).
 *
 * <p>RAGAS의 LLM-faithfulness와 달리, 우리는 그라운딩 규율상 이미 결정론 버전을 갖는다:
 * 모든 주장은 <b>주입된 {@code source_id}만</b> 인용해야 하고, 인용 없는 주장은 무효다(§6.1).
 * 이걸 게이트로 쓰면 환각 인용이 CI를 실패시킨다. "인용이 주장을 <i>지지</i>하는가"(§6.3)는
 * LLM-judge 보조층(RAGAS/사람) 몫이다.
 */
public final class FaithfulnessGate {

    private FaithfulnessGate() {}

    /** 주장 1건 — 진술과 그 인용키들. */
    public record Claim(String statement, List<String> citations) {}

    /** 주장이 지지되는가 — 인용이 하나 이상 있고 <b>모두</b> 주입 컨텍스트에 실재. */
    public static boolean supported(Claim claim, Set<String> injectedSourceIds) {
        return claim.citations() != null
                && !claim.citations().isEmpty()
                && injectedSourceIds.containsAll(claim.citations());
    }

    /** Faithfulness = 지지되는 주장의 비율(0~1). */
    public static double faithfulness(List<Claim> claims, Set<String> injectedSourceIds) {
        if (claims.isEmpty()) {
            return 1.0;   // 주장이 없으면 위반도 없다
        }
        long ok = claims.stream().filter(c -> supported(c, injectedSourceIds)).count();
        return (double) ok / claims.size();
    }

    /** 게이트 — 모든 주장이 지지되면 통과(하나라도 환각/무인용이면 실패). */
    public static boolean passes(List<Claim> claims, Set<String> injectedSourceIds) {
        return claims.stream().allMatch(c -> supported(c, injectedSourceIds));
    }

    /** 위반한 주장 목록(진단용). */
    public static List<Claim> violations(List<Claim> claims, Set<String> injectedSourceIds) {
        return claims.stream().filter(c -> !supported(c, injectedSourceIds)).toList();
    }
}
