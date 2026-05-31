package com.lia.core.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lia.core.client.PipelineClient;
import com.lia.core.command.AnalysisPipeline;
import com.lia.core.command.CommandContext;
import com.lia.core.command.CommandRegistry;
import com.lia.core.domain.Bill;
import com.lia.core.domain.ImpactResult;

/** REST 진입점. MCP 도구 표면(TS)이 이 API를 호출한다. */
@RestController
@RequestMapping("/api/v1")
public class AnalysisController {

    private final AnalysisPipeline pipeline;
    private final CommandRegistry registry;
    private final PipelineClient pipelineClient;

    public AnalysisController(AnalysisPipeline pipeline, CommandRegistry registry,
                              PipelineClient pipelineClient) {
        this.pipeline = pipeline;
        this.registry = registry;
        this.pipelineClient = pipelineClient;
    }

    /** 사용 가능한 커맨드 목록 (= 노출 가능한 MCP 도구 목록). */
    @GetMapping("/commands")
    public List<String> commands() {
        return registry.names();
    }

    /** 단일 커맨드 실행. */
    @PostMapping("/analyze")
    public ImpactResult analyze(@RequestBody AnalyzeRequest req) {
        // TODO: billId 로 저장소에서 Bill 로드. PoC는 최소 Bill 구성.
        Bill bill = new Bill(req.billId(), null, req.billId(), null,
                List.of(), null, null, Bill.Stage.위원회심사, null,
                "assembly", null, null, List.of());
        CommandContext ctx = new CommandContext(bill, req.persona(), pipelineClient);
        return pipeline.run(req.command(), ctx, null);
    }

    public record AnalyzeRequest(String command, String billId, String persona) {}
}
