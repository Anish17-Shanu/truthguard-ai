"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { BarChart3, Radar, ShieldAlert, Sparkles } from "lucide-react";

import { ThemeToggle } from "@/components/layout/theme-toggle";
import { cn } from "@/lib/utils";

const links = [
  { href: "/dashboard", label: "Dashboard", icon: BarChart3 },
  { href: "/detection", label: "Detection", icon: Radar },
  { href: "/admin", label: "Admin", icon: ShieldAlert }
];

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  return (
    <div className="min-h-screen bg-background">
      <div className="mx-auto grid max-w-7xl grid-cols-1 md:grid-cols-[240px_1fr]">
        <aside className="border-r border-border p-4">
          <div className="mb-8 flex items-center gap-2 text-lg font-bold">
            <Sparkles className="h-5 w-5" />
            TruthGuard AI
          </div>
          <nav className="space-y-2">
            {links.map((item) => {
              const Icon = item.icon;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={cn(
                    "flex items-center gap-2 rounded-md px-3 py-2 text-sm transition",
                    pathname.startsWith(item.href) ? "bg-primary text-primary-foreground" : "hover:bg-muted"
                  )}
                >
                  <Icon className="h-4 w-4" />
                  {item.label}
                </Link>
              );
            })}
          </nav>
        </aside>
        <main className="p-4 md:p-8">
          <header className="mb-6 flex items-center justify-end">
            <ThemeToggle />
          </header>
          {children}
        </main>
      </div>
    </div>
  );
}

