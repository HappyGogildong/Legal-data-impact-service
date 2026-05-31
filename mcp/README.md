# mcp — 도구 표면 / BFF (TypeScript)

참고 리포(`korean-law-mcp`)처럼 서비스를 MCP 도구로 노출해 Claude·Cursor 등에서
바로 쓰게 한다. 각 도구는 코어(Java)의 커맨드 한 개(또는 체인)에 1:1 매핑된다.

| MCP 도구 | 매핑 커맨드 |
|---|---|
| `search_upcoming_bills` | 의안 검색 |
| `analyze_from_url` | SourceResolve → ImpactSummary |
| `get_impact_for_me` | persona_impact |
| `get_action_plan` | action_plan |

새 커맨드를 코어에 추가하면, `src/tools/` 에 얇은 래퍼 하나만 더 만들면 된다
(또는 `/api/v1/commands` 를 읽어 동적 생성).
