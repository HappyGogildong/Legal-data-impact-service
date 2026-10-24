"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useId, useState } from "react";
import { LoadError } from "@/components/auth/load-error";
import { ReconsentDialog } from "@/components/auth/reconsent-dialog";
import { SessionExpiredDialog } from "@/components/auth/session-expired-dialog";
import { fetchProfile, saveProfile } from "@/lib/api/auth";
import { ApiError, isUnauthorized } from "@/lib/api/errors";
import type { Profile, ProfileInput } from "@/lib/api/types";
import { formatSeoulDateTime } from "@/lib/dates";
import { clearReturnTo, readReturnTo } from "@/lib/return-to";
import { PROFILE_KEY, useRequireSession } from "@/lib/session";
import { cn } from "@/lib/utils";
import {
  AGE_WHY,
  MAX_AGE,
  MIN_AGE,
  OPTIONS,
  PURPOSES_WHY,
  SINGLE_FIELDS,
  type SingleFieldKey,
} from "./profile-fields";

type Values = { purposes: string[]; age: string } & Record<
  SingleFieldKey,
  string
>;

function toValues(profile: Profile | null): Values {
  return {
    purposes: profile?.purposes ?? [],
    age: profile?.age != null ? String(profile.age) : "",
    occupation: profile?.occupation ?? "",
    employmentType: profile?.employmentType ?? "",
    householdType: profile?.householdType ?? "",
    housingType: profile?.housingType ?? "",
    regionSido: profile?.regionSido ?? "",
  };
}

/** 고른 항목만 — 빈 값은 보내지 않아 서버에서 "입력 안 함"(null)이 된다. 전체 교체. */
function toInput(values: Values): ProfileInput {
  const input: ProfileInput = {};
  if (values.purposes.length > 0) input.purposes = values.purposes;
  if (values.age) input.age = Number(values.age);
  for (const field of SINGLE_FIELDS) {
    if (values[field.key]) input[field.key] = values[field.key];
  }
  return input;
}

/** S09 — 모든 항목 선택, 자유 입력 없음(D41). 소득은 받지 않는다. */
export function ProfileScreen() {
  const session = useRequireSession();
  const profile = useQuery({
    queryKey: PROFILE_KEY,
    queryFn: fetchProfile,
    enabled: session.data != null,
  });
  // 조회 실패 때 빈 폼을 보이면 저장(전체 교체)이 저장된 프로필을 지운다 — 오류 화면으로 막는다.
  // 백그라운드 재조회만 실패한 경우(데이터는 캐시에 있음)는 폼을 유지한다.
  if (session.isError && !session.data)
    return <LoadError onRetry={() => session.refetch()} />;
  if (profile.isError && !profile.data)
    return <LoadError onRetry={() => profile.refetch()} />;
  if (!session.data || profile.isPending) {
    return (
      <p
        role="status"
        className="px-4 py-12 text-center text-sm text-muted-foreground"
      >
        불러오는 중…
      </p>
    );
  }
  // key 를 두지 않는다 — 저장 성공 시 캐시가 갱신돼도 폼이 다시 만들어지지 않아야 "저장했어요" 상태가 유지된다.
  // 폼 값은 처음 열릴 때의 프로필로만 채우고, 입력 중 재조회로 덮어쓰지 않는다.
  return <ProfileForm initial={profile.data ?? null} />;
}

