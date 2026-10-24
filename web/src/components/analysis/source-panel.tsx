import type { CitedSource } from "@/lib/api/types";
import { ArticleCompare } from "./article-compare";
import { ChangeBadge } from "./change-badge";

/** 근거 조문 내용 — 패널(데스크톱)·시트(모바일) 공용. 현행 원문이 있으면 대비로. */
export function SourceBody({ source }: { source: CitedSource }) {
  return (
    <div className="flex flex-col gap-2 text-sm">
      <div className="flex items-center gap-2">
        <b>{source.label}</b>
        <ChangeBadge type={source.changeType} />
      </div>
      {source.effectiveDate && (
        <p className="text-xs text-muted-foreground">
          {source.effectiveDate} 시행본
        </p>
      )}
      {!source.text ? (
        <p className="text-muted-foreground">원문을 불러오지 못했어요.</p>
      ) : source.previousText ? (
        <ArticleCompare before={source.previousText} after={source.text} />
      ) : (
        <p className="whitespace-pre-line">{source.text}</p>
      )}
    </div>
  );
}

/** 데스크톱 — 인용을 누르면 오른쪽 패널이 근거로 바뀐다. "← 법령 정보로"·✕로 복귀(visual-design §3). */
export function SourcePanel({
  source,
  onClose,
}: {
  source: CitedSource;
  onClose: () => void;
}) {
  return (
    <section
      aria-label="근거 조문"
      className="rounded-lg border border-border bg-card p-3.5"
    >
      <div className="mb-2 flex items-center justify-between text-xs">
        <button type="button" onClick={onClose} className="font-bold text-navy">
          ← 법령 정보로
        </button>
        <button type="button" onClick={onClose} aria-label="근거 닫기">
          ✕
        </button>
      </div>
      <SourceBody source={source} />
    </section>
  );
}
