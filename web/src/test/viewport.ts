/** jsdom 용 matchMedia — desktop 이면 "(min-width: 1024px)" 만 참. */
export function mockViewport(desktop: boolean) {
  window.matchMedia = (query: string) =>
    ({
      matches: desktop && query.includes("min-width: 1024px"),
      media: query,
      onchange: null,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
      dispatchEvent: () => false,
    }) as MediaQueryList;
}
