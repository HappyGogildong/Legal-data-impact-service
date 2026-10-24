import { describe, expect, it } from "vitest";
import {
  clearReturnTo,
  isSafePath,
  readReturnTo,
  setReturnTo,
} from "./return-to";

describe("return-to", () => {
  it("같은 출처 경로만 돌려준다", () => {
    setReturnTo("/ask");
    expect(readReturnTo()).toBe("/ask");
    expect(readReturnTo()).toBe("/ask"); // 읽기는 지우지 않는다(StrictMode effect 두 번)
    clearReturnTo();
    expect(readReturnTo()).toBeNull();
  });

  it("외부·프로토콜 상대 주소는 거른다", () => {
    expect(isSafePath("//evil.example")).toBe(false);
    expect(isSafePath("/\\evil.example")).toBe(false);
    expect(isSafePath("https://evil.example")).toBe(false);
    expect(isSafePath("/me")).toBe(true);
    sessionStorage.setItem("lia.returnTo", "//evil.example");
    expect(readReturnTo()).toBeNull();
  });
});
