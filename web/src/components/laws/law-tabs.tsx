import Link from "next/link";
import type { LawTab } from "@/lib/law-view";
import { cn } from "@/lib/utils";

const TABS: { key: LawTab; label: string; href: string }[] = [
  { key: "upcoming", label: "곧 시행", href: "/laws" },
  { key: "trending", label: "자주 찾는", href: "/laws?tab=trending" },
];

/** S06 탭 — 링크라 서버 렌더링·뒤로 가기가 그대로 된다. */
export function LawTabs({ tab }: { tab: LawTab }) {
  return (
    <nav aria-label="법령 목록" className="mb-3 flex border-b border-line-soft">
      {TABS.map((item) => (
        <Link
          key={item.key}
          href={item.href}
          aria-current={item.key === tab ? "page" : undefined}
          className={cn(
            "-mb-px border-b-2 px-3 py-2 text-sm",
            item.key === tab
              ? "border-navy font-bold text-navy"
              : "border-transparent text-muted-foreground",
          )}
        >
          {item.label}
        </Link>
      ))}
    </nav>
  );
}
