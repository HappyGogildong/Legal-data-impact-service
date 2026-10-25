import { render, waitFor } from "@testing-library/react";
import { StrictMode } from "react";
import { describe, expect, it } from "vitest";
import { empty, mockApi } from "@/test/api-mock";
import { ViewBeacon } from "./view-beacon";

const KEY = "POST /api/v1/laws/001809/2026-11-03/views";

describe("ViewBeacon", () => {
  it("한 탭에서 같은 법령은 한 번만 보낸다(StrictMode·다시 그려도)", async () => {
    const requests = mockApi({ [KEY]: () => empty(204) });

    const { unmount } = render(
      <StrictMode>
        <ViewBeacon lawId="001809" effectiveDate="2026-11-03" />
      </StrictMode>,
    );
    await waitFor(() => expect(requests).toHaveLength(1));
    unmount();
    render(<ViewBeacon lawId="001809" effectiveDate="2026-11-03" />);

    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(requests).toHaveLength(1);
    expect(sessionStorage.getItem("lia.viewed")).toContain("001809@2026-11-03");
  });

  it("실패해도 아무것도 던지거나 그리지 않는다", async () => {
    const requests = mockApi({ [KEY]: () => empty(500) });
    const { container } = render(
      <ViewBeacon lawId="001809" effectiveDate="2026-11-03" />,
    );
    await waitFor(() => expect(requests).toHaveLength(1));
    expect(container).toBeEmptyDOMElement();
  });
});
