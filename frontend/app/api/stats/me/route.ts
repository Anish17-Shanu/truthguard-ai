import { NextResponse } from "next/server";

import { backendFetch, backendUnavailable, forward, sessionToken } from "@/lib/backend";

export async function GET() {
  if (!sessionToken()) {
    return NextResponse.json({ message: "Not authenticated" }, { status: 401 });
  }

  let response: Response;
  try {
    response = await backendFetch("/stats/me");
  } catch {
    return backendUnavailable();
  }

  return forward(response);
}
