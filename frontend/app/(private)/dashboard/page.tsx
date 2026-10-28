"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { motion } from "framer-motion";

import { LabelBadge } from "@/components/analysis/label-badge";
import { TrendChart } from "@/components/charts/trend-chart";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { api, errorMessage } from "@/lib/api";
import type { Analysis, Paged, UserStats } from "@/lib/types";

export default function DashboardPage() {
  const [stats, setStats] = useState<UserStats | null>(null);
  const [recent, setRecent] = useState<Analysis[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [statsResult, historyResult] = await Promise.all([
        api<UserStats>("/stats/me"),
        api<Paged<Analysis>>("/history?page=0&size=5")
      ]);
      setStats(statsResult);
      setRecent(historyResult.items);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const chartData = useMemo(
    () => Object.entries(stats?.trendsByDate ?? {}).map(([date, scans]) => ({ date: date.slice(5), scans })),
    [stats]
  );

  if (loading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-10 w-60" />
        <div className="grid gap-4 md:grid-cols-4">
          {[0, 1, 2, 3].map((key) => (
            <Skeleton key={key} className="h-24 w-full" />
          ))}
        </div>
        <Skeleton className="h-80 w-full" />
      </div>
    );
  }

  if (error) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Dashboard unavailable</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p role="alert" className="text-sm text-red-500">{error}</p>
          <Button onClick={() => void load()}>Try again</Button>
        </CardContent>
      </Card>
    );
  }

  const items = [
    { label: "Total scans", value: stats?.totalScans ?? 0 },
    { label: "Fake", value: stats?.fakeCount ?? 0 },
    { label: "Real", value: stats?.realCount ?? 0 },
    { label: "Propaganda", value: stats?.propagandaCount ?? 0 }
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <motion.h1 initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} className="text-3xl font-bold">
          Your analysis overview
        </motion.h1>
        <Button asChild>
          <Link href="/detection">New analysis</Link>
        </Button>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 md:grid-cols-4">
        {items.map((item, index) => (
          <motion.div
            key={item.label}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: index * 0.08 }}
          >
            <Card>
              <CardHeader>
                <CardTitle className="text-sm text-muted-foreground">{item.label}</CardTitle>
              </CardHeader>
              <CardContent className="text-3xl font-black">{item.value}</CardContent>
            </Card>
          </motion.div>
        ))}
      </div>

      <Card>
        <CardHeader>
          <CardTitle>7-day scan trend</CardTitle>
        </CardHeader>
        <CardContent>
          <TrendChart data={chartData} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <div className="flex items-center justify-between gap-3">
            <CardTitle>Recent analyses</CardTitle>
            <Link href="/history" className="text-sm font-medium text-primary hover:underline">
              View all
            </Link>
          </div>
        </CardHeader>
        <CardContent>
          {recent.length === 0 ? (
            <div className="space-y-3 text-sm text-muted-foreground">
              <p>No analyses yet. Paste an article, URL, or upload a .txt/.pdf file to get started.</p>
              <Button asChild variant="outline">
                <Link href="/detection">Run your first analysis</Link>
              </Button>
            </div>
          ) : (
            <ul className="divide-y divide-border">
              {recent.map((item) => (
                <li key={item.analysisId}>
                  <Link
                    href={`/results/${item.analysisId}`}
                    className="flex items-center justify-between gap-3 py-3 text-sm transition hover:text-primary"
                  >
                    <span className="line-clamp-2">{item.inputSnippet ?? "Uploaded file"}</span>
                    <span className="flex shrink-0 items-center gap-3">
                      <LabelBadge label={item.label} />
                      <span className="text-xs text-muted-foreground">
                        {new Date(item.createdAt).toLocaleDateString()}
                      </span>
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
