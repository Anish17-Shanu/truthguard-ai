import type { Metadata } from "next";

import { LegalShell } from "@/components/layout/legal-shell";

export const metadata: Metadata = { title: "Terms of Service · TruthGuard AI" };

export default function TermsPage() {
  return (
    <LegalShell title="Terms of Service" updated="2026-10-29">
      <p>
        By creating an account or using TruthGuard AI, you agree to these terms. If you do not agree,
        do not use the service.
      </p>

      <h2>Service</h2>
      <p>
        TruthGuard AI provides automated text classification and explanatory signals. The service is
        provided on an as-is basis and may change or be interrupted. English-language content is
        supported at launch.
      </p>

      <h2>Accounts</h2>
      <ul>
        <li>Provide accurate account information and keep your password secure.</li>
        <li>You are responsible for activity performed through your account.</li>
        <li>Do not share accounts or attempt to access another user&apos;s data.</li>
      </ul>

      <h2>Acceptable use</h2>
      <ul>
        <li>Do not use the service to harass, defame, or target individuals.</li>
        <li>Do not attempt to bypass rate limits, probe internal networks, or disrupt the service.</li>
        <li>Do not submit content you do not have the right to process.</li>
      </ul>

      <h2>No accuracy guarantee</h2>
      <p>
        Classification is probabilistic and can be wrong. Results are not a substitute for
        professional fact-checking, legal advice, medical advice, or editorial judgment.
      </p>

      <h2>Limitation of liability</h2>
      <p>
        To the maximum extent permitted by law, the operator is not liable for indirect, incidental,
        or consequential damages arising from use of the service, including decisions made based on
        its output.
      </p>

      <h2>Termination</h2>
      <p>
        You may delete your account at any time. We may suspend accounts that violate these terms or
        threaten the security and availability of the service.
      </p>

      <h2>Changes</h2>
      <p>
        We may update these terms as the service evolves. Continued use after an update constitutes
        acceptance of the revised terms.
      </p>
    </LegalShell>
  );
}
