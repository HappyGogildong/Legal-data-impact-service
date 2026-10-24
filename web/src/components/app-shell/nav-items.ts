/** 주 메뉴 — 1단계는 홈·안내(frontend-stack §5). 로그인 흐름이 "내 정보"를, S11(4단계)이 "알림"을 더한다. */
export const NAV_ITEMS: { href: string; label: string }[] = [
  { href: "/", label: "홈" },
  { href: "/info", label: "안내" },
];

export function isActive(pathname: string, href: string): boolean {
  return href === "/"
    ? pathname === "/"
    : pathname === href || pathname.startsWith(href + "/");
}
