import { connection } from "next/server";
import { fetchUpcomingLaws } from "@/lib/api/laws";
import { seoulToday } from "@/lib/dates";
import { UpcomingLawList } from "./upcoming-law-list";

export const HOME_LIST_SIZE = 5;

/** 홈 "곧 시행되는 법령" — 요청 시점에 조회(빌드는 백엔드 없이). 실패하면 목록만 숨기고 질문창은 그대로. */
export async function UpcomingLaws() {
  await connection();
  const today = seoulToday();
  const page = await fetchUpcomingLaws({ limit: HOME_LIST_SIZE }).catch(
    () => null,
  );
  if (!page) return null;
  return <UpcomingLawList items={page.items ?? []} today={today} />;
}
