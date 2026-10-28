"use client";

import { useCallback, useEffect, useState } from "react";

import { TrendChart } from "@/components/charts/trend-chart";
import { LabelBadge } from "@/components/analysis/label-badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { ApiError, api, errorMessage } from "@/lib/api";
import type { AdminStats, Analysis, FeedbackItem, Paged, RetrainResult } from "@/lib/types";

const FEEDBACK_PAGE_SIZE = 10;

export default function AdminPage() {
  const [stats, setStats] = useState<AdminStats | null>(null);
  const [feedbackPage, setFeedbackPage] = useState(0);
  const [feedback, setFeedback] = useState<Paged<FeedbackItem> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [retrainState, setRetrainState] = useState<"idle" | "running">("idle");
  const [retrainMessage, setRetrainMessage] = useState("");

  const load = useCallback(async (targetPage: number) => {
    setLoading(true);
    setError("");
    try {
      const [statsResult, feedbackResult] = await Promise.all([
        api<AdminStats>("/admin/stats"),
        api<Paged<FeedbackItem>>(`/admin/feedback?page=${targetPage}&size=${FEEDBACK_PAGE_SIZE}`)
      ]);
      setStats(statsResult);
      setFeedback(feedbackResult);
      setFeedbackPage(feedbackResult.page);
    } catch (err) {
      setError(err instanceof ApiError && err.status === 403 ? "Admin access required." : errorMessage(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(0);
  }, [load]);

  const retrain = async () => {
    if (!window.confirm("Queue a retraining run? The offline pipeline will process it.")) {
      return;
    }
    setRetrainState("running");
    setRetrainMessage("");
    try {
      const result = await api<RetrainResult>("/admin/retrain", { method: "POST" });
      setRetrainMessage(result.message ?? `Retraining status: ${result.status}`);
    } catch (err) {
      setRetrainMessage(errorMessage(err));
    } finally {
      setRetrainState("idle");
    }
  };

  if (loading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-10 w-60" />
        <div className="grid gap-4 md:grid-cols-4">
          {[0, 1, 2, 3].map((key) => <Skeleton key={key} className="h-24 w-full" />)}
        </div>
        <Skeleton className="h-80 w-full" />
      </div>
    );
  }

  if (error || !stats) {
    return (
      <Card>
        <CardHeader><CardTitle>Admin</CardTitle></CardHeader>
        <CardContent className="space-y-4">
          <p role="alert" className="text-sm text-red-500">{error || "Could not load admin data."}</p>
          <Button onClick={() => void load(0)}>Try again</Button>
        </CardContent>
      </Card>
    );
  }

  const cards = [
    { label: "Total scans", value: stats.totalScans },
    { label: "Scans today", value: stats.scansToday },
    { label: "Users", value: stats.users },
    { label: "Feedback", value: stats.feedbackCount }
  ];
  const chartData = Object.entries(stats.trendsByDate).map(([date, scans]) => ({ date: date.slice(5), scans }));
  const feedbackPages = feedback ? Math.max(1, Math.ceil(feedback.total / FEEDBACK_PAGE_SIZE)) : 1;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-3xl font-bold">Admin operations</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Active model: {stats.activeModelVersion}
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => void load(feedbackPage)}>Refresh</Button>
          <Button onClick={retrain} disabled={retrainState === "running"}>
            {retrainState === "running" ? "Requesting..." : "Request retraining"}
          </Button>
        </div>
      </div>

      {retrainMessage && (
        <p role="status" className="rounded-md border border-border bg-muted/40 p-3 text-sm">
          {retrainMessage}
        </p>
      )}

      <div className="grid gap-4 sm:grid-cols-2 md:grid-cols-4">
        {cards.map((card) => (
          <Card key={card.label}>
            <CardHeader><CardTitle className="text-sm text-muted-foreground">{card.label}</CardTitle></CardHeader>
            <CardContent className="text-3xl font-black">{card.value}</CardContent>
          </Card>
        ))}
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        <Card className="md:col-span-2">
          <CardHeader><CardTitle>7-day scan trend</CardTitle></CardHeader>
          <CardContent><TrendChart data={chartData} /></CardContent>
        </Card>
        <Card>
          <CardHeader><CardTitle>Label distribution</CardTitle></CardHeader>
          <CardContent className="space-y-3 text-sm">
            {(
              [
                ["real", stats.realCount],
                ["fake", stats.fakeCount],
                ["propaganda", stats.propagandaCount]
              ] as Array<[Analysis["label"], number]>
            ).map(([label, count]) => (
              <div key={label} className="flex items-center justify-between">
                <LabelBadge label={label} />
                <span className="font-semibold">{count}</span>
              </div>
            ))}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader><CardTitle>Feedback review</CardTitle></CardHeader>
        <CardContent>
          {!feedback || feedback.items.length === 0 ? (
            <p className="text-sm text-muted-foreground">No feedback submitted yet.</p>
          ) : (
            <>
              <div className="overflow-x-auto">
                <table className="w-full min-w-[720px] text-left text-sm">
                  <thead className="border-b border-border text-xs uppercase text-muted-foreground">
                    <tr>
                      <th className="py-2 pr-4">Content</th>
                      <th className="py-2 pr-4">Predicted</th>
                      <th className="py-2 pr-4">Verdict</th>
                      <th className="py-2 pr-4">Comment</th>
                      <th className="py-2">Date</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {feedback.items.map((item) => (
                      <tr key={item.id} className="align-top">
                        <td className="max-w-[280px] py-3 pr-4">
                          <p className="line-clamp-2">{item.inputSnippet ?? "—"}</p>
                          <p className="mt-1 text-xs text-muted-foreground">{item.modelVersion ?? "unknown model"}</p>
                        </td>
                        <td className="py-3 pr-4">{item.predictedLabel}</td>
                        <td className="py-3 pr-4">
                          {item.correct ? (
                            <span className="text-emerald-600 dark:text-emerald-400">Correct</span>
                          ) : (
                            <span className="text-red-600 dark:text-red-400">
                              Incorrect → {item.correctedLabel}
                            </span>
                          )}
                        </td>
                        <td className="max-w-[240px] py-3 pr-4 text-muted-foreground">{item.comment ?? "—"}</td>
                        <td className="py-3 text-xs text-muted-foreground">
                          {new Date(item.createdAt).toLocaleDateString()}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="mt-4 flex items-center justify-between">
                <Button variant="outline" size="sm" disabled={feedbackPage === 0} onClick={() => void load(feedbackPage - 1)}>
                  Previous
                </Button>
                <span className="text-sm text-muted-foreground">Page {feedbackPage + 1} of {feedbackPages}</span>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={feedbackPage + 1 >= feedbackPages}
                  onClick={() => void load(feedbackPage + 1)}
                >
                  Next
                </Button>
              </div>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
