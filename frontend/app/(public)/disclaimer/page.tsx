import type { Metadata } from "next";

import { LegalShell } from "@/components/layout/legal-shell";

export const metadata: Metadata = { title: "Disclaimer · TruthGuard AI" };

export default function DisclaimerPage() {
  return (
    <LegalShell title="Disclaimer" updated="2026-10-29">
      <h2>Not a fact-checking authority</h2>
      <p>
        TruthGuard AI produces automated signals about text. A &quot;fake&quot; or
        &quot;propaganda&quot; label does not prove that content is false, and a
        &quot;real&quot; label does not prove that it is true. Always verify important claims with
        primary sources and qualified professionals.
      </p>

      <h2>Model limitations</h2>
      <ul>
        <li>Accuracy varies by topic, writing style, and source length.</li>
        <li>English-language content is supported at launch.</li>
        <li>Satire, opinion, and quoted material can be misclassified.</li>
        <li>When no trained model artifact is loaded, the service explicitly labels results as heuristic mode.</li>
      </ul>

      <h2>High-stakes decisions</h2>
      <p>
        Do not use this service as the sole basis for legal, medical, financial, employment, or
        safety-critical decisions. Human review is required.
      </p>

      <h2>No warranty</h2>
      <p>
        The service is provided without warranties of any kind, express or implied, including
        fitness for a particular purpose and non-infringement.
      </p>
    </LegalShell>
  );
}
