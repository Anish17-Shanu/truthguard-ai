"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { BarChart3, History, Radar, Settings, ShieldAlert, Sparkles } from "lucide-react";

import { LogoutButton } from "@/components/layout/logout-button";
import { ThemeToggle } from "@/components/layout/theme-toggle";
import { cn } from "@/lib/utils";
import type { SessionUser } from "@/lib/types";

const links = [
  { href: "/dashboard", label: "Dashboard", icon: BarChart3 },
  { href: "/detection", label: "Detection", icon: Radar },
  { href: "/history", label: "History", icon: History },
  { href: "/settings", label: "Settings", icon: Settings }
];

export function AppShell({ children, user }: { children: React.ReactNode; user: SessionUser }) {
  const pathname = usePathname();
  const navigation = user.role === "ADMIN" ? [...links, { href: "/admin", label: "Admin", icon: ShieldAlert }] : links;

  return (
    <div className="min-h-screen bg-background">
      <div className="mx-auto grid max-w-7xl grid-cols-1 md:grid-cols-[240px_1fr]">
        <aside className="border-b border-border p-4 md:min-h-screen md:border-b-0 md:border-r">
          <Link href="/dashboard" className="mb-6 flex items-center gap-2 text-lg font-bold">
            <Sparkles className="h-5 w-5" />
            TruthGuard AI
          </Link>
          <nav aria-label="Main navigation" className="flex gap-2 overflow-x-auto pb-1 md:flex-col md:overflow-visible md:pb-0">
            {navigation.map((item) => {
              const Icon = item.icon;
              const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  aria-current={active ? "page" : undefined}
                  className={cn(
                    "flex shrink-0 items-center gap-2 rounded-md px-3 py-2 text-sm transition",
                    active ? "bg-primary text-primary-foreground" : "hover:bg-muted"
                  )}
                >
                  <Icon className="h-4 w-4" />
                  {item.label}
                </Link>
              );
            })}
          </nav>
          <div className="mt-6 hidden border-t border-border pt-4 md:block">
            <p className="truncate px-3 text-sm font-medium">{user.fullName}</p>
            <p className="truncate px-3 text-xs text-muted-foreground">{user.email}</p>
            <div className="mt-2">
              <LogoutButton />
            </div>
          </div>
        </aside>
        <main className="p-4 md:p-8">
          <header className="mb-6 flex items-center justify-end gap-3">
            <ThemeToggle />
            <div className="md:hidden">
              <LogoutButton />
            </div>
          </header>
          {children}
        </main>
      </div>
    </div>
  );
}
