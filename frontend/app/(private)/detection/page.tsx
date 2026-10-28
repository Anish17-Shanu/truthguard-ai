"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { api, errorMessage } from "@/lib/api";
import { cn } from "@/lib/utils";
import type { Analysis } from "@/lib/types";

type Mode = "text" | "url" | "file";

const MAX_CHARS = 20_000;
const MAX_FILE_BYTES = 10 * 1024 * 1024;

const tabs: Array<{ id: Mode; label: string }> = [
  { id: "text", label: "Paste text" },
  { id: "url", label: "Article URL" },
  { id: "file", label: "Upload file" }
];

export default function DetectionPage() {
  const router = useRouter();
  const [mode, setMode] = useState<Mode>("text");
  const [text, setText] = useState("");
  const [url, setUrl] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError("");

    if (mode === "text" && text.trim().length < 20) {
      setError("Enter at least 20 characters of English text.");
      return;
    }
    if (mode === "text" && text.length > MAX_CHARS) {
      setError(`Text must be ${MAX_CHARS.toLocaleString()} characters or fewer.`);
      return;
    }
    if (mode === "url" && !url.trim()) {
      setError("Enter a public http(s) URL.");
      return;
    }
    if (mode === "file" && !file) {
      setError("Choose a .txt or .pdf file.");
      return;
    }
    if (file && file.size > MAX_FILE_BYTES) {
      setError("File must be 10 MB or smaller.");
      return;
    }

    setLoading(true);
    try {
      const formData = new FormData();
      if (mode === "text") formData.append("text", text);
      if (mode === "url") formData.append("url", url.trim());
      if (mode === "file" && file) formData.append("file", file);

      const result = await api<Analysis>("/analyze", { method: "POST", body: formData });
      router.push(`/results/${result.analysisId}`);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold">Analyze content</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          English only at launch. Content is processed by our own classifier; no third-party AI API is used.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Choose a source</CardTitle>
        </CardHeader>
        <CardContent>
          <div role="tablist" aria-label="Content source" className="mb-5 flex gap-2">
            {tabs.map((tab) => (
              <button
                key={tab.id}
                type="button"
                role="tab"
                aria-selected={mode === tab.id}
                onClick={() => {
                  setMode(tab.id);
                  setError("");
                }}
                className={cn(
                  "rounded-md px-3 py-2 text-sm font-medium transition",
                  mode === tab.id ? "bg-primary text-primary-foreground" : "bg-muted hover:bg-muted/70"
                )}
              >
                {tab.label}
              </button>
            ))}
          </div>

          <form className="space-y-4" onSubmit={submit}>
            {mode === "text" && (
              <div className="space-y-1">
                <label htmlFor="content" className="text-sm font-medium">Article or post text</label>
                <textarea
                  id="content"
                  className="min-h-40 w-full rounded-md border border-input bg-background p-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  placeholder="Paste the text you want to analyze..."
                  value={text}
                  onChange={(event) => setText(event.target.value)}
                  maxLength={MAX_CHARS}
                  required
                />
                <p className="text-xs text-muted-foreground">
                  {text.length.toLocaleString()} / {MAX_CHARS.toLocaleString()} characters
                </p>
              </div>
            )}

            {mode === "url" && (
              <div className="space-y-1">
                <label htmlFor="url" className="text-sm font-medium">Public article URL</label>
                <Input
                  id="url"
                  type="url"
                  placeholder="https://example.com/article"
                  value={url}
                  onChange={(event) => setUrl(event.target.value)}
                  required
                />
                <p className="text-xs text-muted-foreground">
                  The page must allow automated retrieval in robots.txt and be reachable over http(s).
                </p>
              </div>
            )}

            {mode === "file" && (
              <div className="space-y-1">
                <label htmlFor="file" className="text-sm font-medium">Text or PDF file</label>
                <Input
                  id="file"
                  type="file"
                  accept=".txt,.pdf"
                  onChange={(event) => setFile(event.target.files?.[0] ?? null)}
                  required
                />
                <p className="text-xs text-muted-foreground">.txt or .pdf, up to 10 MB.</p>
              </div>
            )}

            {error && (
              <p role="alert" className="rounded-md border border-red-500/40 bg-red-500/10 p-3 text-sm text-red-700 dark:text-red-300">
                {error}
              </p>
            )}

            <Button disabled={loading}>{loading ? "Analyzing..." : "Run analysis"}</Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
