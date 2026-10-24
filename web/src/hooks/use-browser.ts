import { useSyncExternalStore } from "react";
import { seoulDate } from "@/lib/dates";

const noSubscription = () => () => {};

/** 서울 기준 오늘(YYYY-MM-DD). 서버 렌더링·하이드레이션 중엔 null — 렌더 중 new Date() 를 피한다(react-hooks/purity). */
export function useToday(): string | null {
  return useSyncExternalStore(
    noSubscription,
    () => seoulDate(new Date()),
    () => null,
  );
}

export function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    (onChange) => {
      const list = window.matchMedia(query);
      list.addEventListener("change", onChange);
      return () => list.removeEventListener("change", onChange);
    },
    () => window.matchMedia(query).matches,
    () => false,
  );
}

/** 2단 레이아웃 경계(visual-design §3: ≥ 1024px). */
export function useIsDesktop(): boolean {
  return useMediaQuery("(min-width: 1024px)");
}

/** 하이드레이션이 끝났는가 — 서버 스냅숏(false)과 브라우저 값(true)이 갈리는 것을 이용한다. */
export function useHydrated(): boolean {
  return useSyncExternalStore(
    noSubscription,
    () => true,
    () => false,
  );
}
