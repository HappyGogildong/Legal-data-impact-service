"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useId, useState } from "react";
import { CheckRow } from "@/components/auth/check-row";
import { LoadError } from "@/components/auth/load-error";
import { SessionExpiredDialog } from "@/components/auth/session-expired-dialog";
import {
  agreeNotificationEmail,
  deleteAccount,
  deleteProfile,
  fetchConsents,
  fetchProfile,
  logout,
  withdrawNotificationEmail,
} from "@/lib/api/auth";
import { ApiError, isUnauthorized } from "@/lib/api/errors";
import type { Consents, Profile } from "@/lib/api/types";
import { formatSeoulDateTime } from "@/lib/dates";
import { CONSENTS_KEY, PROFILE_KEY, useRequireSession } from "@/lib/session";
import { ConfirmDialog } from "./confirm-dialog";

const PROVIDER_LABELS: Record<string, string> = {
  kakao: "카카오",
  naver: "네이버",
  google: "Google",
};
const PROFILE_FIELD_COUNT = 7;
const RETRY_MESSAGE = "요청을 처리하지 못했어요. 다시 시도해 주세요.";

function filledCount(profile: Profile): number {
  const singles = [
    profile.age,
    profile.occupation,
    profile.employmentType,
    profile.householdType,
    profile.housingType,
    profile.regionSido,
  ];
  return (
    (profile.purposes?.length ? 1 : 0) +
    singles.filter((value) => value != null && value !== "").length
  );
}

const dateOf = (iso: string | undefined) =>
  iso ? formatSeoulDateTime(iso).slice(0, 10) : "";

/** 401 이 아닌 실패 — 401 은 세션 만료 대화상자가 맡는다. */
const failedWithoutExpiry = (mutation: { isError: boolean; error: unknown }) =>
  mutation.isError && !isUnauthorized(mutation.error);

type Pending = "profile" | "account" | "email" | null;

