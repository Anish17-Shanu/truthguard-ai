import { NextResponse } from "next/server";

import { backendFetch, backendUnavailable, checkOrigin, forward } from "@/lib/backend";

const MAX_UPLOAD_BYTES = 12 * 1024 * 1024;

export async function POST(request: Request) {
  const originError = checkOrigin(request);
  if (originError) {
    return originError;
  }

  const contentLength = Number(request.headers.get("content-length") ?? "0");
  if (contentLength > MAX_UPLOAD_BYTES) {
    return NextResponse.json({ message: "Upload exceeds the 10 MB limit" }, { status: 413 });
  }

  let formData: FormData;
  try {
    formData = await request.formData();
  } catch {
    return NextResponse.json({ message: "Invalid form submission" }, { status: 400 });
  }

  let response: Response;
  try {
    response = await backendFetch("/analyze", { method: "POST", body: formData });
  } catch {
    return backendUnavailable();
  }

  return forward(response);
}
