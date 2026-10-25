import { Suspense } from "react";
import { BottomTabs } from "./bottom-tabs";
import { CurrentBottomTabs, CurrentTopBar } from "./current-nav";
import { SiteFooter } from "./site-footer";
import { TopBar } from "./top-bar";

/** 공통 틀 — 메뉴는 주소를 읽으므로 Suspense 안. 대체 메뉴는 같은 모양에 현재 표시만 없어 화면이 밀리지 않는다. */
export function AppShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col">
      <Suspense fallback={<TopBar pathname={null} />}>
        <CurrentTopBar />
      </Suspense>
      <main className="flex-1">{children}</main>
      <SiteFooter />
      <Suspense fallback={<BottomTabs pathname={null} />}>
        <CurrentBottomTabs />
      </Suspense>
    </div>
  );
}
