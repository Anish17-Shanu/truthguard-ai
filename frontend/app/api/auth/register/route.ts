import { NextResponse } from "next/server";

import {
  backendFetch,
  backendUnavailable,
  checkOrigin,
  cookieOptions,
  parseBackendBody,
  SESSION_COOKIE
} from "@/lib/backend";
import type { AuthPayload } from "@/lib/types";

type BackendAuth = AuthPayload & { accessToken: string };

export async function POST(request: Request) {
  const originError = checkOrigin(request);
  if (originError) {
    return originError;
  }

  let body: { fullName?: unknown; email?: unknown; password?: unknown };
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ message: "Invalid request body" }, { status: 400 });
  }
  if (
    typeof body.fullName !== "string" ||
    typeof body.email !== "string" ||
    typeof body.password !== "string"
  ) {
    return NextResponse.json({ message: "Name, email, and password are required" }, { status: 400 });
  }

  let response: Response;
  try {
    response = await backendFetch("/auth/register", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        fullName: body.fullName.trim(),
        email: body.email.trim(),
        password: body.password
      })
    });
  } catch {
    return backendUnavailable();
  }

  const payload = (await parseBackendBody(response)) as Partial<BackendAuth>;
  if (!response.ok) {
    return NextResponse.json(payload, { status: response.status });
  }
  if (
    !payload.accessToken ||
    typeof payload.expiresIn !== "number" ||
    !payload.userId ||
    !payload.fullName ||
    !payload.email ||
    (payload.role !== "USER" && payload.role !== "ADMIN")
  ) {
    return NextResponse.json({ message: "Invalid authentication response" }, { status: 502 });
  }

  const result = NextResponse.json({
    expiresIn: payload.expiresIn,
    userId: payload.userId,
    fullName: payload.fullName,
    email: payload.email,
    role: payload.role
  } satisfies AuthPayload);
  result.cookies.set(SESSION_COOKIE, payload.accessToken, cookieOptions(payload.expiresIn));
  return result;
}
