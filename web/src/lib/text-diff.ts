import { diffWords } from "diff";

export type DiffPart = { text: string; kind: "same" | "added" | "removed" };

/** 조문 대비 — 현행(before)·개정(after)을 단어 단위로. 현행 칸은 same+removed, 개정 칸은 same+added 를 그린다. */
export function wordDiff(before: string, after: string): DiffPart[] {
  return diffWords(before, after).map((change) => ({
    text: change.value,
    kind: change.added ? "added" : change.removed ? "removed" : "same",
  }));
}
