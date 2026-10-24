"use client";

import { useQuery } from "@tanstack/react-query";
import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import { fetchAccount } from "@/lib/api/auth";
import { setReturnTo } from "@/lib/return-to";

export const SESSION_KEY = ["session"] as const;
export const PROFILE_KEY = ["profile"] as const;
export const CONSENTS_KEY = ["consents"] as const;

/** 로그인 상태 — data 가 null 이면 익명. */
export function useSession() {
  return useQuery({
    queryKey: SESSION_KEY,
    queryFn: fetchAccount,
    staleTime: 5 * 60_000,
  });
}

/** 보호 화면 — 익명이면 지금 경로를 돌아올 경로로 두고 로그인으로. */
export function useRequireSession() {
  const session = useSession();
  const router = useRouter();
  const pathname = usePathname();
  const anonymous = session.isSuccess && session.data === null;
  useEffect(() => {
    if (anonymous) {
      setReturnTo(pathname);
      router.replace("/login");
    }
  }, [anonymous, pathname, router]);
  return session;
}
