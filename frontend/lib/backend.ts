import { cache } from "react";
import { cookies } from "next/headers";
import { NextResponse } from "next/server";

import type { SessionUser } from "@/lib/types";

export const SESSION_COOKIE = "tg_session";

export function backendUrl(): string {
  const url = process.env.BACKEND_URL ?? process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";
  return url.replace(/\/$/, "");
}

export function sessionToken(): string | null {
  return cookies().get(SESSION_COOKIE)?.value ?? null;
}

export async function backendFetch(
  path: string,
  init: RequestInit = {},
  token: string | null = sessionToken()
): Promise<Response> {
  const headers = new Headers(init.headers);
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }
  return fetch(`${backendUrl()}${path}`, {
    ...init,
    headers,
    cache: "no-store"
  });
}

export async function parseBackendBody(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!text) {
    return {};
  }
  try {
    return JSON.parse(text);
  } catch {
    return { message: "Unexpected response from the API" };
  }
}

export async function forward(response: Response): Promise<NextResponse> {
  const body = await parseBackendBody(response);
  return NextResponse.json(body, { status: response.status });
}

export function backendUnavailable(): NextResponse {
  return NextResponse.json(
    { code: "backend_unavailable", message: "The service is temporarily unavailable. Try again shortly." },
    { status: 503 }
  );
}

export function cookieOptions(maxAge: number) {
  return {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax" as const,
    path: "/",
    maxAge
  };
}

export function clearSessionCookie(response: NextResponse): NextResponse {
  response.cookies.set(SESSION_COOKIE, "", { ...cookieOptions(0), maxAge: 0 });
  return response;
}

export function checkOrigin(request: Request): NextResponse | null {
  const origin = request.headers.get("origin");
  if (!origin) {
    return null;
  }
  let originHost: string;
  try {
    originHost = new URL(origin).host;
  } catch {
    return NextResponse.json({ message: "Invalid request origin" }, { status: 403 });
  }
  const host = request.headers.get("x-forwarded-host") ?? request.headers.get("host");
  if (host && originHost === host) {
    return null;
  }
  const site = process.env.SITE_URL;
  if (site) {
    try {
      if (new URL(site).origin === origin) {
        return null;
      }
    } catch {
      // Ignore malformed configuration and fall through to rejection.
    }
  }
  return NextResponse.json({ message: "Cross-origin request rejected" }, { status: 403 });
}

export const getSessionUser = cache(async (): Promise<SessionUser | null> => {
  if (!sessionToken()) {
    return null;
  }
  try {
    const response = await backendFetch("/auth/me");
    if (!response.ok) {
      return null;
    }
    return (await response.json()) as SessionUser;
  } catch {
    return null;
  }
});
