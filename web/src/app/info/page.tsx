import type { Metadata } from "next";
import { DataStatusCard } from "@/components/info/data-status-card";

export const metadata: Metadata = { title: "안내" };

/** S15 — 이용 안내·면책, 데이터 현황, 문의처, 개인정보처리방침 요약(전문은 법무 산출물 #35). */
export default function InfoPage() {
  return (
    <div className="mx-auto flex max-w-[760px] flex-col gap-2.5 px-4 py-5">
      <h1 className="text-lg font-bold">안내</h1>
      <DataStatusCard />
      <section className="rounded-lg border border-border bg-card p-3.5 text-[13px]">
        <h2 className="font-bold">LIA가 하는 일</h2>
        <p>
          곧 시행될 법령이 나에게 어떤 영향을 주는지, 근거 조문과 함께
          알려드려요.
        </p>
        <h2 className="mt-2 font-bold">하지 않는 일</h2>
        <p>
          법률 자문이 아니에요. 이미 시행 중인 법령이나 공포 전 법안은 다루지
          않아요.
        </p>
        <h2 className="mt-2 font-bold">답변을 만드는 방식</h2>
        <p>
          AI가 답을 만들되 모든 주장에 조문 근거를 달아요. 근거가 약한 부분은 ⚠
          불확실성으로 표시해요.
        </p>
      </section>
      <section
        id="contact"
        className="rounded-lg border border-border bg-card p-3.5 text-[13px]"
      >
        <h2 className="font-bold">문의</h2>
        <p>
          오류나 잘못된 안내를 알려 주세요 —{" "}
          <a
            href="mailto:rbgus630@gmail.com"
            className="font-bold text-navy underline"
          >
            rbgus630@gmail.com
          </a>
        </p>
      </section>
      <section
        id="privacy"
        className="rounded-lg border border-border bg-card p-3.5 text-[13px]"
      >
        <h2 className="font-bold">개인정보처리방침 요약</h2>
        <p>
          <b>수집하는 항목</b> — 자기신고 프로필(이용 목적·나이·직업·고용
          형태·가구 형태·주거 형태·거주 시도), 로그인 계정 식별값, 동의한 경우
          알림 이메일.
        </p>
        <p>
          <b>수집하지 않는 항목</b> — 이름·생년월일·주민등록번호·연락처·상세
          주소·소득.
        </p>
        <p>
          <b>처리 위탁</b> — Anthropic(질문·법령 분석, 내 영향 분석 시 프로필
          항목), OpenAI(질문의 검색용 임베딩, &quot;내 프로필 기준으로
          찾기&quot;를 켠 경우 프로필 항목 포함).
        </p>
        <p className="text-xs text-muted-foreground">
          전문과 이전 버전은 법무 검토(#35)를 마치면 이곳에 싣습니다.
        </p>
      </section>
    </div>
  );
}
