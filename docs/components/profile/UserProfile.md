---
title: UserProfile — 자기신고 프로필 도메인 + Store (슬라이스 ②)
status: Draft
version: 0.1
date: 2026-09-09
tags: [component, profile, domain, store, privacy]
related: ["components/component-specs.md", "components/auth/Auth.md", "components/web/ProfileApi.md", "components/store/LawStore.md", "adr/decision-log.md"]
---

# UserProfile (도메인 + Store)

> 회원가입 자기신고 프로필(D41). **스키마·프라이버시·프롬프트 주입 규율은 이미 확정** — [[component-specs]] §2가 SSOT. 이 문서는 **도메인 타입 불변식 + 영속 계약**만 담는다. 관련: [[Auth]](① 신원) · [[ProfileApi]](② HTTP) · [[component-specs]] §2.

## Responsibility
- **담당**: `UserProfile` 값 불변식(입력 검증) · `(userId)` 단위 영속(upsert·find·delete).
- **담당 안 함**: 신원/세션([[Auth]]) · HTTP([[ProfileApi]]) · 프롬프트 주입(Layer B, ③).

## Collaborators
- **DB**: `user_profiles` 테이블(JdbcClient+Flyway, [[LawStore]] 패턴). `user_id`는 [[Auth]] `accounts` FK.
- 소비자: [[ProfileApi]](CRUD) · Layer B(③ — userId로 조회해 프롬프트에 주입).

## 구조 (컴포넌트) — `com.lia.core.profile`
| 클래스 | 역할 |
|---|---|
| `UserProfile` (record) | D41 속성 + **불변식**. 스키마 세부는 [[component-specs]] §2 참조(미러링 금지). |
| `Purpose` (enum) | 이용 목적 8종(§2). |
| `UserProfileStore` | JdbcClient — `upsert(userId, UserProfile)` · `find(userId)` · `delete(userId)`. 테이블 `user_profiles`. |

## Schema (→ [[component-specs]] §2)
속성: `purposes[]` · `age(정수?)` · `occupation?` · `employmentType?` · `householdType?` · `housingType?` · `regionSido?` · `updatedAt`. **미수집: 성명·생년월일·주민번호·연락처·상세주소(시군구↓)·소득**(§2 표).

## Invariants
- **age는 정수(구간화 금지, D41)** — 있으면 양의 정수(만 나이). 법령 임계(만 19/34/65)와 정확 대조.
- enum 필드(`employmentType`·`householdType`·`housingType`)는 정의된 값만. `regionSido`는 **17개 시도**만(시군구 이하 거부).
- `purposes`는 `Purpose` enum 다중(비어 있을 수 있음 — 전부 선택).
- 전부 선택 입력(다 비어도 유효) — 채울수록 개인화 정확도↑(§2).
- **`userId`는 도메인 값에 넣지 않는다**(Store 키로만). 프롬프트 주입 시 속성만(D41·D10).

## Persistence Contract
- `user_profiles(user_id uuid PK → accounts(user_id) ON DELETE CASCADE, purposes text[], age int, occupation text, employment_type text, household_type text, housing_type text, region_sido text, updated_at timestamptz)` — Flyway `V3__user_profiles.sql`.
- `upsert` 멱등(`ON CONFLICT (user_id) DO UPDATE`, updated_at 갱신) — 프로필 수정.
- `delete(userId)` — 파기(계정 삭제 시 cascade로도 제거).

## Error Handling
- 검증 위반(음수 age·미정의 enum·시군구 시도) → `IllegalArgumentException`(생성자) → [[ProfileApi]]가 400 매핑.

## Side Effects
- **DB 쓰기/삭제**. 외부 API 없음.

## 프라이버시 (D41, 횡단)
- **"개인정보 아님"이 아니라 최소 수집** — 조합 재식별 가능성 → 처리방침·동의·**파기(DELETE)** 필요.
- **답변 캐시 키 = 프로필 속성 해시**(userId 아님, D41·D51) — 동일 속성 재사용·개인 추적 회피.
- 정량 인구통계 용도 금지(자기신고 표본, 대표성 없음).

## 검증
- 단위: `UserProfile` 불변식(음수 age·미정의 enum·비시도 regionSido 거부·전부 null 허용).
- 통합(Testcontainers): `UserProfileStore` upsert/find/delete 라운드트립 + accounts FK cascade.

## 의존 / 관련
[[Auth]](① userId 발급) · [[ProfileApi]](② CRUD) · [[component-specs]] §2(스키마 SSOT) · Layer B(③ 프롬프트 주입). D41.
