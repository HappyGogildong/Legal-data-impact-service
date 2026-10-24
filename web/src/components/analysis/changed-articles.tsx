"use client";

import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import type { CitedSource } from "@/lib/api/types";
import { articleTitle } from "@/lib/analysis-view";
import { ArticleCompare } from "./article-compare";
import { ChangeBadge } from "./change-badge";

/** "무엇이 바뀌나"의 바뀐 조문 목록 — 펼치면 현행↔개정 대비. */
export function ChangedArticles({ articles }: { articles: CitedSource[] }) {
  if (articles.length === 0) return null;
  return (
    <div>
      <h3 className="mb-1 text-xs font-bold text-muted-foreground">
        바뀐 조문
      </h3>
      <ul className="divide-y divide-line-soft">
        {articles.map((article) => (
          <li key={article.sourceId}>
            <Collapsible>
              <CollapsibleTrigger className="flex w-full items-center justify-between gap-2 py-2 text-left">
                <span>{articleTitle(article.label ?? "")}</span>
                <ChangeBadge type={article.changeType} />
              </CollapsibleTrigger>
              <CollapsibleContent className="pb-2">
                <ArticleCompare
                  before={article.previousText ?? null}
                  after={article.text ?? null}
                />
              </CollapsibleContent>
            </Collapsible>
          </li>
        ))}
      </ul>
    </div>
  );
}
