"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { QuestionBox } from "@/components/question/question-box";
import { NAV_ITEMS, isActive } from "./nav-items";

/** 상단 바 — 데스크톱(≥768px)은 메뉴, 홈이 아닌 화면은 작은 질문창(visual-design §3). */
export function TopBar() {
  const pathname = usePathname();
  return (
    <header className="bg-navy text-white">
      <div className="mx-auto flex max-w-[1120px] items-center gap-3 px-4 py-3">
        <Link href="/" className="font-extrabold tracking-wide">
          LIA
        </Link>
        {pathname !== "/" && (
          <div className="hidden max-w-[420px] flex-1 md:block">
            <QuestionBox compact />
          </div>
        )}
        <nav
          aria-label="주 메뉴"
          className="ml-auto hidden gap-4 text-sm md:flex"
        >
          {NAV_ITEMS.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              aria-current={isActive(pathname, item.href) ? "page" : undefined}
              className="opacity-90 hover:opacity-100 aria-[current=page]:font-bold aria-[current=page]:opacity-100"
            >
              {item.label}
            </Link>
          ))}
        </nav>
      </div>
    </header>
  );
}
