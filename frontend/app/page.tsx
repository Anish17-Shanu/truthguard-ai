import Link from "next/link";
import { BrainCircuit, FileSearch, Lock, Radar, ShieldCheck } from "lucide-react";

import { ThemeToggle } from "@/components/layout/theme-toggle";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

const features = [
  {
    title: "Fake news detection",
    icon: ShieldCheck,
    text: "A locally hosted classifier scores text as real, fake, or propaganda with a calibrated confidence."
  },
  {
    title: "Propaganda signals",
    icon: Radar,
    text: "Surface loaded language, sensational markers, bias cues, and emotional framing."
  },
  {
    title: "Explainable by design",
    icon: BrainCircuit,
    text: "Every result includes influential terms and suspicious phrases so you can see why it was flagged."
  },
  {
    title: "Private processing",
    icon: Lock,
    text: "No third-party AI APIs. Your content is processed by our own service and tied to your account only."
  },
  {
    title: "Source-aware ingestion",
    icon: FileSearch,
    text: "Analyze pasted text, a public article URL, or a .txt/.pdf document, with SSRF and size protections."
  }
];

const steps = [
  "Create an account and sign in.",
  "Paste text, submit a public URL, or upload a document.",
  "Review the classification, confidence, and highlighted signals.",
  "Send feedback to improve the next model evaluation."
];

export default function LandingPage() {
  return (
    <div className="min-h-screen bg-gradient-to-b from-sky-100 via-background to-cyan-50 dark:from-slate-900 dark:via-background dark:to-slate-900">
      <header className="mx-auto flex max-w-7xl items-center justify-between gap-3 px-6 py-5">
        <h1 className="whitespace-nowrap text-lg font-bold">TruthGuard AI</h1>
        <div className="flex items-center gap-2 sm:gap-3">
          <ThemeToggle />
          <Button asChild variant="outline" className="hidden sm:inline-flex">
            <Link href="/login">Login</Link>
          </Button>
          <Button asChild>
            <Link href="/signup">Get started</Link>
          </Button>
        </div>
      </header>

      <main className="mx-auto max-w-7xl px-6 pb-16 pt-10">
        <section className="mb-12 max-w-3xl">
          <h2 className="text-4xl font-black tracking-tight sm:text-5xl">
            Defense against disinformation at scale
          </h2>
          <p className="mt-5 text-lg text-muted-foreground">
            TruthGuard AI helps teams detect fake news, cyber propaganda, and biased narratives with
            explainable machine intelligence. English only at launch.
          </p>
        </section>

        <section className="mb-12 grid gap-5 md:grid-cols-2 lg:grid-cols-3">
          {features.map((feature) => {
            const Icon = feature.icon;
            return (
              <Card key={feature.title}>
                <CardHeader>
                  <CardTitle className="flex items-center gap-3 text-lg">
                    <Icon className="h-5 w-5 text-primary" />
                    {feature.title}
                  </CardTitle>
                </CardHeader>
                <CardContent className="text-sm text-muted-foreground">{feature.text}</CardContent>
              </Card>
            );
          })}
        </section>

        <section className="mb-12 rounded-2xl border bg-card p-6 shadow-sm">
          <h3 className="text-2xl font-bold">How it works</h3>
          <ol className="mt-4 grid gap-4 text-sm text-muted-foreground sm:grid-cols-2 lg:grid-cols-4">
            {steps.map((step, index) => (
              <li key={step} className="rounded-lg border border-border bg-muted/30 p-4">
                <span className="mb-2 block text-xs font-semibold uppercase tracking-wide text-primary">
                  Step {index + 1}
                </span>
                {step}
              </li>
            ))}
          </ol>
        </section>

        <section className="flex flex-col items-start gap-3 rounded-2xl border bg-card p-6 shadow-sm sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h3 className="text-2xl font-bold">Ready to analyze your first article?</h3>
            <p className="mt-1 text-sm text-muted-foreground">
              Create an account to save analyses and send feedback.
            </p>
          </div>
          <Button asChild>
            <Link href="/signup">Get started</Link>
          </Button>
        </section>
      </main>

      <footer className="border-t border-border/60 px-6 py-8">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-4 text-sm text-muted-foreground">
          <p>© {new Date().getFullYear()} TruthGuard AI</p>
          <nav aria-label="Legal" className="flex gap-4">
            <Link className="hover:text-foreground" href="/privacy">Privacy</Link>
            <Link className="hover:text-foreground" href="/terms">Terms</Link>
            <Link className="hover:text-foreground" href="/disclaimer">Disclaimer</Link>
          </nav>
        </div>
      </footer>
    </div>
  );
}
