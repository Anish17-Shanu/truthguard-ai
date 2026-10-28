"use client";

import { useEffect } from "react";

export default function GlobalError({
  error,
  reset
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <main className="mx-auto flex min-h-screen max-w-xl flex-col items-center justify-center gap-4 px-6 text-center">
      <h1 className="text-2xl font-semibold">Something went wrong</h1>
      <p className="text-sm text-muted-foreground">
        The page could not be loaded. Your data was not changed.
      </p>
      <button
        className="rounded-md border px-4 py-2 text-sm font-medium hover:bg-muted"
        onClick={() => reset()}
        type="button"
      >
        Try again
      </button>
    </main>
  );
}
