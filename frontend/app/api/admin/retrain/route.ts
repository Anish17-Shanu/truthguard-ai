import { NextResponse } from "next/server";

import { backendFetch, backendUnavailable, checkOrigin, forward, sessionToken } from "@/lib/backend";

export async function POST(request: Request) {
  const originError = checkOrigin(request);
  if (originError) {
    return originError;
  }
  if (!sessionToken()) {
    return NextResponse.json({ message: "Not authenticated" }, { status: 401 });
  }

  let response: Response;
  try {
    response = await backendFetch("/admin/retrain", { method: "POST" });
  } catch {
    return backendUnavailable();
  }

  return forward(response);
}
