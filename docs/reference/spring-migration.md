---
title: Spring Boot 4.0 · Spring AI 2.0 버전 변경점 대조
status: Draft
version: 0.1
date: 2026-07-21
tags: [reference, spring, spring-ai, migration, versions]
related:
  - "adr/decision-log.md"
  - "architecture/v0.6-spring-consolidation.md"
---

# Spring Boot 4.0 · Spring AI 2.0 — 버전 변경점 대조

**관련:** [[decision-log|D35]] (파이프라인 Spring 통합) · [[v0.6-spring-consolidation|아키텍처 v0.6]]

파이프라인을 Python에서 Spring으로 통합(D35)하면서 채택하는 **Spring Boot 4.0 + Spring AI 2.0**이 기존 버전 대비 무엇이 달라졌는지 정리한다. 우리 core는 Boot **3.3.0 스텁**에서 출발하므로 사실상 신규 작성에 가깝고, 아래는 "왜 이 조합인지"와 "코드에서 주의할 것"의 근거다.

## 0. 채택 버전·전제

| 항목 | 기존(core 스텁) | 채택 |
|---|---|---|
| Spring Boot | 3.3.0 | **4.0.x** (2025-11 GA) |
| Spring Framework | 6.1.x | **7.x** |
| Spring AI | (미사용) | **2.0.x** (2026-05-28 GA) |
| Java | 21 | **21 LTS** (toolchain 고정) |

> **호환성 핵심:** Spring AI 2.0은 **Boot 4.0 전용**이다 — 3.x 컨텍스트에서 로드되지 않으므로 "Boot 3.5 + Spring AI 1.1.x" 또는 "Boot 4.0 + Spring AI 2.0" 두 조합만 유효하다. 우리는 후자.

## 1. Spring Boot 3.x → 4.0 주요 변경

| 변경 | 내용 | 우리 영향 |
|---|---|---|
| ~~Java 21 baseline~~ **정정** | **최소는 Java 17** — Boot 3.x와 동일하고 4.0에서 올라가지 않았다. 다만 공식 문서가 *최신 LTS 사용*을 권한다(4.1 기준 Java 26까지 호환) | 우리는 **Java 21 LTS 고정**. `sourceCompatibility` 가 아니라 **toolchain** 으로 JDK 자체를 고정한다 — source 레벨만 낮추면 상위 JDK API 링크를 못 막아 런타임 `NoSuchMethodError` 위험 |
| **Framework 7 + Jakarta EE 11** | 코어 세대 교체 | 신규 코드라 무풍 |
| **Jackson 2 → 3** | 패키지 `com.fasterxml.jackson` → `tools.jackson`, 날짜 직렬화·필드 순서 기본값 변경 | ⚠️ **주의 1순위** — 구조화 JSON(ImpactResult) 직렬화 코드·테스트는 처음부터 Jackson 3 기준으로 |
| **JSpecify null-safety** | `org.springframework.lang.@Nullable` 제거 → `org.jspecify.annotations` | 신규 코드에서 JSpecify 사용 |
| **모듈화** | 모듈별 `org.springframework.boot.<module>` 패키지 재배치 | starter 의존성만 쓰면 영향 적음 |
| **Deprecated 대거 제거** | 2.x·3.x deprecated 클래스 ~36종 제거 | 없음(레거시 없음) |
| **Undertow·JUnit4 제거** | 지원 종료 | 없음(Tomcat·JUnit5) |
| **Security 기본값 변경** | REST API가 조용히 깨질 수 있는 기본값 변경 | Security 도입 시 재확인 |
| **설정 프로퍼티 개명** | 일부 properties 이름 변경 — `spring-boot-properties-migrator`로 진단 가능 | 신규 작성이라 무풍 |

## 2. Spring AI 1.x → 2.0 주요 변경

