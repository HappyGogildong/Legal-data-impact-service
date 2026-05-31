// 코어(Java Spring) REST API 호출 래퍼.
const CORE_BASE_URL = process.env.CORE_BASE_URL ?? "http://localhost:8080";

export interface ImpactResult {
  billId: string;
  persona: string | null;
  summary: string;
  impacts: string[];
  actions: string[];
  confidence: number;
}

export async function runCommand(
  command: string,
  billId: string,
  persona?: string,
): Promise<ImpactResult> {
  const res = await fetch(`${CORE_BASE_URL}/api/v1/analyze`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ command, billId, persona: persona ?? null }),
  });
  if (!res.ok) throw new Error(`core ${command} 실패: ${res.status}`);
  return res.json() as Promise<ImpactResult>;
}
