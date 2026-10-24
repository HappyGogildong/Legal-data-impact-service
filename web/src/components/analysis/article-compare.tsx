import { wordDiff } from "@/lib/text-diff";

/** 조문 대비 — 모바일 위아래, 데스크톱 좌우. 현행은 지운 구간 취소선, 개정은 바뀐 구간 강조(visual-design §3). */
export function ArticleCompare({
  before,
  after,
}: {
  before: string | null;
  after: string | null;
}) {
  const parts = before && after ? wordDiff(before, after) : null;
  return (
    <div className="mt-1 flex flex-col gap-2 text-[12.5px] lg:flex-row">
      <div className="flex-1 rounded-md bg-slate-50 p-2.5">
        <p className="mb-1 text-[10.5px] text-muted-foreground">현행</p>
        <p className="whitespace-pre-line text-muted-foreground">
          {before === null
            ? "현행 조문 없음"
            : parts
              ? parts
                  .filter((part) => part.kind !== "added")
                  .map((part, i) =>
                    part.kind === "removed" ? (
                      <del key={i}>{part.text}</del>
                    ) : (
                      <span key={i}>{part.text}</span>
                    ),
                  )
              : before}
        </p>
      </div>
      <div className="flex-1 rounded-md bg-amber-50 p-2.5">
        <p className="mb-1 text-[10.5px] text-amend">개정</p>
        <p className="whitespace-pre-line">
          {after === null
            ? "원문을 불러오지 못했어요"
            : parts
              ? parts
                  .filter((part) => part.kind !== "removed")
                  .map((part, i) =>
                    part.kind === "added" ? (
                      <mark key={i} className="bg-mark px-0.5">
                        {part.text}
                      </mark>
                    ) : (
                      <span key={i}>{part.text}</span>
                    ),
                  )
              : after}
        </p>
      </div>
    </div>
  );
}
