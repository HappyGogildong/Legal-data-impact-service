---
title: Profile API — REST 어댑터 (슬라이스 ②, web)
status: Draft
version: 0.2
date: 2026-09-27
tags: [component, web, rest, profile]
related: ["components/profile/UserProfile.md", "components/application/ProfileUseCase.md", "components/web/ConsentApi.md", "components/auth/Auth.md", "mvp/service-api-spec.md"]
---

# Profile API (web 계층, HTTP 어댑터)

> 자기신고 프로필 **속성**의 조회·수정·파기([[service-api-spec]] §3.4). 인증된 세션 `userId`에 귀속해 [[ProfileUseCase]]에 위임한다. **동의는 여기서 받지 않는다** — [[ConsentApi]]. HTTP 경계만 담당([[AnalysisApi]]와 동일 패턴).

## Responsibility
- **담당**: `/api/v1/profile` 요청 파싱·라벨→enum 변환 → 세션 `userId`로 [[ProfileUseCase]] 위임 → 응답·상태코드.
- **담당 안 함**: 동의 규칙·409 판단([[ProfileUseCase]]) · 값 불변식·영속([[UserProfile]]) · 인증/세션([[Auth]]) · 동의 받기([[ConsentApi]]).

## Collaborators
- [[Auth]] — `CurrentUser`로 세션 `userId`. 없으면 401.
- [[ProfileUseCase]] — update/find/delete.

## HTTP Contract — `com.lia.core.web`
| 메서드 | 경로 | 동작 | 응답 |
|---|---|---|---|
| `PUT` | `/api/v1/profile` | 속성 **전체 교체**(보내지 않은 필드 → null). 동의는 건드리지 않는다 | 200 + 저장된 프로필 / **409** 현재 버전 동의 없음 |
| `GET` | `/api/v1/profile` | 속성 + `updatedAt`(**userId 제외**) | 200 / 404 |
| `DELETE` | `/api/v1/profile` | 파기 = 프로필 동의 철회. 멱등 | 204 |

```jsonc
// PUT /api/v1/profile — 전부 선택 입력, 값은 라벨 문자열
{ "purposes": ["생활·주거", "관심사 모니터링"], "age": 29, "occupation": "사무",
  "employmentType": "임금근로", "householdType": "1인",
  "housingType": "전세", "regionSido": "서울특별시" }
// 200 / GET 응답 → 위 속성 + "updatedAt"   (userId 없음)
// 409 → { "error": "consent_required", "message": "..." }
```
- 인증 필수, 상태변경은 CSRF 토큰 필요(SPA 방식, [[SecurityConfig]]).
- 동의만 하고 속성을 아직 안 넣은 경우 `GET`은 **200 + 빈 속성**(`purposes: []`, 나머지 null) — 행은 동의가 만든다.

## 구조 (컴포넌트)
| 클래스 | 역할 |
|---|---|
| `ProfileController` | `@RestController` `/api/v1/profile` — PUT/GET/DELETE. HTTP만. |
| `ProfileApiRequest` | PUT 바디(라벨 문자열) → `UserProfile`. 모르는 라벨 → `IllegalArgumentException`. |
| `ProfileResponse` | `StoredProfile` → 응답(속성 라벨 + `updatedAt`). userId를 담을 필드가 없다. |

## Error Handling
- 검증 실패(모르는 라벨·범위 밖 age) → 400(`ApiExceptionHandler`).
- 현재 버전 동의 없음 → **409 `consent_required`**(`ConsentRequiredException` → `ApiExceptionHandler`). 403이 아닌 이유: 403은 CSRF 실패에도 쓰여 SPA가 구분하지 못한다.
- 미인증 → 401([[SecurityConfig]]). 세션에 `userId` 없음(인증됐지만 매핑 없는 이상 상태) → 401.
- `GET` 미존재 → 404.

## Side Effects
- 없음(위임).

## 검증
- 웹 슬라이스(MockMvc — 실제 `SecurityConfig` + 실제 컨트롤러 + 가짜 store, [[SecurityConfig]] §검증과 같은 방식): 동의 전 PUT 409 · 동의 후 PUT 200 · GET에 userId 없음 · 미존재 404 · DELETE 204 · 모르는 라벨 400 · 버전 상승 후 PUT 409.

## 의존 / 관련
[[Auth]](세션 userId) · [[ProfileUseCase]] · [[UserProfile]] · [[ConsentApi]](동의) · [[AnalysisApi]](동일 web 패턴·ApiExceptionHandler 재사용) · [[service-api-spec]] §3.4.
