---
title: UserProfile — 자기신고 프로필 도메인 + Store (슬라이스 ②)
status: Draft
version: 0.2
date: 2026-09-27
tags: [component, profile, domain, store, privacy, consent]
related: ["components/component-specs.md", "components/auth/Auth.md", "components/web/ProfileApi.md", "components/web/ConsentApi.md", "components/application/ProfileUseCase.md", "adr/decision-log.md"]
---

# UserProfile (도메인 + Store)

> 회원가입 자기신고 프로필(D41) + 프로필 수집 동의(D61). **스키마·동의 모델·프롬프트 주입 규율은 [[component-specs]] §2가 SSOT** — 이 문서는 **도메인 타입 불변식 + 영속 계약**만 담는다. 관련: [[Auth]](① 신원) · [[ProfileUseCase]](규칙) · [[ProfileApi]]·[[ConsentApi]](HTTP).

## Responsibility
- **담당**: `UserProfile` 값 불변식(입력 검증) · 라벨↔enum 변환 · `(userId)` 단위 영속(동의 기록·속성 교체·조회·삭제).
- **담당 안 함**: 동의 규칙(현재 버전 여부·만 14세 확인 → [[ProfileUseCase]]) · 신원/세션([[Auth]]) · HTTP · 프롬프트 주입(Layer B, ③).

## Collaborators
- **DB**: `user_profiles`(JdbcClient+Flyway, [[LawStore]] 패턴). `user_id`는 [[Auth]] `accounts` FK.
- 소비자: [[ProfileUseCase]] · Layer B(③ — userId로 조회해 프롬프트에 주입).

## 구조 (컴포넌트) — `com.lia.core.profile`
| 클래스 | 역할 |
|---|---|
| `UserProfile` (record) | 속성만(프롬프트에 들어갈 값). **userId·타임스탬프 없음**. 불변식은 compact 생성자가 강제. |
| enum 6종 | `Purpose`·`Occupation`·`EmploymentType`·`HouseholdType`·`HousingType`·`Sido`. 값 목록은 [[component-specs]] §2(미러링 금지). |
| `StoredProfile` (record) | 조회 결과 — `UserProfile` + `policyVersion` + `consentedAt` + `updatedAt`. |
| `UserProfileStore` | JdbcClient — `recordConsent` · `replaceAttributes` · `find` · `delete`. 테이블 `user_profiles`. |

## 라벨 규약 (enum ↔ 문자열)
- **API·DB 모두 라벨 문자열**(`"무직·은퇴"`, `"부부+자녀"`, `"서울특별시"` …)을 값으로 쓴다 — 표기가 하나라 변환 지점이 경계 한 곳뿐이다.
- 라벨에 `·`·`+`·공백이 있어 Java 상수명으로 쓸 수 없는 경우가 있다 → 상수는 기존 관례대로 한글(`무직은퇴`, `부부자녀` …), 라벨은 필드로 따로 둔다.
- 모르는 라벨 → `IllegalArgumentException`(400). `Sido`는 **공식 전체 명칭만** — 약칭(`서울`)·시군구 거부.

## Invariants
- **age는 정수(구간화 금지, D41), null 또는 14~120**(만 14세 이상 전용, D61). 법령 임계(만 19/34/65)와 정확 대조.
- `purposes`는 집합 — null이면 빈 집합, 중복 제거, 불변 복사.
- 전부 선택 입력(다 비어도 유효) — 채울수록 개인화 정확도↑.
- **모든 필드가 닫힌 값**(자유 문자열 없음) — 프롬프트 인젝션 경로 차단(D61).
- **`userId`는 도메인 값에 넣지 않는다**(Store 키로만). 프롬프트 주입 시 속성만(D41·D10).

## Persistence Contract
```sql
-- V3__user_profiles.sql
user_profiles(
  user_id uuid PRIMARY KEY REFERENCES accounts(user_id) ON DELETE CASCADE,
  purposes text[] NOT NULL DEFAULT '{}',
  age int CHECK (age BETWEEN 14 AND 120),
  occupation text, employment_type text, household_type text,
  housing_type text, region_sido text,          -- 라벨 문자열
  policy_version text NOT NULL,                 -- 동의 없는 프로필은 DB가 거부
  consented_at   timestamptz NOT NULL,
  updated_at     timestamptz NOT NULL)
```
- `recordConsent(userId, policyVersion)` — 행이 없으면 **속성이 빈 행**을 만들고, 있으면 `policy_version`·`consented_at`만 갱신(속성 보존). 재동의도 같은 호출. `ON CONFLICT (user_id) DO UPDATE`.
- `replaceAttributes(userId, profile)` → 갱신 여부(boolean). 속성 **전체 교체**(보내지 않은 필드는 null) + `updated_at` 갱신. **동의 컬럼은 건드리지 않는다.** 행이 없으면 아무것도 안 하고 false — 동의 없이 행을 만들 수 없다.
- `find(userId)` → `Optional<StoredProfile>`.
- `delete(userId)` — 파기(프로필 동의 철회 겸). 멱등. 계정 삭제 시 cascade로도 제거.

## Error Handling
- 검증 위반(범위 밖 age·모르는 라벨) → `IllegalArgumentException`(생성자·라벨 변환) → 400.
- DB CHECK(age)는 도메인 검증의 방어선 — 정상 경로에선 도달하지 않는다.

## Side Effects
- **DB 쓰기/삭제**. 외부 API 없음.

## 프라이버시 (D41, 횡단)
- **"개인정보 아님"이 아니라 최소 수집** — 조합 재식별 가능성 → 처리방침·동의·**파기(DELETE)** 필요.
- **답변 캐시 키 = 프로필 속성 해시**(userId 아님, D41·D51) — 동일 속성 재사용·개인 추적 회피.
- 정량 인구통계 용도 금지(자기신고 표본, 대표성 없음).
- **Layer B 소비 규칙(③)**: 현재 버전 동의(`upToDate`)가 있는 프로필만 프롬프트에 주입한다. 옛 버전 동의로는 개인화하지 않는다.

## 검증
- 단위: `UserProfile` 불변식(age 13/14/120/121/null · purposes null→빈 집합·중복 제거 · 전부 null 허용) · 라벨 변환(모르는 값·시도 약칭 거부).
- 통합(Testcontainers): 동의만으로 빈 행 생성 · 속성 교체가 동의를 보존 · 동의 없으면 교체 false · `text[]`·라벨 라운드트립 · 삭제 · **accounts 삭제 시 cascade** · DB age CHECK.

## 의존 / 관련
[[Auth]](① userId 발급) · [[ProfileUseCase]](동의 규칙) · [[ProfileApi]]·[[ConsentApi]](HTTP) · [[component-specs]] §2(스키마 SSOT) · Layer B(③ 프롬프트 주입). D41·D61.
