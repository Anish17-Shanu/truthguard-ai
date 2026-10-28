"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { ArrowLeft, Download } from "lucide-react";

import { LabelBadge } from "@/components/analysis/label-badge";
import { ModeNotice } from "@/components/analysis/mode-notice";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { api, errorMessage } from "@/lib/api";
import type { Analysis, ClassificationLabel } from "@/lib/types";

const correctedLabels: ClassificationLabel[] = ["real", "fake", "propaganda", "uncertain"];

export default function ResultPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const [result, setResult] = useState<Analysis | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [actionError, setActionError] = useState("");

  useEffect(() => {
    let active = true;
    api<Analysis>(`/history/${params.id}`)
      .then((data) => {
        if (active) setResult(data);
      })
      .catch((err) => {
        if (active) setError(errorMessage(err));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [params.id]);

  if (loading) {
    return <Skeleton className="h-96 w-full" />;
  }

  if (error || !result) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Analysis unavailable</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p role="alert" className="text-sm text-red-500">{error || "Analysis not found."}</p>
          <Button asChild variant="outline">
            <Link href="/history">Back to history</Link>
          </Button>
        </CardContent>
      </Card>
    );
  }

  const download = () => {
    const blob = new Blob([JSON.stringify(result, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `truthguard-${result.analysisId}.json`;
    link.click();
    URL.revokeObjectURL(url);
  };

  const remove = async () => {
    if (!window.confirm("Delete this analysis? This cannot be undone.")) {
      return;
    }
    setActionError("");
    try {
      await api(`/history/${result.analysisId}`, { method: "DELETE" });
      router.replace("/history");
      router.refresh();
    } catch (err) {
      setActionError(errorMessage(err));
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <Link href="/history" className="mb-2 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="h-4 w-4" />
            History
          </Link>
          <h1 className="text-3xl font-bold">Analysis result</h1>
          <p className="mt-1 text-xs text-muted-foreground">
            {new Date(result.createdAt).toLocaleString()} · {result.sourceType} · {result.modelVersion}
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={download}>
            <Download className="mr-2 h-4 w-4" />
            Export JSON
          </Button>
          <Button variant="outline" onClick={remove}>Delete analysis</Button>
        </div>
      </div>

      <ModeNotice mode={result.classifierMode} />

      {actionError && (
        <p role="alert" className="rounded-md border border-red-500/40 bg-red-500/10 p-3 text-sm text-red-700 dark:text-red-300">
          {actionError}
        </p>
      )}

      {result.inputSnippet && (
        <Card>
          <CardHeader>
            <CardTitle>Analyzed content</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="whitespace-pre-wrap break-words text-sm text-muted-foreground">{result.inputSnippet}</p>
          </CardContent>
        </Card>
      )}

      <div className="grid gap-4 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Classification</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3 text-sm">
            <div className="flex items-center gap-3">
              <span className="text-muted-foreground">Label</span>
              <LabelBadge label={result.label} />
            </div>
            <dl className="grid grid-cols-2 gap-2">
              <div><dt className="text-muted-foreground">Confidence</dt><dd className="font-semibold">{confidenceBand(result.confidence)} ({(result.confidence * 100).toFixed(1)}%)</dd></div>
              <div><dt className="text-muted-foreground">Bias score</dt><dd className="font-semibold">{(result.biasScore * 100).toFixed(1)}%</dd></div>
              <div><dt className="text-muted-foreground">Emotion</dt><dd className="font-semibold capitalize">{result.emotion}</dd></div>
              <div><dt className="text-muted-foreground">Language</dt><dd className="font-semibold uppercase">{result.language}</dd></div>
            </dl>
            <p className="text-xs text-muted-foreground">
              Confidence describes how certain the classifier is about its label, not whether the
              claim is true. Treat low-confidence results as inconclusive.
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Explainable signals</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4 text-sm">
            <div>
              <p className="mb-2 font-semibold">Highlighted terms</p>
              {result.highlightedText.length === 0 ? (
                <p className="text-muted-foreground">No influential terms detected.</p>
              ) : (
                <div className="flex flex-wrap gap-2">
                  {result.highlightedText.map((term) => (
                    <span key={term} className="rounded-full bg-primary/15 px-2 py-1 text-xs font-medium text-primary">
                      {term}
                    </span>
                  ))}
                </div>
              )}
            </div>
            <div>
              <p className="mb-2 font-semibold">Suspicious phrases</p>
              {result.suspiciousPhrases.length === 0 ? (
                <p className="text-muted-foreground">None found.</p>
              ) : (
                <ul className="list-inside list-disc text-muted-foreground">
                  {result.suspiciousPhrases.map((phrase) => (
                    <li key={phrase}>{phrase}</li>
                  ))}
                </ul>
              )}
            </div>
          </CardContent>
        </Card>
      </div>

      <FeedbackCard analysis={result} />
    </div>
  );
}

function confidenceBand(confidence: number): string {
  if (confidence >= 0.8) return "High confidence";
  if (confidence >= 0.55) return "Moderate confidence";
  return "Low confidence";
}

function FeedbackCard({ analysis }: { analysis: Analysis }) {
  const [verdict, setVerdict] = useState<"correct" | "incorrect" | null>(null);
  const [correctedLabel, setCorrectedLabel] = useState<ClassificationLabel>("real");
  const [comment, setComment] = useState("");
  const [state, setState] = useState<"idle" | "saving" | "saved">("idle");
  const [error, setError] = useState("");

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!verdict) {
      setError("Choose whether the result was correct.");
      return;
    }
    setState("saving");
    setError("");
    try {
      await api("/feedback", {
        method: "POST",
        body: JSON.stringify({
          analysisId: analysis.analysisId,
          correct: verdict === "correct",
          correctedLabel: verdict === "incorrect" ? correctedLabel : null,
          comment: comment.trim() || null
        })
      });
      setState("saved");
    } catch (err) {
      setError(errorMessage(err));
      setState("idle");
    }
  };

  if (state === "saved") {
    return (
      <Card>
        <CardContent className="pt-6">
          <p role="status" className="text-sm text-emerald-600 dark:text-emerald-400">
            Thanks — your feedback was recorded and will be used to evaluate the next model.
          </p>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Was this result useful?</CardTitle>
      </CardHeader>
      <CardContent>
        <form onSubmit={submit} className="space-y-4">
          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              variant={verdict === "correct" ? "default" : "outline"}
              onClick={() => setVerdict("correct")}
            >
              Yes, correct
            </Button>
            <Button
              type="button"
              variant={verdict === "incorrect" ? "default" : "outline"}
              onClick={() => setVerdict("incorrect")}
            >
              No, incorrect
            </Button>
          </div>

          {verdict === "incorrect" && (
            <div className="space-y-1">
              <label htmlFor="correctedLabel" className="text-sm font-medium">Correct label</label>
              <select
                id="correctedLabel"
                className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                value={correctedLabel}
                onChange={(event) => setCorrectedLabel(event.target.value as ClassificationLabel)}
              >
                {correctedLabels.map((label) => (
                  <option key={label} value={label}>{label}</option>
                ))}
              </select>
            </div>
          )}

          <div className="space-y-1">
            <label htmlFor="comment" className="text-sm font-medium">Comment (optional)</label>
            <textarea
              id="comment"
              className="min-h-20 w-full rounded-md border border-input bg-background p-3 text-sm"
              value={comment}
              onChange={(event) => setComment(event.target.value)}
              maxLength={1000}
              placeholder="What did the model get wrong?"
            />
          </div>

          {error && <p role="alert" className="text-sm text-red-500">{error}</p>}

          <Button disabled={state === "saving"}>
            {state === "saving" ? "Sending..." : "Submit feedback"}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
