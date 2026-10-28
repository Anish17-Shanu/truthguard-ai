import { cn } from "@/lib/utils";
import type { ClassificationLabel } from "@/lib/types";

const styles: Record<ClassificationLabel, string> = {
  real: "bg-emerald-500/15 text-emerald-700 dark:text-emerald-300",
  fake: "bg-red-500/15 text-red-700 dark:text-red-300",
  propaganda: "bg-amber-500/15 text-amber-700 dark:text-amber-300",
  uncertain: "bg-slate-500/15 text-slate-700 dark:text-slate-300"
};

const names: Record<ClassificationLabel, string> = {
  real: "Real",
  fake: "Fake",
  propaganda: "Propaganda",
  uncertain: "Uncertain"
};

export function LabelBadge({ label, className }: { label: ClassificationLabel; className?: string }) {
  return (
    <span className={cn("inline-flex rounded-full px-2.5 py-1 text-xs font-semibold", styles[label], className)}>
      {names[label]}
    </span>
  );
}
