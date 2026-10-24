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

/** openapi-fetch 결과 → 데이터, 실패면 서버 {error, message} 의 message 로 ApiError. */
export function unwrap<T>({ data, error, response }: FetchResult<T>): T {
  if (response.ok && data !== undefined) return data;
  const message =
    typeof error === "object" &&
    error !== null &&
    "message" in error &&
    typeof error.message === "string"
      ? error.message
      : `요청 실패 (${response.status})`;
  throw new ApiError(response.status, message);
}
