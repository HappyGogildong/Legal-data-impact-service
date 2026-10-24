import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach, beforeEach, vi } from "vitest";
import { mockViewport } from "@/test/viewport";

beforeEach(() => {
  window.scrollTo = () => {}; // jsdom 은 scrollTo 를 구현하지 않는다
  mockViewport(false); // jsdom 엔 matchMedia 가 없다 — 기본은 모바일
});

afterEach(() => {
  cleanup();
  sessionStorage.clear();
  vi.restoreAllMocks();
  vi.useRealTimers();
});