| 변경 | 내용 | 우리 영향 |
|---|---|---|
| **Boot 4.0 필수** | 3.x에서 로드 불가 | 채택 조합의 이유 |
| **Jackson 3 전환** | 모델 응답 직렬화가 `tools.jackson` 기반 | Boot 4와 동일 주의 |
| **옵션 클래스 setter 제거** | `OpenAiChatOptions` 등 전부 **빌더 패턴 강제** | 신규 코드는 처음부터 빌더로 |
| **Chat Memory 변경** | `PromptChatMemoryAdvisor` 제거, memory advisor에 `conversationId` 명시 필수 | 현재 대화 메모리 미사용 — 후속 참고 |
| **MCP 어노테이션 코어 편입** | `org.springaicommunity.mcp` → `org.springframework.ai.mcp.annotation` | (내부 MCP 어댑터 시) 코어 것 사용 |
| **일부 VectorStore 제거** | SAP HANA·Infinispan 모듈 제거 | **PgVector는 유지** ✓ (우리 스택 무풍) |
| **JSpecify** | null 안전성 어노테이션 통일 | 위와 동일 |

## 3. 우리 컴포넌트 ↔ Spring AI 2.0 매핑

| LIA 컴포넌트 | Spring AI 2.0 API | 비고 |
|---|---|---|
| Embedder | `EmbeddingModel` | OpenAI starter; Upstage는 OpenAI-호환 base-url 오버라이드 |
| RAGIndexer / Vector Index | `VectorStore` (PgVectorStore) | pgvector 네이티브, 네임스페이스는 메타데이터 필터로 |
| AnalysisEngine 추론 | `ChatClient` (Anthropic) | 구조화 출력 컨버터로 ImpactResult JSON |
| SourceConnector | `RestClient` | AI 무관 — Boot 표준 HTTP |
| SourceAnalyzer | 순수 로직 포팅 | 4상태·fail-closed 그대로 |

## 4. 마이그레이션 체크리스트 (core 기준)

- [x] Boot 3.3.0 → 4.0.x 플러그인·BOM 교체 (`core/build.gradle`)
- [x] Spring AI BOM 2.0.x + starter(anthropic·openai·pgvector) 추가
- [x] Jackson 3(`tools.jackson`) 적용 — `AssemblyBillsConnector`가 `tools.jackson.databind.ObjectMapper`로 직접 파싱(열린국회가 text/html 로 응답하는 문제 때문에 컨버터 우회)
- [ ] 옵션 설정은 빌더만 사용 (`AnthropicChatOptions.builder()...`)
- [ ] Security 도입 시 4.0 기본값 검토
- [ ] Spring AI 2.0.x 패치 버전 추적 (GA 직후라 패치 잦음)

## 5. 출처

- [Spring AI 2.0 GA 발표 (VisualStudioMagazine, 2026-06-29)](https://visualstudiomagazine.com/articles/2026/06/29/spring-ai-2-0-goes-ga-giving-java-developers-a-more-mature-ai-app-stack.aspx) · [byteiota — GA 5/28 상세](https://byteiota.com/spring-ai-2-ga-java-production-stack/)
- [Spring AI Upgrade Notes (공식)](https://docs.spring.io/spring-ai/reference/upgrade-notes.html) · [spring.io 릴리스 블로그](https://spring.io/blog/2026/05/08/spring-ai-1-0-7-1-1-6-2-0-0-M6-available-now/)
- [Spring Boot 4.0 Migration Guide (공식 wiki)](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [HeroDevs — Spring AI 2.0/Boot 4.0 마이그레이션](https://www.herodevs.com/blog-posts/spring-ai-2-0-is-coming-soon-your-boot-4-0-migration-does-not-have-to-start-tomorrow) · [Boot 4 breaking changes](https://www.herodevs.com/blog-posts/spring-boot-4-0-breaking-changes-migration-guide)
- [JavaCodeGeeks — Boot 4 마이그레이션 실전](https://www.javacodegeeks.com/2026/05/spring-boot-4-migration-breaking-changes-new-defaultsand-what-actually-broke.html)
