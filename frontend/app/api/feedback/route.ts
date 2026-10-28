import { NextResponse } from "next/server";

import { backendFetch, backendUnavailable, checkOrigin, forward } from "@/lib/backend";

const LABELS = new Set(["real", "fake", "propaganda", "uncertain"]);

export async function POST(request: Request) {
  const originError = checkOrigin(request);
  if (originError) {
    return originError;
  }

  let body: {
    analysisId?: unknown;
    correct?: unknown;
    correctedLabel?: unknown;
    comment?: unknown;
  };
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ message: "Invalid request body" }, { status: 400 });
  }

  if (typeof body.analysisId !== "string" || body.analysisId.length === 0) {
    return NextResponse.json({ message: "analysisId is required" }, { status: 400 });
  }
  if (typeof body.correct !== "boolean") {
    return NextResponse.json({ message: "correct must be true or false" }, { status: 400 });
  }
  const correctedLabel =
    typeof body.correctedLabel === "string" && LABELS.has(body.correctedLabel)
      ? body.correctedLabel
      : null;
  if (!body.correct && !correctedLabel) {
    return NextResponse.json({ message: "Choose the correct label" }, { status: 400 });
  }
  const comment = typeof body.comment === "string" ? body.comment.slice(0, 1000) : null;

  let response: Response;
  try {
    response = await backendFetch("/feedback", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        analysisId: body.analysisId,
        correct: body.correct,
        correctedLabel,
        comment
      })
    });
  } catch {
    return backendUnavailable();
  }

  return forward(response);
}
