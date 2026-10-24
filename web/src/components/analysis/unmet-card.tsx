import type { Unmet } from "@/lib/api/types";
import { unmetCopy } from "@/lib/analysis-view";

/** 채우지 못한 차원 자리의 안내 — 사유 코드별 문구, 다음 행동 버튼은 children(로그인 흐름이 넣는다). */
export function UnmetCard({
  unmet,
  children,
}: {
  unmet?: Unmet;
  children?: React.ReactNode;
}) {
  const copy = unmetCopy(unmet);
  return (
    <div className="rounded-lg bg-tint p-3 text-sm">
      <p className="font-bold">{copy.title}</p>
      {copy.body && <p className="text-muted-foreground">{copy.body}</p>}
      {children}
    </div>
  );
}
