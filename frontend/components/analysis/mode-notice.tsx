export function ModeNotice({ mode }: { mode: "transformer" | "heuristic" }) {
  if (mode === "transformer") {
    return (
      <p className="rounded-md border border-border bg-muted/40 p-3 text-xs text-muted-foreground">
        Result produced by the local classifier model. Confidence reflects model probability.
      </p>
    );
  }

  return (
    <p role="status" className="rounded-md border border-amber-500/40 bg-amber-500/10 p-3 text-xs text-amber-800 dark:text-amber-200">
      Heuristic mode: this result comes from deterministic language rules, not a trained model
      prediction. Treat it as a signal, not a verdict.
    </p>
  );
}
