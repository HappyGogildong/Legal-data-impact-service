import { describe, expect, it, vi } from "vitest";
import { api, readCookie } from "./client";
import { ApiError, unwrap } from "./errors";

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

describe("api 클라이언트", () => {
  it("상태 변경 요청엔 XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 싣는다", async () => {
    document.cookie = "XSRF-TOKEN=token%2Fvalue";
    const fetchSpy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(jsonResponse({ resolution: "NOT_FOUND_YET" }));

    await api.POST("/api/v1/analyses", { body: { query: "주택법" } });

    const request = fetchSpy.mock.calls[0][0] as Request;
    expect(request.headers.get("X-XSRF-TOKEN")).toBe("token/value");
    expect(new URL(request.url).pathname).toBe("/api/v1/analyses");
  });

  it("조회(GET)엔 CSRF 헤더를 싣지 않는다", async () => {
    document.cookie = "XSRF-TOKEN=abc";
    const fetchSpy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(jsonResponse({ pendingLawCount: 1 }));

    await api.GET("/api/v1/data-status");

    expect(
      (fetchSpy.mock.calls[0][0] as Request).headers.has("X-XSRF-TOKEN"),
    ).toBe(false);
  });

  it("readCookie — 없는 쿠키는 null", () => {
    expect(readCookie("없는-쿠키")).toBeNull();
  });
});

describe("unwrap", () => {
  it("실패 응답은 서버 message 를 담은 ApiError", () => {
    const failed = {
      error: { error: "bad_request", message: "query는 필수입니다." },
      response: new Response(null, { status: 400 }),
    };
    expect(() => unwrap(failed)).toThrowError(
      new ApiError(400, "query는 필수입니다."),
    );
  });

  it("message 가 없으면 상태 코드로 설명", () => {
    const failed = {
      error: undefined,
      response: new Response(null, { status: 502 }),
    };
    expect(() => unwrap(failed)).toThrowError("요청 실패 (502)");
  });
});
