"use client";

import { useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { fetchConsents } from "@/lib/api/auth";
import { readReturnTo } from "@/lib/return-to";
import { CONSENTS_KEY, useSession } from "@/lib/session";

/**
 * 소셜 로그인 착지(백엔드 성공 리다이렉트, 계획 1) — 동의 없음·옛 버전이면 동의(S08), 아니면 돌아올 경로.
 * 돌아올 경로는 읽기만 한다(effect 는 개발 모드에서 두 번 돈다).
 */
export function LoginDoneScreen() {
  const router = useRouter();
  const session = useSession();
  const signedIn = session.data != null;
  const consents = useQuery({
    queryKey: CONSENTS_KEY,
    queryFn: fetchConsents,
    enabled: signedIn,
  });

  useEffect(() => {
    // 익명, 또는 세션 확인 자체가 실패(5xx·네트워크)면 확인 중에 멈추지 않고 로그인 오류 안내로
    if (session.isError || (session.isSuccess && session.data === null)) {
      router.replace("/login?error=session");
      return;
    }
    if (consents.isSuccess) {
      const profileConsent = consents.data.profile;
      router.replace(
        !profileConsent || !profileConsent.upToDate
          ? "/consent"
          : (readReturnTo() ?? "/"),
      );
    } else if (consents.isError) {
      router.replace(readReturnTo() ?? "/");
    }
  }, [
    session.isError,
    session.isSuccess,
    session.data,
    consents.isSuccess,
    consents.isError,
    consents.data,
    router,
  ]);

  return (
    <div
      role="status"
      className="mx-auto max-w-[420px] px-4 py-12 text-center text-sm text-muted-foreground"
    >
      로그인 확인 중…
    </div>
  );
}
