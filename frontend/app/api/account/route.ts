import { NextResponse } from "next/server";

import {
  backendFetch,
  backendUnavailable,
  checkOrigin,
  clearSessionCookie,
  forward,
  sessionToken
} from "@/lib/backend";

export async function DELETE(request: Request) {
  const originError = checkOrigin(request);
  if (originError) {
    return originError;
  }
  if (!sessionToken()) {
    return NextResponse.json({ message: "Not authenticated" }, { status: 401 });
  }

  let body: { password?: unknown };
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ message: "Password confirmation is required" }, { status: 400 });
  }
  if (typeof body.password !== "string" || body.password.length === 0) {
    return NextResponse.json({ message: "Password confirmation is required" }, { status: 400 });
  }

  let response: Response;
  try {
    response = await backendFetch("/auth/me", {
      method: "DELETE",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ password: body.password })
    });
  } catch {
    return backendUnavailable();
  }

  if (!response.ok) {
    return forward(response);
  }

  return clearSessionCookie(new NextResponse(null, { status: 204 }));
}
