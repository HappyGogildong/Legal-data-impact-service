import { vi } from "vitest";

type Handler = (request: Request) => Response | Promise<Response>;

export function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

export function empty(status: number) {
  return new Response(null, { status });
}

/** fetch 를 "메서드 경로" 키로 모의한다. 모의 없는 호출은 실패로 드러낸다. 반환값은 받은 요청 목록. */
export function mockApi(routes: Record<string, Handler>) {
  const requests: Request[] = [];
  vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
    const request = input as Request;
    requests.push(request.clone());
    const key = `${request.method} ${new URL(request.url).pathname}`;
    const handler = routes[key];
    if (!handler) throw new Error(`모의 없음: ${key}`);
    return handler(request);
  });
  return requests;
}
