/** 주 메뉴 — 홈·안내·내 정보(frontend-stack §5). S11(4단계)이 "알림"을 더한다. 익명이 내 정보를 누르면 로그인으로. */
export const NAV_ITEMS: { href: string; label: string }[] = [
  { href: "/", label: "홈" },
  { href: "/info", label: "안내" },
  { href: "/me", label: "내 정보" },
];

export function isActive(pathname: string, href: string): boolean {
  return href === "/"
    ? pathname === "/"
    : pathname === href || pathname.startsWith(href + "/");
}
