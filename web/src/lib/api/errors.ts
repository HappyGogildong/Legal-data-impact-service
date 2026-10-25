/** 시스템 오류(4xx/5xx) — 해소 실패·근거 부족은 오류가 아니라 200 본문(service-api-spec §4.1). */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

type FetchResult<T> = { data?: T; error?: unknown; response: Response };

function failure(error: unknown, response: Response): ApiError {
  const message =
    typeof error === "object" &&
    error !== null &&
    "message" in error &&
    typeof error.message === "string"
      ? error.message
      : `요청 실패 (${response.status})`;
  return new ApiError(response.status, message);
}

/** openapi-fetch 결과 → 데이터, 실패면 서버 {error, message} 의 message 로 ApiError. */
export function unwrap<T>({ data, error, response }: FetchResult<T>): T {
  if (response.ok && data !== undefined) return data;
  throw failure(error, response);
}

/** 본문 없는 성공(204)을 기대하는 호출. */
export function expectOk({ error, response }: FetchResult<unknown>): void {
  if (!response.ok) throw failure(error, response);
}

/** 세션 만료·익명 — 보호 동작에서 "다시 로그인"으로. */
export function isUnauthorized(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401;
}
