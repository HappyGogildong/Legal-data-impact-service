import { describe, expect, it } from "vitest";
import { empty, json, mockApi } from "@/test/api-mock";
import { fetchAccount, fetchProfile, logout, saveProfile } from "./auth";
import { ApiError } from "./errors";

describe("auth API", () => {
  it("계정 401 은 익명(null)", async () => {
    mockApi({
      "GET /api/v1/account": () => json({ error: "unauthenticated" }, 401),
    });
    expect(await fetchAccount()).toBeNull();
  });

  it("프로필 404 는 없음(null)", async () => {
    mockApi({ "GET /api/v1/profile": () => empty(404) });
    expect(await fetchProfile()).toBeNull();
  });

  it("프로필 저장 409 는 status 409 ApiError(재동의 신호)", async () => {
    mockApi({
      "PUT /api/v1/profile": () =>
        json({ error: "consent_required", message: "동의 필요" }, 409),
    });
    await expect(saveProfile({ housingType: "전세" })).rejects.toEqual(
      new ApiError(409, "동의 필요"),
    );
  });

  it("로그아웃 — POST /logout 에 CSRF 헤더", async () => {
    document.cookie = "XSRF-TOKEN=t1";
    const requests = mockApi({ "POST /logout": () => empty(204) });
    await logout();
    expect(requests[0].headers.get("X-XSRF-TOKEN")).toBe("t1");
  });
});
