import { api, readCookie } from "./client";
import { ApiError, expectOk, unwrap } from "./errors";
import type {
  Account,
  Consents,
  NotificationEmailConsent,
  Profile,
  ProfileConsent,
  ProfileInput,
} from "./types";

/** 로그인 상태 확인 겸용 — 401 이면 익명(null). */
export async function fetchAccount(): Promise<Account | null> {
  const result = await api.GET("/api/v1/account");
  if (result.response.status === 401) return null;
  return unwrap(result);
}

export async function fetchConsents(): Promise<Consents> {
  return unwrap(await api.GET("/api/v1/consents"));
}

/** 프로필 수집 동의·재동의 — 만 14세 이상 확인을 함께 보낸다(화면에서 체크받은 뒤에만 부른다). */
export async function agreeProfileConsent(): Promise<ProfileConsent> {
  return unwrap(
    await api.PUT("/api/v1/consents/profile", { body: { over14: true } }),
  );
}

/** 프로필 — 없으면 null(404). */
export async function fetchProfile(): Promise<Profile | null> {
  const result = await api.GET("/api/v1/profile");
  if (result.response.status === 404) return null;
  return unwrap(result);
}

/** 전체 교체. 현재 버전 동의가 없으면 409 ApiError. */
export async function saveProfile(input: ProfileInput): Promise<Profile> {
  return unwrap(await api.PUT("/api/v1/profile", { body: input }));
}

/** 프로필 삭제 = 동의 철회. */
export async function deleteProfile(): Promise<void> {
  expectOk(await api.DELETE("/api/v1/profile"));
}

export async function agreeNotificationEmail(): Promise<NotificationEmailConsent> {
  return unwrap(await api.PUT("/api/v1/consents/notification-email"));
}

export async function withdrawNotificationEmail(): Promise<void> {
  expectOk(await api.DELETE("/api/v1/consents/notification-email"));
}

/** 계정 삭제 — 백엔드가 세션도 끝낸다. 되돌릴 수 없다. */
export async function deleteAccount(): Promise<void> {
  expectOk(await api.DELETE("/api/v1/account"));
}

/** 로그아웃 — /logout 은 OpenAPI 밖(/api/** 아님)이라 직접 부른다. CSRF 필요, 응답 204. */
export async function logout(): Promise<void> {
  const token = readCookie("XSRF-TOKEN");
  const response = await globalThis.fetch(
    new Request(new URL("/logout", window.location.origin), {
      method: "POST",
      credentials: "same-origin",
      headers: token ? { "X-XSRF-TOKEN": token } : {},
    }),
  );
  if (!response.ok)
    throw new ApiError(response.status, `로그아웃 실패 (${response.status})`);
}
