import Link from "next/link";

import { ThemeToggle } from "@/components/layout/theme-toggle";

export function LegalShell({
  title,
  updated,
  children
}: {
  title: string;
  updated: string;
  children: React.ReactNode;
}) {
  return (
    <div className="min-h-screen bg-background">
      <header className="mx-auto flex max-w-3xl items-center justify-between px-6 py-5">
        <Link href="/" className="text-lg font-bold">TruthGuard AI</Link>
        <ThemeToggle />
      </header>
      <main className="mx-auto max-w-3xl px-6 pb-16">
        <h1 className="text-3xl font-bold">{title}</h1>
        <p className="mt-1 text-sm text-muted-foreground">Last updated: {updated}</p>
        <div className="prose prose-sm mt-8 max-w-none space-y-4 text-sm leading-6 text-muted-foreground [&_h2]:mt-8 [&_h2]:text-lg [&_h2]:font-semibold [&_h2]:text-foreground [&_ul]:list-disc [&_ul]:pl-6">
          {children}
        </div>
        <p className="mt-10 text-sm">
          <Link href="/" className="text-primary hover:underline">← Back to home</Link>
        </p>
      </main>
    </div>
  );
}
