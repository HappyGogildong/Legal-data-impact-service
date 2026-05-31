# core — 도메인 & 커맨드 오케스트레이션 (Java Spring)

표준 도메인 모델의 단일 진실 공급원이자, **use-case별 후처리 커맨드**의 실행 엔진.

```
src/main/java/com/lia/core/
├── LiaCoreApplication.java
├── domain/                도메인 모델 (Bill, Article, ImpactResult, Persona)
├── command/               확장성의 심장
│   ├── AnalysisCommand.java   커맨드 계약 (interface)
│   ├── CommandContext.java    대상 Bill + 페르소나 + 엔진 핸들
│   ├── Requirement.java       선행 데이터 선언 (현행법 diff 등)
│   ├── CommandRegistry.java   @Component 자동 발견
│   ├── AnalysisPipeline.java  requirements 해소 후 커맨드(체인) 실행
│   └── impl/                  ImpactSummary / PersonaImpact / ActionPlan ...
├── client/PipelineClient.java 파이썬 해석 엔진 호출 (WebClient)
└── api/AnalysisController.java REST 진입점
```

## 새 use-case 추가법

`AnalysisCommand` 를 구현하고 `@Component` 를 붙이면 끝.
`CommandRegistry` 가 자동 발견하고, MCP 도구 표면이 자동으로 노출한다.
코어 코드는 수정하지 않는다 (개방-폐쇄 원칙).
