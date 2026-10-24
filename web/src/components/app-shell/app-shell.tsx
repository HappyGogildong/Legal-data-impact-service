import { BottomTabs } from "./bottom-tabs";
import { SiteFooter } from "./site-footer";
import { TopBar } from "./top-bar";

export function AppShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col">
      <TopBar />
      <main className="flex-1">{children}</main>
      <SiteFooter />
      <BottomTabs />
    </div>
  );
}
