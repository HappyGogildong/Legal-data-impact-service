"use client";

import { useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { fetchConsents } from "@/lib/api/auth";
import { clearReturnTo, readReturnTo } from "@/lib/return-to";
import { CONSENTS_KEY, useSession } from "@/lib/session";

/**
 * 소셜 로그인 착지(백엔드 성공 리다이렉트, 계획 1) — 동의 없음·옛 버전이면 동의(S08), 아니면 돌아올 경로.
 * 돌아올 경로는 마운트 때 한 번 state 로 잡아 둔다 — 개발 모드(StrictMode)에서 effect 가 두 번 돌아도
 * state 는 유지되므로, 돌아올 경로로 떠나는 분기에서 지워도(멱등) 두 번째 실행이 값을 잃지 않는다.
 * 동의 화면(/consent)으로 가는 분기는 지우지 않는다 — 동의·프로필이 이어서 써야 한다.
 */
export function LoginDoneScreen() {
  const router = useRouter();
  const session = useSession();
  const [returnTo] = useState(readReturnTo);
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
      if (!profileConsent || !profileConsent.upToDate) {
        router.replace("/consent");
      } else {
        clearReturnTo();
        router.replace(returnTo ?? "/");
      }
    } else if (consents.isError) {
      clearReturnTo();
      router.replace(returnTo ?? "/");
    }
  }, [
    session.isError,
    session.isSuccess,
    session.data,
    consents.isSuccess,
    consents.isError,
    consents.data,
    returnTo,
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
