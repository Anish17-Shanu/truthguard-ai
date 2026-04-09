"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";

const backendUrl = process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";

type AnalysisResult = {
  analysisId: string;
};

export default function DetectionPage() {
  const router = useRouter();
  const [text, setText] = useState("");
  const [url, setUrl] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError("");
    try {
      const formData = new FormData();
      if (text) formData.append("text", text);
      if (url) formData.append("url", url);
      if (file) formData.append("file", file);

      const token = localStorage.getItem("tg_token");
      const response = await fetch(`${backendUrl}/analyze`, {
        method: "POST",
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        body: formData
      });
      if (!response.ok) {
        const err = await response.json().catch(() => ({}));
        throw new Error(err.error ?? "Analysis failed");
      }
      const result = (await response.json()) as AnalysisResult;
      router.push(`/results/${result.analysisId}`);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>Analyze Content</CardTitle>
      </CardHeader>
      <CardContent>
        <form className="space-y-4" onSubmit={submit}>
          <textarea
            className="min-h-32 w-full rounded-md border border-input bg-background p-3 text-sm"
            placeholder="Paste article or post text"
            value={text}
            onChange={(e) => setText(e.target.value)}
          />
          <Input placeholder="Or provide URL to scan" value={url} onChange={(e) => setUrl(e.target.value)} />
          <Input type="file" accept=".txt,.pdf" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
          {error && <p className="text-sm text-red-500">{error}</p>}
          <Button disabled={loading}>{loading ? "Analyzing..." : "Run Detection"}</Button>
        </form>
      </CardContent>
    </Card>
  );
}

