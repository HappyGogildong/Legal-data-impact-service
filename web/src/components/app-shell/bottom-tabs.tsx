"use client";

import Link from "next/link";
import { UnreadBadge } from "@/components/notifications/unread-badge";
import { NAV_ITEMS, isActive } from "./nav-items";

/** 모바일(< 768px) 하단 탭. pathname 이 null 이면 주소를 아직 모르는 정적 껍데기(AppShell). */
export function BottomTabs({ pathname }: { pathname: string | null }) {
  return (
    <nav
      aria-label="하단 메뉴"
      className="fixed inset-x-0 bottom-0 z-20 flex border-t border-line-soft bg-white md:hidden"
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
          className="flex-1 py-3 text-center text-xs text-muted-foreground aria-[current=page]:font-bold aria-[current=page]:text-navy"
        >
          {item.label}
          {item.href === "/notifications" && <UnreadBadge />}
        </Link>
      ))}
    </nav>
  );
}
