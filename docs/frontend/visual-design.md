---
title: 시각 디자인 — 방향 A(신뢰·공공형)와 화면 시안
status: Draft
version: 0.1
date: 2026-10-23
tags: [frontend, design, ui, visual, mockup]
related: ["product/screen-plan.md", "frontend/frontend-stack.md", "adr/decision-log.md"]
---

# 시각 디자인

> [[screen-plan|화면 계획]]의 화면에 입힐 시각 방향과 화면 시안. 구현 기술: [[frontend-stack]](Tailwind CSS + shadcn/ui). **시안: [`mockups/index.html`](mockups/index.html)** — S01~S16 전 화면, 단독 HTML(공통 스타일 `mockups/lia-mock.css`).

## 1. 방향 — A. 신뢰·공공형

세 방향(신뢰·공공형 / 친근·대화형 / 미니멀·정보형)을 같은 홈 화면으로 비교해 **A**를 골랐다. 법령 서비스다운 무게감과 신뢰, 분명한 정보 계층. 딱딱함은 쉬운 말투와 넉넉한 여백으로 덜어낸다.

## 2. 디자인 토큰

| 토큰 | 값 | 용도 |
|---|---|---|
| `navy` | `#16325c` | 주 색 — 상단 바·주 버튼·활성 상태 |
| `ink` | `#1a2b45` | 본문 글자 |
| `muted` / `faint` | `#5f6b82` / `#9aa6b8` | 보조 글자(읽어야 하는 글자 — 흰 바탕 5.3:1 · tint 4.9:1) / 장식·비활성 전용 — 읽어야 하는 글자엔 쓰지 않는다 |
| `line` / `line-soft` | `#d6dfec` / `#e6ebf2` | 테두리 / 구분선 |
| `bg` | `#f5f7fa` | 데스크톱 바탕 |
| `chip` / `tint` | `#e8eef8` / `#f1f5fb` | 칩·배지 바탕 / 안내 영역 |
| `amber` | `#b45309` on `#fef3c7` | **개정**·주의·달라짐 |
| `green` | `#15803d` on `#dcfce7` | **신설**·완료 |
| `red` | `#b91c1c` on `#fee2e2` | **삭제**·위험·철회 |
| `mark` | `#fde68a` | 조문 대비에서 바뀐 구간 강조 |

- 글꼴 **Pretendard**(대체 Apple SD Gothic Neo·맑은 고딕). 제목 굵게, 본문 줄 간격 1.55.
- 모서리 — 카드 10px, 인용·배지 5px, 칩 999px, 모바일 프레임 시트 16px.
- 보조 글자도 명도 대비 4.5:1 이상(WCAG AA) — faint 는 읽어야 하는 글자에 쓰지 않는다.
- 변경 배지의 의미 색(개정 주황·신설 초록·삭제 빨강)은 **글자와 함께** 쓴다 — 색만으로 의미를 전하지 않는다(접근성).

## 3. 레이아웃 규칙

| 규칙 | 내용 |
|---|---|
| **평면 패널** | 카드·패널은 **그림자 없이 1px 테두리**로만 구분. 두꺼운 강조선(4px 상단·좌측 선)도 쓰지 않는다 |
| **모바일 결과 화면** | **접이식 카드** — 요약만 펼치고 나머지는 제목·상태(조문 수·로그인 필요)만. 미충족 상태가 한눈에 보인다 |
| **데스크톱 결과 화면** | **2단** — 본문(접이식 카드를 넓게, 요약·변경점 기본 펼침) + 오른쪽 고정 패널(법령 정보·시행일·알림·저장·후속 질문) |
| **인용 → 근거 패널** | 데스크톱은 인용 칩을 누르면 오른쪽 패널이 **근거 조문**으로 바뀌고, 그 인용이 뒷받침하는 본문 문장을 옅게 강조. "← 법령 정보로"·✕로 복귀. 모바일은 같은 내용이 **아래 시트**로 |
| **조문 대비** | 데스크톱은 현행·개정 좌우, 모바일은 위아래. 현행은 취소선, 바뀐 구간은 `mark` |
| **반응형 경계** | 모바일 < 768px(한 열·하단 탭) · 768~1023px(한 열 넓게, 상단 바) · ≥ 1024px(2단, 본문 최대 폭 1120px) |
| **내비게이션** | 모바일 하단 탭 홈·알림·내 정보, 데스크톱 상단 바(작은 질문창 + 알림·내 정보) (1단계 구성은 [[frontend-stack]] §5) |
| **포커스** | 입력·버튼은 키보드 포커스가 보이게 — 입력은 흰 입력 틀 **안쪽**에 2px navy 링(focus-within, inset)이라 어느 바탕에서도 보인다. outline 만 지우고 대체 표시 없이 두지 않는다. navy 바탕 위의 링크·버튼은 흰 outline(focus-visible, 2px, offset 2px) |

## 4. 시안 목록

| 시안 | 담은 것 |
|---|---|
| [S01 홈](mockups/S01-home.html) | 익명·로그인/프로필 상태별, 데스크톱 |
| [S02 분석 결과](mockups/S02-result.html) | 접이식 카드, 근거 조문 시트, 데스크톱 2단 기본·인용 상태 |
| [S02b 대응 방안 상황 확인](mockups/S02b-action.html) | 구조화 질문 카드(D71), 달라진 단계 강조 — 자유 입력 → "이렇게 이해했어요"는 다음 단계 |
| [S03 해소 안내](mockups/S03-resolution.html) | 모호함·미등록·확인 불가 |
| [S04 법령 발견](mockups/S04-discovery.html) | 후보 카드·프로필 기준 토글·결과 없음 |
| [S05 법령 상세](mockups/S05-law-detail.html) | 시행일 선택·조문 대비·상태 배너, 데스크톱 |
| [S06 법령 목록](mockups/S06-law-list.html) | 곧 시행·자주 찾는 |
| [S07 로그인](mockups/S07-login.html) · [S08 동의](mockups/S08-consent.html) · [S09 프로필](mockups/S09-profile.html) · [S10 내 정보](mockups/S10-my.html) | 로그인·동의·재동의·14세 미만·프로필·계정 삭제·알림 이메일 동의 |
| [S11 알림함](mockups/S11-notifications.html) · [S12 구독](mockups/S12-subscriptions.html) · [S13 이력](mockups/S13-history.html) · [S14 북마크](mockups/S14-bookmarks.html) | 알림·구독 시트·저장 당시 기준·상태 표시 |
| [S15 안내](mockups/S15-info.html) · [S16 오류](mockups/S16-errors.html) | 데이터 현황·처리방침·404·오류·세션 만료·점검 |

- 시안은 **저충실도**다 — 배치·정보 계층·상태를 확인하는 용도이고, 실제 부품은 shadcn/ui로 구현하며 토큰을 Tailwind 테마로 옮긴다.
- 법령명·날짜·수치는 모두 예시 데이터(기준일 2026-10-23).
