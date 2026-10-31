"use client";

import Link from "next/link";
import { UnreadBadge } from "@/components/notifications/unread-badge";
import { QuestionBox } from "@/components/question/question-box";
import { NAV_ITEMS, isActive } from "./nav-items";

/** 상단 바 — 데스크톱(≥768px)은 메뉴, 홈이 아닌 화면은 작은 질문창(visual-design §3). pathname 이 null 이면 주소를 아직 모르는 정적 껍데기(AppShell). */
export function TopBar({ pathname }: { pathname: string | null }) {
  return (
    <header className="bg-navy text-white">
      <div className="mx-auto flex max-w-[1120px] items-center gap-3 px-4 py-3">
        <Link
          href="/"
          className="font-extrabold tracking-wide focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
        >
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
              aria-current={
                pathname !== null && isActive(pathname, item.href)
                  ? "page"
                  : undefined
              }
              className="opacity-90 hover:opacity-100 aria-[current=page]:font-bold aria-[current=page]:opacity-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
            >
              {item.label}
              {item.href === "/notifications" && <UnreadBadge />}
            </Link>
          ))}
        </nav>
      </div>
    </header>
  );
}
