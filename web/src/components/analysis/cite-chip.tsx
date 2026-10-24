import type { CitedSource } from "@/lib/api/types";
import { citeLabel } from "@/lib/analysis-view";
import { cn } from "@/lib/utils";

type CiteChipProps = {
  sourceId: string;
  source?: CitedSource;
  active: boolean;
  onCite: (sourceId: string) => void;
};

export function CiteChip({ sourceId, source, active, onCite }: CiteChipProps) {
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={() => onCite(sourceId)}
      className={cn(
        "mt-0.5 mr-1 inline-block rounded-sm px-2 py-0.5 text-[11.5px]",
        active ? "bg-navy text-white" : "bg-chip text-navy",
      )}
    >
      {citeLabel(source, sourceId)}
    </button>
  );
}
