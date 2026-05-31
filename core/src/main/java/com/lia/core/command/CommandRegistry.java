package com.lia.core.command;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * 모든 {@link AnalysisCommand} 빈을 자동 수집한다.
 * Spring이 @Component 로 등록된 커맨드를 생성자에 주입 → 이름으로 조회.
 * 새 커맨드를 추가해도 이 클래스는 손대지 않는다.
 */
@Component
public class CommandRegistry {

    private final Map<String, AnalysisCommand<?, ?>> byName;

    public CommandRegistry(List<AnalysisCommand<?, ?>> commands) {
        this.byName = commands.stream()
                .collect(Collectors.toMap(AnalysisCommand::name, Function.identity()));
    }

    public Optional<AnalysisCommand<?, ?>> find(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public List<String> names() {
        return List.copyOf(byName.keySet());
    }
}
