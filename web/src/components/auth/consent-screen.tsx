"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState } from "react";
import { agreeProfileConsent, fetchConsents } from "@/lib/api/auth";
import { isUnauthorized } from "@/lib/api/errors";
import type { Consents } from "@/lib/api/types";
import {
  clearReturnTo,
  readReturnTo,
  setReturnTo,
  takeReturnTo,
} from "@/lib/return-to";
import { CONSENTS_KEY, useRequireSession } from "@/lib/session";
import { CheckRow } from "./check-row";
import { LoadError } from "./load-error";
import { SessionExpiredDialog } from "./session-expired-dialog";

const SUMMARY = [
  ["수집 항목", "이용 목적·나이·직업 등 자기신고 속성"],
  ["목적", "내 영향·대응 방안 분석"],
  ["보관·파기", "동의 철회·계정 삭제 시 즉시 파기"],
  ["철회", "내 정보 → 프로필 삭제"],
];

/** S08 — 처음 동의(→ 프로필), 재동의(옛 버전 → 돌아올 경로), 만 14세 미만 안내, 거절. */
export function ConsentScreen() {
  const router = useRouter();
  const pathname = usePathname();
  const queryClient = useQueryClient();
  const session = useRequireSession();
  const consents = useQuery({
    queryKey: CONSENTS_KEY,
    queryFn: fetchConsents,
    enabled: session.data != null,
  });
  const [over14, setOver14] = useState(false);
  const [agreed, setAgreed] = useState(false);
  const [under14, setUnder14] = useState(false);
  const [expired, setExpired] = useState(false);

  const existing = consents.data?.profile;
  const reconsent = existing != null && !existing.upToDate;

  const leave = () => router.push(takeReturnTo(pathname) ?? "/");
  const agree = useMutation({
    mutationFn: agreeProfileConsent,
    onSuccess: (profile) => {
      queryClient.setQueryData<Consents>(CONSENTS_KEY, (old) => ({
        ...old,
        profile,
      }));
      // 동의 전에 받은 분석(PROFILE_REQUIRED·CONSENT_OUTDATED)이 캐시에 남으면 돌아간 화면이 새로 받지 못한다
      queryClient.removeQueries({ queryKey: ["analysis"] });
      if (reconsent) {
        router.replace(takeReturnTo(pathname) ?? "/");
      } else {
        // 첫 동의는 프로필 입력을 거친다 — 돌아올 경로는 프로필이 쓴다. 없으면 내 정보가 아니라 홈으로
        if (!readReturnTo()) setReturnTo("/");
        router.replace("/profile");
      }
    },
    onError: (error) => {
      if (isUnauthorized(error)) setExpired(true);
    },
  });

  // 백그라운드 재조회만 실패한 경우(데이터는 캐시에 있음)는 폼을 유지한다 — isError 만 보면 폼이 오류 화면으로 바뀐다.
  if (session.isError && !session.data)
    return <LoadError onRetry={() => session.refetch()} />;
  if (consents.isError && !consents.data)
    return <LoadError onRetry={() => consents.refetch()} />;
  if (!session.data || consents.isPending) {
    return (
      <p
        role="status"
        className="px-4 py-12 text-center text-sm text-muted-foreground"
      >
        불러오는 중…
      </p>
    );
  }

  if (under14) {
    return (
      <div className="mx-auto max-w-[420px] px-4 py-12 text-center">
        <p aria-hidden className="text-3xl">
          🙏
        </p>
        <h1 className="mt-2 font-bold">개인화 기능을 이용할 수 없어요</h1>
        <p className="text-sm text-muted-foreground">
          LIA의 프로필·내 영향 기능은 만 14세 이상만 쓸 수 있어요. 요약·변경점은
          계속 볼 수 있어요.
        </p>
        <div className="mt-4 flex flex-col gap-2">
          <Link
            href="/"
            onClick={clearReturnTo}
            className="rounded-md bg-navy py-2.5 text-[13px] font-bold text-white"
          >
            홈으로
          </Link>
          <Link
            href="/me#delete-account"
            className="rounded-md border border-border bg-white py-2.5 text-[13px] font-bold text-removed"
          >
            계정 삭제하기
          </Link>
        </div>
      </div>
    );
  }

  // 첫 동의 직후 캐시가 최신으로 바뀌어도 "이미 동의" 화면이 잠깐 뜨지 않게 — 이동 중에는 폼을 유지한다.
  if (existing?.upToDate && !agree.isSuccess) {
    return (
      <div className="mx-auto max-w-[420px] px-4 py-12 text-center">
        <h1 className="font-bold">이미 최신 처리방침에 동의하셨어요</h1>
        <button
          type="button"
          onClick={leave}
          className="mt-3 text-[12.5px] font-bold text-navy"
        >
          돌아가기
        </button>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-[480px] px-4 py-6">
      <h1 className="text-lg font-bold">
        {reconsent
          ? "개인정보처리방침이 바뀌었어요"
          : "프로필을 쓰기 전에 확인해 주세요"}
      </h1>
      {reconsent && (
        <p className="text-sm text-muted-foreground">
          이전에 동의하신 {existing?.policyVersion} 버전과 달라요. 바뀐 내용을
          확인해 주세요.
        </p>
      )}
      <dl className="mt-3 rounded-lg border border-border bg-card px-3.5 text-sm">
        {SUMMARY.map(([term, value]) => (
          <div
            key={term}
            className="flex justify-between gap-3 border-b border-line-soft py-2.5 last:border-0"
          >
            <dt className="text-muted-foreground">{term}</dt>
            <dd className="text-right">{value}</dd>
          </div>
        ))}
      </dl>
      <Link
        href="/info#privacy"
        className="mt-2 inline-block text-[12.5px] font-bold text-navy"
      >
        개인정보처리방침 보기 →
      </Link>

      <CheckRow checked={over14} onChange={setOver14}>
        만 14세 이상입니다 <span className="text-muted-foreground">(필수)</span>
      </CheckRow>
      <CheckRow checked={agreed} onChange={setAgreed}>
        {reconsent
          ? "바뀐 처리방침에 동의합니다"
          : "프로필 수집·이용에 동의합니다"}{" "}
        <span className="text-muted-foreground">(필수)</span>
      </CheckRow>
      {agree.isError && !expired && (
        <p className="text-sm text-removed">
          동의를 저장하지 못했어요. 다시 시도해 주세요.
        </p>
      )}

      <div className="mt-3 flex flex-col gap-2">
        <button
          type="button"
          disabled={!over14 || !agreed || agree.isPending || agree.isSuccess}
          onClick={() => agree.mutate()}
          className="rounded-md bg-navy py-2.5 text-[13px] font-bold text-white disabled:opacity-40"
        >
          {reconsent ? "동의하기" : "동의하고 프로필 입력"}
        </button>
        <button
          type="button"
          disabled={agree.isSuccess}
          onClick={leave}
          className="rounded-md border border-border bg-white py-2.5 text-[13px] font-bold disabled:opacity-40"
        >
          {reconsent ? "나중에" : "동의하지 않고 계속 보기"}
        </button>
        <p className="text-center text-xs text-muted-foreground">
          {reconsent
            ? "동의하지 않으면 내 영향·대응 방안을 쓸 수 없어요."
            : "동의하지 않아도 요약·변경점은 계속 볼 수 있어요."}
        </p>
        <button
          type="button"
          onClick={() => setUnder14(true)}
          className="text-xs text-muted-foreground underline"
        >
          만 14세 미만이에요
        </button>
      </div>
      <SessionExpiredDialog open={expired} onClose={() => setExpired(false)} />
    </div>
  );
}
