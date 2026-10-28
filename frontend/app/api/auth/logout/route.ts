import { NextResponse } from "next/server";

import { backendFetch, checkOrigin, clearSessionCookie, sessionToken } from "@/lib/backend";

export async function POST(request: Request) {
  const originError = checkOrigin(request);
  if (originError) {
    return originError;
  }

  const token = sessionToken();
  if (token) {
    try {
      await backendFetch("/auth/logout", { method: "POST" });
    } catch {
      // Clearing the local cookie still logs the user out of this browser.
    }
  }

  return clearSessionCookie(new NextResponse(null, { status: 204 }));
}
