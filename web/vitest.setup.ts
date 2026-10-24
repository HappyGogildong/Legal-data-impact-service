import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach, beforeEach, vi } from "vitest";
import { mockViewport } from "@/test/viewport";

beforeEach(() => {
  mockViewport(false); // jsdom 엔 matchMedia 가 없다 — 기본은 모바일
});

afterEach(() => {
  cleanup();
  sessionStorage.clear();
  vi.restoreAllMocks();
  vi.useRealTimers();
});
