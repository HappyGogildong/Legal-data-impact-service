import { describe, expect, it } from "vitest";
import { wordDiff } from "./text-diff";

describe("wordDiff", () => {
  it("단어 단위로 같음·추가·삭제를 나눈다", () => {
    const parts = wordDiff(
      "승인권자는 심의할 수 있다.",
      "승인권자는 통합하여 심의할 수 있다.",
    );
    expect(
      parts
        .filter((part) => part.kind === "added")
        .map((part) => part.text.trim()),
    ).toEqual(["통합하여"]);
    expect(parts.some((part) => part.kind === "removed")).toBe(false);
    expect(
      parts
        .filter((part) => part.kind !== "removed")
        .map((part) => part.text)
        .join(""),
    ).toBe("승인권자는 통합하여 심의할 수 있다.");
  });
});
