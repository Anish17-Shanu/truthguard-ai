import { NextResponse } from "next/server";

import { backendFetch, backendUnavailable, forward, sessionToken } from "@/lib/backend";

export async function GET(request: Request) {
  if (!sessionToken()) {
    return NextResponse.json({ message: "Not authenticated" }, { status: 401 });
  }

  const { searchParams } = new URL(request.url);
  const page = Math.max(0, Number.parseInt(searchParams.get("page") ?? "0", 10) || 0);
  const size = Math.min(50, Math.max(1, Number.parseInt(searchParams.get("size") ?? "20", 10) || 20));

  let response: Response;
  try {
    response = await backendFetch(`/admin/feedback?page=${page}&size=${size}`);
  } catch {
    return backendUnavailable();
  }

  return forward(response);
}
