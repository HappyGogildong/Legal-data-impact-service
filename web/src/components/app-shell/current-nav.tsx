"use client";

import { usePathname } from "next/navigation";
import { BottomTabs } from "./bottom-tabs";
import { TopBar } from "./top-bar";

/*
 * 지금 주소를 읽어 메뉴에 넘긴다. 동적 경로(/laws/[lawId]…)의 정적 껍데기에서 usePathname 은 요청 시점 데이터라
 * <Suspense> 안에서만 읽을 수 있다(Cache Components) — AppShell 이 같은 모양의 대체 메뉴(pathname=null)로 감싼다.
 */
export function CurrentTopBar() {
  return <TopBar pathname={usePathname()} />;
}

export function CurrentBottomTabs() {
  return <BottomTabs pathname={usePathname()} />;
}
