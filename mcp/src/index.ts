// 입법 영향 분석기 MCP 서버 진입점.
// 코어(Java)가 노출하는 커맨드를 MCP 도구로 감싼다.
import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";
import { zodToJsonSchema } from "zod/v4"; // 또는 zod-to-json-schema
import { tools } from "./tools/index.js";

const server = new Server(
  { name: "lia-mcp", version: "0.1.0" },
  { capabilities: { tools: {} } },
);

server.setRequestHandler(ListToolsRequestSchema, async () => ({
  tools: tools.map((t) => ({
    name: t.name,
    description: t.description,
    // 실제로는 zod → JSON Schema 변환 라이브러리 사용
    inputSchema: { type: "object" },
  })),
}));

server.setRequestHandler(CallToolRequestSchema, async (req) => {
  const tool = tools.find((t) => t.name === req.params.name);
  if (!tool) throw new Error(`알 수 없는 도구: ${req.params.name}`);
  const args = tool.inputSchema.parse(req.params.arguments ?? {});
  const result = await tool.handler(args);
  return { content: [{ type: "text", text: JSON.stringify(result, null, 2) }] };
});

const transport = new StdioServerTransport();
await server.connect(transport);
console.error("lia-mcp 서버 시작 (stdio)");
