// MCP 도구 정의. 각 도구는 코어 커맨드에 1:1 매핑되는 얇은 래퍼.
import { z } from "zod";
import { runCommand } from "../coreClient.js";

export interface ToolDef {
  name: string;
  description: string;
  inputSchema: z.ZodTypeAny;
  handler: (args: any) => Promise<unknown>;
}

export const tools: ToolDef[] = [
  {
    name: "get_impact_for_me",
    description: "입법 예정 법안이 내 상황(페르소나)에 어떤 변화를 주는지 알려준다.",
    inputSchema: z.object({
      billId: z.string().describe("법안 ID (의안)"),
      persona: z.string().describe("예: 임차인, 자영업자, 직장인, 학부모"),
    }),
    handler: ({ billId, persona }) => runCommand("persona_impact", billId, persona),
  },
  {
    name: "get_action_plan",
    description: "법안 시행에 대비해 사용자가 해야 할 일과 기한을 안내한다.",
    inputSchema: z.object({
      billId: z.string(),
      persona: z.string().optional(),
    }),
    handler: ({ billId, persona }) => runCommand("action_plan", billId, persona),
  },
  {
    name: "summarize_bill",
    description: "입법 예정 법안을 평이한 말로 요약한다.",
    inputSchema: z.object({ billId: z.string() }),
    handler: ({ billId }) => runCommand("impact_summary", billId),
  },
  // TODO: search_upcoming_bills, analyze_from_url (SourceResolve 체인)
];
