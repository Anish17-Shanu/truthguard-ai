"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";

import { LabelBadge } from "@/components/analysis/label-badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { api, errorMessage } from "@/lib/api";
import type { Analysis, Paged } from "@/lib/types";

const PAGE_SIZE = 10;

export default function HistoryPage() {
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Paged<Analysis> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async (targetPage: number) => {
    setLoading(true);
    setError("");
    try {
      const result = await api<Paged<Analysis>>(`/history?page=${targetPage}&size=${PAGE_SIZE}`);
      setData(result);
      setPage(result.page);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(0);
  }, [load]);

  const remove = async (id: string) => {
    if (!window.confirm("Delete this analysis? This cannot be undone.")) {
      return;
    }
    try {
      await api(`/history/${id}`, { method: "DELETE" });
      const nextPage = data && data.items.length === 1 && page > 0 ? page - 1 : page;
      await load(nextPage);
    } catch (err) {
      setError(errorMessage(err));
    }
  };

  const totalPages = data ? Math.max(1, Math.ceil(data.total / PAGE_SIZE)) : 1;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-3xl font-bold">Analysis history</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {data ? `${data.total} total analyses` : "Loading your analyses..."}
          </p>
        </div>
        <Button asChild>
          <Link href="/detection">New analysis</Link>
        </Button>
      </div>

      {error && (
        <p role="alert" className="rounded-md border border-red-500/40 bg-red-500/10 p-3 text-sm text-red-700 dark:text-red-300">
          {error}
        </p>
      )}

      {loading ? (
        <div className="space-y-3">
          {[0, 1, 2, 3].map((key) => (
            <Skeleton key={key} className="h-20 w-full" />
          ))}
        </div>
      ) : !data || data.items.length === 0 ? (
        <Card>
          <CardHeader>
            <CardTitle>No analyses yet</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4 text-sm text-muted-foreground">
            <p>Analyze an article, public URL, or .txt/.pdf document to see it here.</p>
            <Button asChild variant="outline">
              <Link href="/detection">Run an analysis</Link>
            </Button>
          </CardContent>
        </Card>
      ) : (
        <Card>
          <CardContent className="pt-6">
            <ul className="divide-y divide-border">
              {data.items.map((item) => (
                <li key={item.analysisId} className="flex flex-wrap items-center justify-between gap-3 py-4">
                  <Link href={`/results/${item.analysisId}`} className="min-w-0 flex-1 transition hover:text-primary">
                    <p className="line-clamp-2 text-sm font-medium">{item.inputSnippet ?? "Uploaded file"}</p>
                    <p className="mt-1 text-xs text-muted-foreground">
                      {new Date(item.createdAt).toLocaleString()} · {item.sourceType} ·{" "}
                      {(item.confidence * 100).toFixed(1)}% confidence
                    </p>
                  </Link>
                  <div className="flex items-center gap-3">
                    <LabelBadge label={item.label} />
                    <Button variant="outline" size="sm" onClick={() => remove(item.analysisId)}>
                      Delete
                    </Button>
                  </div>
                </li>
              ))}
            </ul>

            <div className="mt-6 flex items-center justify-between">
              <Button
                variant="outline"
                size="sm"
                disabled={page === 0}
                onClick={() => void load(page - 1)}
              >
                Previous
              </Button>
              <span className="text-sm text-muted-foreground">
                Page {page + 1} of {totalPages}
              </span>
              <Button
                variant="outline"
                size="sm"
                disabled={page + 1 >= totalPages}
                onClick={() => void load(page + 1)}
              >
                Next
              </Button>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