function ProfileForm({ initial }: { initial: Profile | null }) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const ageId = useId();
  const [values, setValues] = useState<Values>(() => toValues(initial));
  const [ageError, setAgeError] = useState(false);
  const [reconsentOpen, setReconsentOpen] = useState(false);
  const [expired, setExpired] = useState(false);
  const [saved, setSaved] = useState(false);

  const save = useMutation({
    mutationFn: saveProfile,
    onSuccess: (profile) => {
      queryClient.setQueryData(PROFILE_KEY, profile);
      queryClient.removeQueries({ queryKey: ["analysis"] }); // 프로필이 바뀌면 이전 분석을 다시 받는다
      setReconsentOpen(false);
      const target = readReturnTo();
      clearReturnTo();
      if (target) router.push(target);
      else setSaved(true);
    },
    onError: (error) => {
      if (error instanceof ApiError && error.status === 409)
        setReconsentOpen(true);
      else if (isUnauthorized(error)) setExpired(true);
    },
  });

  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const age = values.age ? Number(values.age) : null;
    const invalidAge =
      age !== null &&
      (!Number.isInteger(age) || age < MIN_AGE || age > MAX_AGE);
    setAgeError(invalidAge);
    if (!invalidAge) save.mutate(toInput(values));
  }

  function skip() {
    const target = readReturnTo() ?? "/me";
    clearReturnTo();
    router.push(target);
  }

  const togglePurpose = (purpose: string) =>
    setValues((current) => ({
      ...current,
      purposes: current.purposes.includes(purpose)
        ? current.purposes.filter((item) => item !== purpose)
        : [...current.purposes, purpose],
    }));

  if (saved) {
    return (
      <div className="mx-auto max-w-[480px] px-4 py-12 text-center">
        <h1 className="font-bold">저장했어요</h1>
        <p className="text-sm text-muted-foreground">
          이제 내 영향 분석에 이 프로필을 써요.
        </p>
        <Link
          href="/me"
          className="mt-3 inline-block text-[12.5px] font-bold text-navy"
        >
          내 정보로
        </Link>
      </div>
    );
  }

  return (
    <form
      noValidate
      onSubmit={submit}
      className="mx-auto max-w-[560px] px-4 py-5"
    >
      <div className="flex items-center justify-between">
        <h1 className="text-lg font-bold">내 프로필</h1>
        <button
          type="button"
          onClick={skip}
          className="text-[12.5px] font-bold text-navy"
        >
          건너뛰기
        </button>
      </div>
      <p className="text-sm text-muted-foreground">
        입력할수록 내 영향이 정확해져요. 모두 선택 입력이에요.
      </p>
      {initial?.updatedAt && (
        <p className="text-xs text-muted-foreground">
          마지막 수정 {formatSeoulDateTime(initial.updatedAt)}
        </p>
      )}

      <fieldset className="mt-4">
        <legend className="text-[13px] font-bold">
          이용 목적{" "}
          <span className="font-normal text-muted-foreground">(여러 개)</span>
        </legend>
        <div className="mt-1.5 flex flex-wrap gap-1.5">
          {OPTIONS.purposes.map((purpose) => (
            <button
              key={purpose}
              type="button"
              aria-pressed={values.purposes.includes(purpose)}
              onClick={() => togglePurpose(purpose)}
              className="rounded-full border border-transparent bg-chip px-2.5 py-1 text-xs text-navy aria-pressed:bg-navy aria-pressed:text-white"
            >
              {purpose}
            </button>
          ))}
        </div>
        <p className="mt-1 text-[11.5px] text-muted-foreground">
          {PURPOSES_WHY}
        </p>
      </fieldset>

      <div className="mt-4">
        <label htmlFor={ageId} className="block text-[13px] font-bold">
          나이
        </label>
        <input
          id={ageId}
          type="number"
          inputMode="numeric"
          min={MIN_AGE}
          max={MAX_AGE}
          value={values.age}
          onChange={(event) =>
            setValues((current) => ({ ...current, age: event.target.value }))
          }
          className={cn(
            "mt-1.5 w-24 rounded-md border px-2.5 py-1.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-navy",
            ageError ? "border-removed" : "border-border",
          )}
        />
        {ageError && (
          <p className="text-xs text-removed">
            나이는 14~120 사이로 입력해 주세요.
          </p>
        )}
        <p className="mt-1 text-[11.5px] text-muted-foreground">{AGE_WHY}</p>
      </div>

      {SINGLE_FIELDS.map((field) => (
        <SingleField
          key={field.key}
          field={field}
          value={values[field.key]}
          onChange={(value) =>
            setValues((current) => ({ ...current, [field.key]: value }))
          }
        />
      ))}

      {save.isError && !reconsentOpen && !expired && (
        <p className="mt-3 text-sm text-removed">
          저장하지 못했어요. 다시 시도해 주세요.
        </p>
      )}
      <button
        type="submit"
        disabled={save.isPending}
        className="mt-5 w-full rounded-md bg-navy py-2.5 text-[13px] font-bold text-white disabled:opacity-40"
      >
        저장하고 돌아가기
      </button>
      <p className="mt-2 text-center text-xs text-muted-foreground">
        이름·생년월일·연락처·상세 주소·소득은 받지 않아요. 소득에 따라 달라지는
        법령은 대응 방안에서 구간으로만 물어봐요.
      </p>

      <ReconsentDialog
        open={reconsentOpen}
        onAgreed={() => save.mutate(toInput(values))}
        onClose={() => setReconsentOpen(false)}
        onExpired={() => setExpired(true)}
      />
      <SessionExpiredDialog open={expired} onClose={() => setExpired(false)} />
    </form>
  );
}

function SingleField({
  field,
  value,
  onChange,
}: {
  field: (typeof SINGLE_FIELDS)[number];
  value: string;
  onChange: (value: string) => void;
}) {
  const id = useId();
  const choices = OPTIONS[field.key];
  return (
    <fieldset className="mt-4">
      <legend className="text-[13px] font-bold">{field.label}</legend>
      {field.control === "select" ? (
        <select
          aria-label={field.label}
          id={id}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          className="mt-1.5 rounded-md border border-border bg-white px-2.5 py-1.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-navy"
        >
          <option value="">선택 안 함</option>
          {choices.map((choice) => (
            <option key={choice} value={choice}>
              {choice}
            </option>
          ))}
        </select>
      ) : (
        <div
          role="radiogroup"
          aria-label={field.label}
          className="mt-1.5 flex flex-wrap gap-1.5"
        >
          {choices.map((choice) => (
            <label
              key={choice}
              className="cursor-pointer rounded-full border border-border bg-white px-2.5 py-1 text-xs has-checked:border-navy has-checked:bg-navy has-checked:text-white has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-navy"
            >
              <input
                type="radio"
                name={field.key}
                value={choice}
                checked={value === choice}
                onChange={() => onChange(choice)}
                onClick={() => {
                  if (value === choice) onChange(""); // 다시 누르면 선택 해제(모든 항목 선택 입력)
                }}
                className="sr-only"
              />
              {choice}
            </label>
          ))}
        </div>
      )}
      <p className="mt-1 text-[11.5px] text-muted-foreground">{field.why}</p>
    </fieldset>
  );
}
