"use client";

import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import type { CitedSource } from "@/lib/api/types";
import { SourceBody } from "./source-panel";

/** 모바일 — 인용을 누르면 아래 시트로 근거 조문. */
export function SourceSheet({
  source,
  onClose,
}: {
  source: CitedSource | null;
  onClose: () => void;
}) {
  return (
    <Sheet
      open={source !== null}
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      <SheetContent
        side="bottom"
        className="max-h-[80dvh] overflow-y-auto rounded-t-xl"
      >
        <SheetHeader>
          <SheetTitle>근거 조문</SheetTitle>
          <SheetDescription className="sr-only">
            답변의 근거가 된 조문 원문
          </SheetDescription>
        </SheetHeader>
        {source && (
          <div className="px-4 pb-6">
            <SourceBody source={source} />
          </div>
        )}
      </SheetContent>
    </Sheet>
  );
}
