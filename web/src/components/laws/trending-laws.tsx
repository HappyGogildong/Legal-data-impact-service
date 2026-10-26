import { connection } from "next/server";
import { fetchTrendingLaws } from "@/lib/api/laws";
import { TrendingLawList } from "./trending-law-list";

export const HOME_TRENDING_SIZE = 5;

/** 홈 "자주 찾는 법령" — 요청 시점 조회. 비거나 실패하면 목록만 숨긴다. */
export async function TrendingLaws() {
  await connection();
  const page = await fetchTrendingLaws("week", HOME_TRENDING_SIZE).catch(
    () => null,
  );
  if (!page) return null;
  return <TrendingLawList items={page.items ?? []} />;
}
