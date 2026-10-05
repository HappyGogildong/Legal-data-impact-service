---
title: ProfileUseCase — 프로필·프로필 동의 유스케이스 (application 계층)
status: Draft
version: 0.1
date: 2026-09-27
tags: [component, application, usecase, profile, consent]
related: ["components/profile/UserProfile.md", "components/web/ProfileApi.md", "components/web/ConsentApi.md", "components/application/AccountUseCase.md", "adr/decision-log.md"]
---

# ProfileUseCase (application 계층)

> `user_profiles` 한 행을 둘러싼 **규칙**을 담는다 — 프로필 동의(만 14세 확인·처리방침 버전 기록), 현재 버전 동의가 있을 때만 수정 허용, 조회·파기. web은 HTTP만, store는 SQL만 하고 판단은 여기서 한다(D61). 관련: [[UserProfile]](도메인·store) · [[ProfileApi]]·[[ConsentApi]](web).

## 계층·경계
```
web                          application        profile(domain·store)
ProfileController ─┐
ConsentController ─┴──→  ProfileUseCase  ──→  UserProfileStore
```
- **담당**: 동의 전제조건(`over14`)·버전 스탬프 · "현재 버전 동의 없으면 수정 거부" · 동의 상태의 `upToDate` 판정.
- **담당 안 함**: HTTP·세션(web) · 값 불변식·라벨 변환([[UserProfile]]) · SQL([[UserProfile|UserProfileStore]]) · 알림 이메일 동의([[AccountUseCase]]).

## 구조 — `com.lia.core.application.profile`
| 클래스 | 역할 |
|---|---|
| `ProfileUseCase` | 아래 Contract. 생성자에 현재 처리방침 버전(`lia.privacy.policy-version`)을 받는다. |
| `ProfileConsent` (record) | 동의 상태 — `policyVersion` · `consentedAt` · `upToDate`. |
| `ConsentRequiredException` | 현재 버전 동의 없이 수정 시도 → web이 **409 `consent_required`**로 매핑. |

Spring 애노테이션 없음 — `config/UserConfig`의 `@Bean`이 배선([[AnalyzeUseCase]]와 같은 방식·같은 이유).

## Contract
| 메서드 | 전제 → 보장 | 실패 |
|---|---|---|
| `agree(userId, over14)` | `over14 == true` → 현재 버전으로 동의 기록(행 없으면 빈 프로필 생성, 있으면 버전·일시만 갱신). **재동의도 같은 호출** | `over14` false → `IllegalArgumentException`(400). 계정 없음 → `AccountNotFoundException`(401) |
| `consentStatus(userId)` | → `Optional<ProfileConsent>`. `upToDate` = 기록 버전 == 현재 버전 | — |
| `update(userId, profile)` | 현재 버전 동의 있음 → 속성 전체 교체, 저장된 `StoredProfile` 반환(동의 불변) | 동의 없음·옛 버전 → `ConsentRequiredException`(409) |
| `find(userId)` | → `Optional<StoredProfile>` | — |
| `delete(userId)` | 행 삭제 = 파기 + 프로필 동의 철회. 멱등 | — |

## Business Rules
- **동의가 먼저, 수정은 그 다음.** 동의는 한 번 하는 행위, 수정은 여러 번 — 한 요청에 묶지 않는다(재동의하려고 프로필 전체를 다시 보내는 일이 없게).
- **버전 기반 재동의.** 서버 버전이 올라가면 기존 동의는 `upToDate=false` → `update`가 409 → UI가 처리방침을 보여주고 `agree` 후 재시도.
- **클라이언트는 버전을 보내지 않는다.** 서버가 동의 시점의 설정값을 찍는다(게시된 처리방침 = 시행 중인 버전). "현재 버전 조회" 엔드포인트가 필요 없다.
- 경합: 동의 확인과 교체 사이에 프로필이 삭제되면 `replaceAttributes`가 false → `ConsentRequiredException`(동의 없는 행을 만들지 않는다).

## Side Effects
- 없음(조합) — 쓰기는 [[UserProfile|UserProfileStore]].

## 검증
- 웹 슬라이스(MockMvc, 실제 보안 설정 + 가짜 store)로 규칙을 HTTP에서 함께 검증 — [[ConsentApi]]·[[ProfileApi]] §검증. 동의 전 수정 409 · 동의 후 200 · 버전 상승 시 `upToDate=false`+409 → 재동의 후 200 · `over14=false` 400.

## 의존 / 관련
[[UserProfile]] · [[ProfileApi]] · [[ConsentApi]] · [[AccountUseCase]] · [[component-specs]] §2(동의 모델) · D41·D61.
