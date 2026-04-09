"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

import { AppShell } from "@/components/layout/app-shell";

export default function PrivateLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();

  useEffect(() => {
    const token = localStorage.getItem("tg_token");
    if (!token) {
      router.push("/login");
    }
  }, [router]);

  return <AppShell>{children}</AppShell>;
}