/** S10 — 계정·프로필·동의 상태·설정·바로가기·계정 삭제(위험 영역). 프로필 삭제 = 동의 철회. */
export function MyScreen() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const session = useRequireSession();
  const emailSwitchLabelId = useId();
  const signedIn = session.data != null;
  const profile = useQuery({
    queryKey: PROFILE_KEY,
    queryFn: fetchProfile,
    enabled: signedIn,
  });
  const consents = useQuery({
    queryKey: CONSENTS_KEY,
    queryFn: fetchConsents,
    enabled: signedIn,
  });
  const [confirm, setConfirm] = useState<Pending>(null);
  const [emailAgreed, setEmailAgreed] = useState(false);
  const [emailError, setEmailError] = useState<string | null>(null);
  const [expired, setExpired] = useState(false);

  const onError = (error: unknown) => {
    if (isUnauthorized(error)) {
      setConfirm(null);
      setExpired(true);
    }
  };
  const leaveSignedOut = () => {
    queryClient.clear();
    router.replace("/");
  };
  const logoutMutation = useMutation({
    mutationFn: logout,
    onSuccess: leaveSignedOut,
    onError,
  });
  const deleteAccountMutation = useMutation({
    mutationFn: deleteAccount,
    onSuccess: leaveSignedOut,
    onError,
  });
  const deleteProfileMutation = useMutation({
    mutationFn: deleteProfile,
    onSuccess: () => {
      queryClient.setQueryData(PROFILE_KEY, null);
      queryClient.invalidateQueries({ queryKey: CONSENTS_KEY });
      queryClient.removeQueries({ queryKey: ["analysis"] });
      setConfirm(null);
    },
    onError,
  });
  const emailOn = useMutation({
    mutationFn: agreeNotificationEmail,
    onSuccess: (notificationEmail) => {
      queryClient.setQueryData<Consents>(CONSENTS_KEY, (old) => ({
        ...old,
        notificationEmail,
      }));
      setConfirm(null);
    },
    onError: (error) => {
      onError(error);
      if (isUnauthorized(error)) return;
      setEmailError(
        error instanceof ApiError && error.status === 400
          ? "로그인한 계정에 이메일이 없어 켤 수 없어요."
          : RETRY_MESSAGE,
      );
    },
  });
  const emailOff = useMutation({
    mutationFn: withdrawNotificationEmail,
    onSuccess: () =>
      queryClient.setQueryData<Consents>(CONSENTS_KEY, (old) => ({
        ...old,
        notificationEmail: undefined,
      })),
    onError,
  });

  if (session.isError && !session.data) {
    return <LoadError onRetry={() => session.refetch()} />;
  }
  if (!session.data) {
    return (
      <p
        role="status"
        className="px-4 py-12 text-center text-sm text-muted-foreground"
      >
        불러오는 중…
      </p>
    );
  }
  // fetchProfile 은 "프로필 없음"을 null 로 돌려주므로 undefined 와 구분한다.
  if (
    (profile.isError && profile.data === undefined) ||
    (consents.isError && consents.data === undefined)
  ) {
    return (
      <LoadError
        onRetry={() => {
          profile.refetch();
          consents.refetch();
        }}
      />
    );
  }
  if (profile.data === undefined || consents.data === undefined) {
    return (
      <p
        role="status"
        className="px-4 py-12 text-center text-sm text-muted-foreground"
      >
        불러오는 중…
      </p>
    );
  }
  const account = session.data;
  const profileConsent = consents.data.profile;
  const emailEnabled = consents.data.notificationEmail != null;

  return (
    <div className="mx-auto flex max-w-[560px] flex-col gap-2.5 px-4 py-5">
      <h1 className="text-lg font-bold">내 정보</h1>

      <section className="rounded-lg border border-border bg-card px-3.5 text-sm">
        <h2 className="pt-3 font-bold">계정</h2>
        <Row term="로그인 수단">
          {PROVIDER_LABELS[account.provider ?? ""] ?? account.provider}
        </Row>
        <Row term="가입일">{dateOf(account.createdAt)}</Row>
        <div className="py-2.5">
          <button
            type="button"
            onClick={() => logoutMutation.mutate()}
            disabled={logoutMutation.isPending}
            className="text-[12.5px] font-bold text-navy"
          >
            로그아웃
          </button>
          {failedWithoutExpiry(logoutMutation) && (
            <p className="mt-1 text-xs text-removed">{RETRY_MESSAGE}</p>
          )}
        </div>
      </section>

      <section className="rounded-lg border border-border bg-card p-3.5 text-sm">
        <div className="flex items-center justify-between">
          <h2 className="font-bold">
            프로필{" "}
            {profile.data && (
              <small className="font-normal text-muted-foreground">
                {PROFILE_FIELD_COUNT}개 중 {filledCount(profile.data)}개 입력
              </small>
            )}
          </h2>
          <Link href="/profile" className="text-[12.5px] font-bold text-navy">
            {profile.data ? "수정" : "입력"}
          </Link>
        </div>
        {profile.data && (
          <>
            <button
              type="button"
              onClick={() => setConfirm("profile")}
              className="mt-2 text-[12.5px] font-bold text-removed"
            >
              프로필 삭제
            </button>
            <p className="text-xs text-muted-foreground">
              프로필을 삭제하면 동의가 철회되고 내 영향·대응 방안을 볼 수
              없어요.
            </p>
          </>
        )}
      </section>

      <section className="rounded-lg border border-border bg-card px-3.5 text-sm">
        <h2 className="pt-3 font-bold">동의</h2>
        <Row term="프로필 수집·이용">
          {profileConsent ? (
            <>
              <span>
                {profileConsent.policyVersion} 버전 ·{" "}
                {dateOf(profileConsent.consentedAt)}
              </span>{" "}
              {profileConsent.upToDate ? (
                <span className="rounded-sm bg-added-bg px-1.5 py-0.5 text-[11px] font-bold text-added">
                  최신
                </span>
              ) : (
                <Link
                  href="/consent"
                  className="rounded-sm bg-amend-bg px-1.5 py-0.5 text-[11px] font-bold text-amend"
                >
                  재동의 필요
                </Link>
              )}
            </>
          ) : (
            <span className="text-muted-foreground">동의 안 함</span>
          )}
        </Row>
      </section>

      <section className="rounded-lg border border-border bg-card p-3.5 text-sm">
        <h2 className="font-bold">설정</h2>
        <div className="mt-2 flex items-center justify-between gap-3">
          <div>
            <p id={emailSwitchLabelId}>이메일 알림 받기</p>
            <p className="text-xs text-muted-foreground">
              켜면 알림을 로그인 계정 이메일로도 보내요(처음 켤 때 동의). 알림
              기능은 곧 제공돼요.
            </p>
            {consents.data.notificationEmail?.email && (
              <p className="text-xs text-muted-foreground">
                받는 주소: {consents.data.notificationEmail.email}
              </p>
            )}
            {failedWithoutExpiry(emailOff) && (
              <p className="text-xs text-removed">{RETRY_MESSAGE}</p>
            )}
          </div>
          <button
            type="button"
            role="switch"
            aria-checked={emailEnabled}
            aria-labelledby={emailSwitchLabelId}
            disabled={emailOff.isPending}
            onClick={() =>
              emailEnabled ? emailOff.mutate() : setConfirm("email")
            }
            className="relative h-5 w-[34px] flex-none rounded-full bg-border aria-checked:bg-navy"
          >
            <span
              aria-hidden
              className={`absolute top-0.5 size-4 rounded-full bg-white transition-all ${emailEnabled ? "left-4" : "left-0.5"}`}
            />
          </button>
        </div>
      </section>

      <nav
        aria-label="바로가기"
        className="rounded-lg border border-border bg-card px-3.5 text-sm"
      >
        <Link href="/info" className="flex justify-between py-3">
          안내 · 처리방침 <span aria-hidden>›</span>
        </Link>
      </nav>

      <section
        id="delete-account"
        className="rounded-lg border border-removed/40 bg-card p-3.5 text-sm"
      >
        <h2 className="font-bold text-removed">위험 영역</h2>
        <button
          type="button"
          onClick={() => setConfirm("account")}
          className="mt-2 rounded-md border border-removed/40 bg-white px-3 py-1.5 text-[12.5px] font-bold text-removed"
        >
          계정 삭제
        </button>
      </section>

      <ConfirmDialog
        open={confirm === "profile"}
        title="프로필을 삭제할까요?"
        description="동의가 철회되고 내 영향·대응 방안을 더 볼 수 없어요."
        confirmLabel="삭제"
        danger
        confirmDisabled={deleteProfileMutation.isPending}
        onConfirm={() => deleteProfileMutation.mutate()}
        onClose={() => {
          setConfirm(null);
          deleteProfileMutation.reset();
        }}
      >
        {failedWithoutExpiry(deleteProfileMutation) && (
          <p className="text-sm text-removed">{RETRY_MESSAGE}</p>
        )}
      </ConfirmDialog>
      <ConfirmDialog
        open={confirm === "account"}
        title="계정을 삭제할까요?"
        description="되돌릴 수 없어요. 프로필·구독·이력·북마크가 모두 삭제되고 로그아웃돼요."
        confirmLabel="삭제"
        danger
        confirmDisabled={deleteAccountMutation.isPending}
        onConfirm={() => deleteAccountMutation.mutate()}
        onClose={() => {
          setConfirm(null);
          deleteAccountMutation.reset();
        }}
      >
        {failedWithoutExpiry(deleteAccountMutation) && (
          <p className="text-sm text-removed">{RETRY_MESSAGE}</p>
        )}
      </ConfirmDialog>
      <ConfirmDialog
        open={confirm === "email"}
        title="이메일로도 알림 받기"
        description="로그인한 계정의 이메일을 알림 발송에만 써요. 언제든 끌 수 있어요."
        confirmLabel="동의하고 켜기"
        confirmDisabled={!emailAgreed || emailOn.isPending}
        onConfirm={() => emailOn.mutate()}
        onClose={() => {
          setConfirm(null);
          setEmailError(null);
        }}
      >
        <CheckRow checked={emailAgreed} onChange={setEmailAgreed}>
          알림 이메일 수신에 동의합니다
        </CheckRow>
        {emailError && <p className="text-sm text-removed">{emailError}</p>}
      </ConfirmDialog>
      <SessionExpiredDialog open={expired} onClose={() => setExpired(false)} />
    </div>
  );
}

function Row({ term, children }: { term: string; children: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3 border-b border-line-soft py-2.5 last:border-0">
      <span className="text-muted-foreground">{term}</span>
      <span className="text-right">{children}</span>
    </div>
  );
}
