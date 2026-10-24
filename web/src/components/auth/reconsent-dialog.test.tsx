import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { empty, json, mockApi } from "@/test/api-mock";
import { ReconsentDialog } from "./reconsent-dialog";

const CONSENT = {
  policyVersion: "2026-12",
  consentedAt: "2026-10-24T01:00:00Z",
  upToDate: true,
};

/** 열린 대화상자를 그리고, 같은 트리에서 열림 상태만 바꿔 다시 그릴 수 있게 한다. */
function renderDialog() {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  const onAgreed = vi.fn();
  const onClose = vi.fn();
  const onExpired = vi.fn();
  const dialog = (open: boolean) => (
    <QueryClientProvider client={client}>
      <ReconsentDialog
        open={open}
        onAgreed={onAgreed}
        onClose={onClose}
        onExpired={onExpired}
      />
    </QueryClientProvider>
  );
  const view = render(dialog(true));
  return {
    onAgreed,
    onClose,
    onExpired,
    setOpen: (open: boolean) => view.rerender(dialog(open)),
  };
}

const ageCheckbox = () =>
  screen.getByRole("checkbox", { name: /만 14세 이상입니다/ });
const agreeCheckbox = () =>
  screen.getByRole("checkbox", { name: /프로필 수집·이용에 동의합니다/ });

describe("ReconsentDialog", () => {
  it("두 항목을 모두 체크해야 저장되고, 저장 성공 시 onAgreed 를 한 번 부른다", async () => {
    const requests = mockApi({
      "PUT /api/v1/consents/profile": () => json(CONSENT),
    });
    const { onAgreed } = renderDialog();
    const save = screen.getByRole("button", { name: "동의하고 저장" });

    expect(save).toBeDisabled();
    await userEvent.click(ageCheckbox());
    expect(save).toBeDisabled();
    await userEvent.click(agreeCheckbox());
    await userEvent.click(save);

    await vi.waitFor(() => expect(onAgreed).toHaveBeenCalledTimes(1));
    const put = requests.find((request) => request.method === "PUT")!;
    expect(await put.json()).toEqual({ over14: true });
  });

  it("저장 중 세션이 만료(401)되면 onExpired 를 부르고 일반 오류 문구는 보이지 않는다", async () => {
    mockApi({
      "PUT /api/v1/consents/profile": () =>
        json({ message: "로그인이 필요해요" }, 401),
    });
    const { onAgreed, onClose, onExpired } = renderDialog();

    await userEvent.click(ageCheckbox());
    await userEvent.click(agreeCheckbox());
    await userEvent.click(
      screen.getByRole("button", { name: "동의하고 저장" }),
    );

    await vi.waitFor(() => expect(onExpired).toHaveBeenCalledTimes(1));
    expect(onClose).toHaveBeenCalledTimes(1);
    expect(onAgreed).not.toHaveBeenCalled();
    expect(
      screen.queryByText("동의를 저장하지 못했어요. 다시 시도해 주세요."),
    ).not.toBeInTheDocument();
  });

  it("일반 오류(500)면 오류 문구를 보이고 onExpired 는 부르지 않는다", async () => {
    mockApi({
      "PUT /api/v1/consents/profile": () => empty(500),
    });
    const { onExpired } = renderDialog();

    await userEvent.click(ageCheckbox());
    await userEvent.click(agreeCheckbox());
    await userEvent.click(
      screen.getByRole("button", { name: "동의하고 저장" }),
    );

    expect(
      await screen.findByText("동의를 저장하지 못했어요. 다시 시도해 주세요."),
    ).toBeInTheDocument();
    expect(onExpired).not.toHaveBeenCalled();
  });

  it("체크한 뒤 닫았다가 다시 열면 나이·동의 체크가 비워져 있다", async () => {
    const { onClose, setOpen } = renderDialog();

    await userEvent.click(ageCheckbox());
    expect(ageCheckbox()).toBeChecked();
    await userEvent.click(screen.getByRole("button", { name: "나중에" }));
    expect(onClose).toHaveBeenCalledTimes(1);

    setOpen(false);
    setOpen(true);
    expect(ageCheckbox()).not.toBeChecked();
    expect(agreeCheckbox()).not.toBeChecked();
  });
});
