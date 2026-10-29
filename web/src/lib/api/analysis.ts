import type { AskRequest } from "@/lib/ask-store";
import { api } from "./client";
import { unwrap } from "./errors";
import type { AnalysisResponse, DataStatus } from "./types";

export async function analyze(
  request: AskRequest,
  signal?: AbortSignal,
): Promise<AnalysisResponse> {
  return unwrap(
    await api.POST("/api/v1/analyses", {
      body: {
        query: request.query,
        lawRef: request.lawRef,
        profileSearch: request.profileSearch,
      },
      signal,
    }),
  );
}

export async function fetchDataStatus(): Promise<DataStatus> {
  return unwrap(await api.GET("/api/v1/data-status"));
}
