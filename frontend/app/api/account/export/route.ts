import { NextResponse } from "next/server";

import { backendFetch, backendUnavailable, forward, sessionToken } from "@/lib/backend";

export async function GET() {
  if (!sessionToken()) {
    return NextResponse.json({ message: "Not authenticated" }, { status: 401 });
  }

  let response: Response;
  try {
    response = await backendFetch("/auth/me/export");
  } catch {
    return backendUnavailable();
  }

  if (!response.ok) {
    return forward(response);
  }

  const body = await response.text();
  return new NextResponse(body, {
    status: 200,
    headers: {
      "Content-Type": "application/json",
      "Content-Disposition": 'attachment; filename="truthguard-export.json"',
      "Cache-Control": "no-store"
    }
  });
}
