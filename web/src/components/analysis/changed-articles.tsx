"use client";

import { useEffect } from "react";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import { useHash } from "@/hooks/use-browser";
import type { CitedSource } from "@/lib/api/types";
import { articleTitle } from "@/lib/analysis-view";
import { sourceAnchor } from "@/lib/law-view";
import { ArticleCompare } from "./article-compare";
import { ChangeBadge } from "./change-badge";

type ComparedArticle = Pick<
  CitedSource,
  "sourceId" | "label" | "text" | "previousText" | "changeType"
>;

type ChangedArticlesProps = {
  articles: ComparedArticle[];
  /** 목록 제목 — null 이면 숨긴다(바깥에 제목이 있을 때). */
  title?: string | null;
  /** 조문마다 고유 주소(#art-6-3)를 달고, 주소가 가리키는 조문은 펼쳐 둔다(S05). */
  anchored?: boolean;
  /** 현행↔개정 대비 — false 면 본문만(시행된 버전은 현행이 곧 이 조문, S05). */
  compare?: boolean;
};

/** 바뀐 조문 목록 — 펼치면 현행↔개정 대비. S02 "무엇이 바뀌나"·S05 공용. */
export function ChangedArticles({
  articles,
  title = "바뀐 조문",
  anchored = false,
  compare = true,
}: ChangedArticlesProps) {
  const hash = useHash();
  const linkedAnchor = anchored
    ? (articles
        .map((article) =>
          article.sourceId ? sourceAnchor(article.sourceId) : null,
        )
        .find((anchor) => anchor !== null && hash === `#${anchor}`) ?? null)
    : null;
  // 본문이 스트리밍으로 늦게 오면 브라우저의 기본 앵커 이동이 이미 지나가 있다 — 주소의 조문으로 직접 스크롤한다.
  useEffect(() => {
    if (linkedAnchor) {
      document.getElementById(linkedAnchor)?.scrollIntoView({ block: "start" });
    }
  }, [linkedAnchor]);
  if (articles.length === 0) return null;
  return (
    <div>
      {title && (
        <h3 className="mb-1 text-xs font-bold text-muted-foreground">
          {title}
        </h3>
      )}
      <ul className="divide-y divide-line-soft">
        {articles.map((article) => {
          const anchor =
            anchored && article.sourceId
              ? sourceAnchor(article.sourceId)
              : null;
          const linked = anchor !== null && hash === `#${anchor}`;
          return (
            <li
              key={article.sourceId}
              id={anchor ?? undefined}
              className="scroll-mt-16"
            >
              {/* 주소가 이 조문을 가리키게 되면 펼친 채로 다시 그린다(key) */}
              <Collapsible
                key={linked ? "linked" : "closed"}
                defaultOpen={linked}
              >
                <CollapsibleTrigger className="flex w-full items-center justify-between gap-2 py-2 text-left">
                  <span>{articleTitle(article.label ?? "")}</span>
                  <ChangeBadge type={article.changeType} />
                </CollapsibleTrigger>
                <CollapsibleContent className="pb-2">
                  {compare ? (
                    <ArticleCompare
                      before={article.previousText ?? null}
                      after={article.text ?? null}
                    />
                  ) : (
                    <p className="mt-1 text-[12.5px] whitespace-pre-line">
                      {article.text ?? "원문을 불러오지 못했어요"}
                    </p>
                  )}
                </CollapsibleContent>
              </Collapsible>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
