import { NextResponse } from "next/server";

import {
  backendFetch,
  backendUnavailable,
  checkOrigin,
  forward,
  sessionToken
} from "@/lib/backend";

const ID_PATTERN = /^[A-Za-z0-9_-]{1,64}$/;

function invalidId(): NextResponse {
  return NextResponse.json({ message: "Invalid analysis id" }, { status: 400 });
}

export async function GET(_request: Request, { params }: { params: { id: string } }) {
  if (!sessionToken()) {
    return NextResponse.json({ message: "Not authenticated" }, { status: 401 });
  }
  if (!ID_PATTERN.test(params.id)) {
    return invalidId();
  }

  let response: Response;
  try {
    response = await backendFetch(`/history/${params.id}`);
  } catch {
    return backendUnavailable();
  }

  return forward(response);
}

export async function DELETE(request: Request, { params }: { params: { id: string } }) {
  const originError = checkOrigin(request);
  if (originError) {
    return originError;
  }
  if (!sessionToken()) {
    return NextResponse.json({ message: "Not authenticated" }, { status: 401 });
  }
  if (!ID_PATTERN.test(params.id)) {
    return invalidId();
  }

  let response: Response;
  try {
    response = await backendFetch(`/history/${params.id}`, { method: "DELETE" });
  } catch {
    return backendUnavailable();
  }

  if (!response.ok) {
    return forward(response);
  }
  return new NextResponse(null, { status: 204 });
}
