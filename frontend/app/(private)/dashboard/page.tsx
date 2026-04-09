"use client";

import { useEffect, useMemo, useState } from "react";
import { motion } from "framer-motion";

import { TrendChart } from "@/components/charts/trend-chart";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { api } from "@/lib/api";

type HistoryItem = {
  analysisId: string;
  label: string;
  createdAt: string;
};

export default function DashboardPage() {
  const [history, setHistory] = useState<HistoryItem[]>([]);

  useEffect(() => {
    api<HistoryItem[]>("/history").then(setHistory).catch(() => null);
  }, []);

  const stats = useMemo(() => {
    const totalScans = history.length;
    const fakeCount = history.filter((i) => i.label.toLowerCase() === "fake").length;
    const realCount = history.filter((i) => i.label.toLowerCase() === "real").length;
    const propagandaCount = history.filter((i) => i.label.toLowerCase() === "propaganda").length;
    const trendsByDate: Record<string, number> = {};
    history.forEach((item) => {
      const key = item.createdAt.slice(5, 10);
      trendsByDate[key] = (trendsByDate[key] ?? 0) + 1;
    });
    return { totalScans, fakeCount, realCount, propagandaCount, trendsByDate };
  }, [history]);

  const chartData = useMemo(() => Object.entries(stats.trendsByDate).map(([date, scans]) => ({ date, scans })), [stats]);

  const items = [
    { label: "Total Scans", value: stats.totalScans },
    { label: "Fake", value: stats.fakeCount },
    { label: "Real", value: stats.realCount },
    { label: "Propaganda", value: stats.propagandaCount }
  ];

  return (
    <div className="space-y-6">
      <motion.h1 initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} className="text-3xl font-bold">
        Threat Intelligence Dashboard
      </motion.h1>
      <div className="grid gap-4 md:grid-cols-4">
        {items.map((item, i) => (
          <motion.div key={item.label} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.08 }}>
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
          <CardTitle>7-Day Scan Trend</CardTitle>
        </CardHeader>
        <CardContent>
          <TrendChart data={chartData} />
        </CardContent>
      </Card>
    </div>
  );
}
