import Link from "next/link";
import { ShieldCheck, Radar, Languages, BrainCircuit } from "lucide-react";

import { ThemeToggle } from "@/components/layout/theme-toggle";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

const features = [
  { title: "Fake News Detection", icon: ShieldCheck, text: "Transformer-powered classification with confidence scoring." },
  { title: "Propaganda Intelligence", icon: Radar, text: "Detect manipulative framing, suspicious phrases, and bias." },
  { title: "Multilingual Analysis", icon: Languages, text: "Supports multilingual content via transformer embeddings." },
  { title: "Explainable AI", icon: BrainCircuit, text: "Highlights influential words for transparent decisions." }
];

export default function LandingPage() {
  return (
    <div className="min-h-screen bg-gradient-to-b from-sky-100 via-background to-cyan-50 dark:from-slate-900 dark:via-background dark:to-slate-900">
      <header className="mx-auto flex max-w-7xl items-center justify-between px-6 py-5">
        <h1 className="text-lg font-bold">TruthGuard AI</h1>
        <div className="flex items-center gap-3">
          <ThemeToggle />
          <Button asChild variant="outline">
            <Link href="/login">Login</Link>
          </Button>
          <Button asChild>
            <Link href="/signup">Get Started</Link>
          </Button>
        </div>
      </header>
      <main className="mx-auto max-w-7xl px-6 pb-16 pt-10">
        <section className="mb-12 max-w-3xl">
          <h2 className="text-5xl font-black tracking-tight">Defense Against Disinformation at Scale</h2>
          <p className="mt-5 text-lg text-muted-foreground">
            TruthGuard AI helps teams detect fake news, cyber propaganda, and biased narratives with explainable machine intelligence.
          </p>
        </section>
        <section className="grid gap-5 md:grid-cols-2">
          {features.map((f) => {
            const Icon = f.icon;
            return (
              <Card key={f.title}>
                <CardHeader>
                  <CardTitle className="flex items-center gap-3">
                    <Icon className="h-5 w-5 text-primary" />
                    {f.title}
                  </CardTitle>
                </CardHeader>
                <CardContent className="text-sm text-muted-foreground">{f.text}</CardContent>
              </Card>
            );
          })}
        </section>
      </main>
    </div>
  );
}

