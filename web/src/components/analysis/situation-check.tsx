"use client";

import type { CitedSource, SituationQuestion } from "@/lib/api/types";
import { CiteChip } from "./cite-chip";

const UNKNOWN = "모름";

/** 상황 질문 카드 묶음(D71) — 형식별 입력(예/아니오/모름 · 날짜 · 선택지)과 "대응 방안 확인하기". */
export function SituationCheck({
  questions,
  answers,
  onAnswer,
  onSubmit,
  pending,
  sources,
  selected,
  onCite,
}: {
  questions: SituationQuestion[];
  answers: Record<string, string>;
  onAnswer: (questionId: string, answer: string) => void;
  onSubmit: () => void;
  pending: boolean;
  sources: Map<string, CitedSource>;
  selected: string | null;
  onCite: (sourceId: string) => void;
}) {
  const answered = questions.some(
    (q) => q.id && answers[q.id] && answers[q.id] !== UNKNOWN,
  );
  return (
    <section className="rounded-lg border border-border bg-card p-3.5 text-[13px]">
      <h3 className="font-bold">상황을 알려주시면 더 정확해져요</h3>
      <ul className="mt-2 flex flex-col gap-3">
        {questions.map((q) =>
          q.id ? (
            <li key={q.id}>
              <p>
                {q.text}{" "}
                {q.basis && (
                  <CiteChip
                    sourceId={q.basis}
                    source={sources.get(q.basis)}
                    active={selected === q.basis}
                    onCite={onCite}
                  />
                )}
              </p>
              <AnswerInput
                question={q}
                value={answers[q.id]}
                onChange={(answer) => onAnswer(q.id!, answer)}
              />
            </li>
          ) : null,
        )}
      </ul>
      <button
        type="button"
        disabled={!answered || pending}
        onClick={onSubmit}
        className="mt-3 rounded-md bg-navy px-3 py-1.5 text-[12.5px] font-bold text-white disabled:opacity-40"
      >
        대응 방안 확인하기
      </button>
      <p className="mt-1 text-xs text-muted-foreground">
        답은 이 법령 판단에만 쓰고 저장하지 않아요
      </p>
    </section>
  );
}

function AnswerInput({
  question,
  value,
  onChange,
}: {
  question: SituationQuestion;
  value: string | undefined;
  onChange: (answer: string) => void;
}) {
  if (question.answerType === "DATE") {
    return (
      <div className="mt-1 flex items-center gap-2">
        <input
          type="date"
          aria-label={question.text}
          value={value && value !== UNKNOWN ? value : ""}
          onChange={(event) => onChange(event.target.value)}
          className="rounded-md border border-border px-2 py-1"
        />
        <Choice label={UNKNOWN} active={value === UNKNOWN} onPick={onChange} />
      </div>
    );
  }
  const options =
    question.answerType === "CHOICE"
      ? [...(question.options ?? []), UNKNOWN]
      : ["예", "아니오", UNKNOWN];
  return (
    <div className="mt-1 flex flex-wrap gap-1.5">
      {options.map((option) => (
        <Choice
          key={option}
          label={option}
          active={value === option}
          onPick={onChange}
        />
      ))}
    </div>
  );
}

function Choice({
  label,
  active,
  onPick,
}: {
  label: string;
  active: boolean;
  onPick: (answer: string) => void;
}) {
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={() => onPick(label)}
      className="rounded-md border border-border px-2.5 py-1 aria-pressed:border-navy aria-pressed:bg-chip aria-pressed:font-bold"
    >
      {label}
    </button>
  );
}
