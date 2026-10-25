// E2E 모의 백엔드 — 서버 컴포넌트(홈 목록·S05·S06)의 조회는 브라우저 밖이라 page.route 로 못 가로챈다(frontend-stack §5).
// Playwright 가 이 서버를 띄우고 Next 를 LIA_API_ORIGIN=http://localhost:3199 로 빌드·기동한다. 의존성 없음(node:http).
import { createServer } from "node:http";

const PORT = Number(process.env.MOCK_API_PORT ?? 3199);
const DAY_MS = 86_400_000;

/** 서울 오늘 + n일 "YYYY-MM-DD" — D-N 이 실행일과 무관하게 같게 보이도록 날짜를 실행 시점 기준으로 만든다. */
function seoulDay(offset) {
  const seoul = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
  return new Date(Date.parse(`${seoul}T00:00:00Z`) + offset * DAY_MS)
    .toISOString()
    .slice(0, 10);
}

function laws() {
  return [
    {
      lawId: "001809",
      title: "주택법",
      effectiveDate: seoulDay(9),
      lawType: "법률",
      ministry: "국토교통부",
      amendKind: "일부개정",
    },
    {
      lawId: "001234",
      title: "근로기준법 시행령",
      effectiveDate: seoulDay(23),
      lawType: "대통령령",
      ministry: "고용노동부",
      amendKind: "일부개정",
    },
    {
      lawId: "000777",
      title: "소득세법",
      effectiveDate: seoulDay(70),
      lawType: "법률",
      ministry: "기획재정부",
      amendKind: "일부개정",
    },
  ];
}

function list(params) {
  const from = params.get("from");
  const to = params.get("to");
  const limit = Number(params.get("limit") ?? 20);
  const items = laws().filter(
    (law) =>
      (!from || law.effectiveDate >= from) && (!to || law.effectiveDate <= to),
  );
  return { items: items.slice(0, limit), nextCursor: null };
}

function detail(effectiveDate) {
  const ref = `LAW:001809@${effectiveDate}`;
  const clause = {
    sourceId: `${ref}:addenda:제1조`,
    label: "부칙 제1조(시행일)",
    text: "이 법은 공포 후 6개월이 경과한 날부터 시행한다.",
  };
  return {
    lawId: "001809",
    title: "주택법",
    lawType: "법률",
    ministry: "국토교통부",
    promulgateDate: "2026-05-01",
    promulgateNo: "21323",
    effectiveDate,
    status: "시행예정",
    amendKind: "일부개정",
    sourceUrl: "https://www.law.go.kr/",
    amendReason: "주택 사업계획 심의 절차를 간소화한다.",
    enforcementType: "유예",
    effectiveRule: "공포 후 6개월이 경과한 날부터 시행",
    effectiveClause: clause,
    changedArticles: [
      {
        sourceId: `${ref}:art:18`,
        no: "18",
        label: "제18조(사업계획의 통합심의)",
        changeType: "개정",
        text: "① 도시계획·건축·환경·교통·재해 심의를 통합하여 검토한다.",
        previousText: "① 도시계획·건축·교통 심의를 통합하여 검토한다.",
      },
    ],
    addenda: [clause],
    delegationClauses: ["대통령령으로 정하는 기준에 따라 통합심의한다."],
    baselineEffectiveDate: "2020-01-01",
  };
}

function send(response, status, body) {
  response.writeHead(status, { "Content-Type": "application/json" });
  response.end(JSON.stringify(body));
}

createServer((request, response) => {
  const url = new URL(request.url ?? "/", `http://localhost:${PORT}`);
  const housing = seoulDay(9);
  if (url.pathname === "/health") return send(response, 200, { ok: true });
  if (url.pathname === "/api/v1/data-status") {
    return send(response, 200, {
      lawsUpdatedAt: "2026-10-22T19:12:31Z",
      pendingLawCount: 3,
    });
  }
  if (url.pathname === "/api/v1/laws")
    return send(response, 200, list(url.searchParams));
  const versions = url.pathname.match(/^\/api\/v1\/laws\/([^/]+)$/);
  if (versions?.[1] === "001809") {
    return send(response, 200, {
      lawId: "001809",
      title: "주택법",
      pending: [
        {
          effectiveDate: housing,
          promulgateNo: "21323",
          amendKind: "일부개정",
        },
      ],
      current: { effectiveDate: "2020-01-01", promulgateNo: "20000" },
    });
  }
  const detailMatch = url.pathname.match(/^\/api\/v1\/laws\/([^/]+)\/([^/]+)$/);
  if (detailMatch?.[1] === "001809" && detailMatch[2] === housing) {
    return send(response, 200, detail(housing));
  }
  return send(response, 404, {
    error: "not_found",
    message: "모의 데이터 없음",
  });
}).listen(PORT, () => console.log(`모의 API http://localhost:${PORT}`));
