import Link from "next/link";
import { Suspense } from "react";
import { TrendingLaws } from "@/components/laws/trending-laws";
import { UpcomingLaws } from "@/components/laws/upcoming-laws";
import { ExampleChips } from "@/components/question/example-chips";
import { QuestionBox } from "@/components/question/question-box";

const PROMISES = [
  {
    title: "근거 조문과 함께",
    body: "모든 설명에 조문 근거를 달아요. 근거가 약하면 불확실하다고 알려요.",
  },
  {
    title: "곧 시행될 법령만",
    body: "공포됐지만 아직 시행 전인 법령을 다뤄요.",
  },
  {
    title: "로그인 없이도",
    body: "요약과 바뀐 점은 바로 볼 수 있어요. 내 영향은 로그인 후에.",
  },
];

export default function HomePage() {
  return (
    <>
      <section className="bg-navy text-white">
        <div className="mx-auto max-w-[1120px] px-4 pt-2 pb-5 lg:py-10">
          <h1 className="mt-2 mb-3 text-xl leading-snug font-bold lg:text-2xl">
            곧 바뀌는 법,
            <br />
            나에게 무슨 영향이 있을까요?
          </h1>
          <div className="max-w-[640px]">
            <QuestionBox />
            <ExampleChips />
          </div>
        </div>
      </section>
      <div className="mx-auto grid max-w-[1120px] gap-2.5 px-4 pt-5 empty:hidden md:grid-cols-2">
        <Suspense fallback={null}>
          <UpcomingLaws />
        </Suspense>
        <Suspense fallback={null}>
          <TrendingLaws />
        </Suspense>
      </div>
      <section className="mx-auto grid max-w-[1120px] gap-2.5 px-4 py-5 md:grid-cols-3">
        {PROMISES.map((promise) => (
          <div
            key={promise.title}
            className="rounded-lg border border-border bg-card p-3.5"
          >
            <h2 className="font-bold">{promise.title}</h2>
            <p className="text-[13px] text-muted-foreground">{promise.body}</p>
          </div>
        ))}
        <Link
          href="/info"
          className="text-[12.5px] font-bold text-navy md:col-span-3"
        >
          LIA가 답을 만드는 방식 →
        </Link>
      </section>
    </>
  );
}
