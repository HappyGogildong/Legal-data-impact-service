---
title: AccountUseCase — 계정·알림 이메일 동의 유스케이스 (application 계층)
status: Draft
version: 0.1
date: 2026-09-27
tags: [component, application, usecase, account, consent]
related: ["components/auth/Auth.md", "components/web/AccountApi.md", "components/web/ConsentApi.md", "components/application/ProfileUseCase.md", "adr/decision-log.md"]
---

# AccountUseCase (application 계층)

> `accounts` 한 행을 둘러싼 규칙 — 계정 조회·삭제(파기), **알림 이메일 수신 동의**와 철회(D61). 관련: [[Auth]](계정 모델·store) · [[AccountApi]]·[[ConsentApi]](web) · [[ProfileUseCase]](프로필 쪽 짝).

## 계층·경계
```
web                          application        auth(store)
AccountController ─┐
ConsentController ─┴──→  AccountUseCase  ──→  AccountStore
```
- **담당**: 알림 동의 전제조건(이메일이 있어야 함) · 계정 삭제 위임.
- **담당 안 함**: 이메일을 **어디서** 얻는지(세션 principal → web이 `OAuth2Identity`로 추출해 넘긴다) · 세션 무효화(HTTP, web) · SQL([[Auth|AccountStore]]).

## 구조 — `com.lia.core.application.account`
| 클래스 | 역할 |
|---|---|
| `AccountUseCase` | 아래 Contract. Spring 애노테이션 없음 — `config/UserConfig`의 `@Bean`. |

## Contract
| 메서드 | 전제 → 보장 | 실패 |
|---|---|---|
| `find(userId)` | → `Optional<Account>` | — |
| `delete(userId)` | accounts 삭제 → 프로필은 FK cascade로 함께 파기(D41) | — |
| `agreeNotificationEmail(userId, email)` | email 있음 → email + 동의 일시 저장 | email null/빈값(IdP 미제공·미동의) → `IllegalArgumentException`(400). 계정 없음 → `AccountNotFoundException`(401) |
| `withdrawNotificationEmail(userId)` | email·동의 일시를 null로. 멱등 | — |

## Business Rules
- **로그인만으로는 이메일을 저장하지 않는다**(D61 — D60 "동의 기반" 정합). 로그인 중 이메일은 세션 principal 속성에만 머물고, 이 유스케이스의 동의 호출로만 `accounts`에 들어간다.
- 계정 삭제는 **되돌릴 수 없는 파기** — 다음 로그인은 새 `userId`로 새 계정이 된다.

## Side Effects
- 없음(조합) — 쓰기는 [[Auth|AccountStore]].

## 검증
- 웹 슬라이스(MockMvc) — [[AccountApi]]·[[ConsentApi]] §검증: 이메일 있는 principal 동의 → 저장 · 이메일 없는 principal → 400 · 철회 204 · 계정 삭제 204 + 세션 무효화.

## 의존 / 관련
[[Auth]] · [[AccountApi]] · [[ConsentApi]] · [[ProfileUseCase]] · D41·D60·D61.
