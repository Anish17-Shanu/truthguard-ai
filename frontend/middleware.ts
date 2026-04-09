import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

const privatePaths = ["/dashboard", "/detection", "/results", "/admin"];

export function middleware(request: NextRequest) {
  const token = request.cookies.get("tg_token")?.value;
  const needsAuth = privatePaths.some((path) => request.nextUrl.pathname.startsWith(path));
  if (needsAuth && !token) {
    return NextResponse.redirect(new URL("/login", request.url));
  }
  return NextResponse.next();
}

export const config = {
  matcher: ["/dashboard/:path*", "/detection/:path*", "/results/:path*", "/admin/:path*"]
};

