package com.lia.core.pipeline.dispatch;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.lia.core.pipeline.plan.QueryType;

/**
 * {@code QueryType → DimensionHandler} 레지스트리(§4#9) — Spring이 주입한 모든 핸들러 빈을 수집한다.
 * 새 차원 핸들러는 {@code @Component}만 붙이면 자동 등재된다(dispatcher 수정 불필요).
 *
 * <p>같은 차원 핸들러가 둘이면 <b>배선 버그</b>이므로 생성 시 즉시 실패한다(fail-fast 구성 검증).
 */
@Component
public class DimensionHandlerRegistry {

    private final Map<QueryType, DimensionHandler> byType;

    public DimensionHandlerRegistry(List<DimensionHandler> handlers) {
        Map<QueryType, DimensionHandler> m = new EnumMap<>(QueryType.class);
        for (DimensionHandler h : handlers) {
            DimensionHandler prev = m.put(h.type(), h);
            if (prev != null) {
                throw new IllegalStateException(
                        "중복 DimensionHandler: " + h.type() + " (" + prev.getClass().getSimpleName()
                                + " vs " + h.getClass().getSimpleName() + ")");
            }
        }
        this.byType = Collections.unmodifiableMap(m); // 구성 후 read-only (EnumMap 이점 유지, copyOf는 EnumMap 버림)
    }

    public Optional<DimensionHandler> get(QueryType type) {
        return Optional.ofNullable(byType.get(type));
    }
}
