"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { api } from "@/lib/api";

type Result = {
  analysisId: string;
  label: string;
  confidence: number;
  biasScore: number;
  emotion: string;
  language: string;
  highlightedText: string[];
  suspiciousPhrases: string[];
  modelVersion: string;
  createdAt: string;
};

export default function ResultPage() {
  const params = useParams<{ id: string }>();
  const [result, setResult] = useState<Result | null>(null);

  useEffect(() => {
    api<Result>(`/history/${params.id}`).then(setResult).catch(() => null);
  }, [params.id]);

  if (!result) {
    return <div className="text-sm text-muted-foreground">Loading analysis result...</div>;
  }

  return (
    <div className="grid gap-4 md:grid-cols-2">
      <Card>
        <CardHeader>
          <CardTitle>Classification</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2 text-sm">
          <p><span className="font-semibold">Label:</span> {result.label}</p>
          <p><span className="font-semibold">Confidence:</span> {(result.confidence * 100).toFixed(1)}%</p>
          <p><span className="font-semibold">Bias Score:</span> {(result.biasScore * 100).toFixed(1)}%</p>
          <p><span className="font-semibold">Emotion:</span> {result.emotion}</p>
          <p><span className="font-semibold">Language:</span> {result.language}</p>
          <p><span className="font-semibold">Model:</span> {result.modelVersion}</p>
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>Explainable AI Signals</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3 text-sm">
          <div>
            <p className="mb-1 font-semibold">Highlighted Terms</p>
            <div className="flex flex-wrap gap-2">
              {result.highlightedText.map((term) => (
                <span key={term} className="rounded-full bg-primary/15 px-2 py-1 text-xs font-medium text-primary">
                  {term}
                </span>
              ))}
            </div>
          </div>
          <div>
            <p className="mb-1 font-semibold">Suspicious Phrases</p>
            <ul className="list-inside list-disc text-muted-foreground">
              {result.suspiciousPhrases.map((phrase) => (
                <li key={phrase}>{phrase}</li>
              ))}
            </ul>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

