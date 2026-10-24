"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { NAV_ITEMS, isActive } from "./nav-items";

/** 모바일(< 768px) 하단 탭. */
export function BottomTabs() {
  const pathname = usePathname();
  return (
    <nav
      aria-label="하단 메뉴"
      className="fixed inset-x-0 bottom-0 z-20 flex border-t border-line-soft bg-white md:hidden"
    >
      {NAV_ITEMS.map((item) => (
        <Link
          key={item.href}
          href={item.href}
          aria-current={isActive(pathname, item.href) ? "page" : undefined}
          className="flex-1 py-3 text-center text-xs text-faint aria-[current=page]:font-bold aria-[current=page]:text-navy"
        >
          {item.label}
        </Link>
      ))}
    </nav>
  );
}
