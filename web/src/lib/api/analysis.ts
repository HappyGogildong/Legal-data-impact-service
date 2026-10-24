import { api } from "./client";
import { unwrap } from "./errors";
import type { AnalysisResponse, DataStatus, LawRefInput } from "./types";

export async function analyze(request: {
  query: string;
  lawRef?: LawRefInput;
}): Promise<AnalysisResponse> {
  return unwrap(
    await api.POST("/api/v1/analyses", {
      body: { query: request.query, lawRef: request.lawRef },
    }),
  );
}

export async function fetchDataStatus(): Promise<DataStatus> {
  return unwrap(await api.GET("/api/v1/data-status"));
}
