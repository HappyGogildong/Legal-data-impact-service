---
title: 비용 정책 — 한도·차감·모델·재시도·배치 유료 단계
status: Draft
version: 0.1
date: 2026-10-29
tags: [backend, cost, policy]
related: ["adr/decision-log.md", "backend/observability.md", "components/batch/IngestJob.md"]
---

# 비용 정책

비용이 드는 결정은 이 문서에 모은다 — 항목마다 설정 키·기본값·결정·바꾸는 법. 새 기능이 비용 결정을 만들면 여기에 추가한다. 결정의 배경은 [[decision-log]], 호출·토큰 계측은 [[observability]], 배치 단계는 [[IngestJob]].

## 정책 표

| 항목 | 정책 | 설정 키(env) · 기본값 | 결정 |
|---|---|---|---|
| 분석·요약 모델 | Opus 5.5, adaptive thinking + effort medium | `SpringAiReasoner.MODEL`(코드) · max-tokens `spring.ai.anthropic.chat.options.max-tokens` 16000 | D68 |
| 질문 분류 | Haiku 4.5, 분석 요청마다 1회 | 코드 | D46 |
| 임베딩 | OpenAI, 색인·법령 발견 검색어 | `spring.ai.openai.embedding.*` | D32·D70 |
| 개인화 하루 한도 | 사용자별·서울 날짜, **첫 Layer B 1회 + 상황 재생성 1회씩**, 호출 직전 차감·환불 없음 | `lia.personalization.daily-limit`(`LIA_PERSONALIZATION_DAILY_LIMIT`) 20 | D69·D71 |
| 인용 검증 실패 시 재생성 | 분석 최대 3회 시도(코드 상수 `ANALYZE_MAX_ATTEMPTS` — 설정값화는 후속) · 배치 요약 `lia.batch.…max-attempts` 3 | — | D69 |
| 쉬운 요약 선계산 | 정기 배치 기본 꺼짐, `summarize` 명령은 늘 처리(`--dry-run` 으로 규모 먼저) | `LIA_BATCH_SUMMARY_ENABLED` false | D67 |
| 색인 임베딩 | 정기 배치에서 처리, 끄면 큐에 남김 | `LIA_BATCH_INDEX_ENABLED` true | D62 |
| 법령 발견 | Haiku 1 + 임베딩 1, Opus·한도·조회 집계 없음 | `lia.discovery.*` | D70 |
| 저장된 쉬운 요약 재사용 | 비용 0(유효할 때) | — | D67 |

### 바꾸는 법

- **설정 키가 있는 항목**은 환경변수(또는 `application.yml`)만 바꾸고 재시작한다. 개인화 하루 한도는 첫 Layer B 와 상황 재생성이 같은 한도를 공유하므로, 재생성이 너무 빨리 한도를 소진하면 이 값을 올린다.
- **"코드"로 표시된 항목**(모델·분류 모델·재시도 횟수)은 코드 상수라 바꾸면 결과가 달라진다 — 변경은 결정 기록(D번호)과 프롬프트·평가 확인을 함께 한다(D68).
- 새 기능이 호출을 늘리면 위 표에 한 줄 추가하고, 한도를 공유할지(예: D71) 따로 둘지를 그 행에 적는다.

## 운영 원칙

- **로컬은 동작 확인만** — 전체 법령 임베딩·요약 일괄 적재는 클라우드 배포 후에 한다. 로컬 큐에는 INDEX·SUMMARIZE 가 READY 로 남아 있어도 된다.
- **유료 라이브 테스트는 옵트인 환경변수** — 기본 테스트는 가짜 구현만 쓰고, 실제 Opus·임베딩을 부르는 스모크는 명시적으로 켠 때만(예: `LIA_ANALYZE_LIVE=1`).
- **대략 1회 비용(참고치)** — Opus 분석 약 $0.1~0.3 · 쉬운 요약 1건 약 $0.09(로컬 22건 약 $2 실측). 상황 재생성도 Opus 1회라 첫 Layer B 와 같은 규모로 본다.

## 미정·후속

- **요청 빈도 제한** — 입력 공격 일괄 작업에서 함께 다룬다(현재는 개인화 하루 한도만 있음).
- **온라인 재시도 설정** — Spring AI 기본 재시도가 프록시 120초를 넘길 수 있다. 재시도 횟수·간격을 설정으로 노출하고 프록시 제한과 맞춘다.
- **`ANALYZE_MAX_ATTEMPTS` 설정값화** — 지금은 코드 상수(3).
