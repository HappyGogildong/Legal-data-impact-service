package com.lia.core.command;

import java.util.Set;

/**
 * use-case별 후처리 커맨드의 계약.
 *
 * <p>수집된 Bill은 원자재일 뿐이고, 가치는 특정 use-case로 후처리할 때 생긴다.
 * 새 use-case = 이 인터페이스 구현체 한 개 추가 + {@code @Component} 등록.
 * 코어는 수정하지 않는다 (개방-폐쇄 원칙).
 *
 * @param <P> 파라미터 타입
 * @param <R> 결과 타입
 */
public interface AnalysisCommand<P, R> {

    /** 도구/엔드포인트 식별자. MCP 도구명과 매핑된다. */
    String name();

    /** 이 컨텍스트에서 실행 가능한가 (예: 페르소나 필요 여부). */
    boolean supports(CommandContext ctx);

    /** 실행 전 채워져 있어야 할 선행 데이터. 오케스트레이터가 해소한다. */
    default Set<Requirement> requirements() {
        return Set.of();
    }

    R execute(CommandContext ctx, P params);
}
