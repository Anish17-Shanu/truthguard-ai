import type { Metadata } from "next";

import { LegalShell } from "@/components/layout/legal-shell";

export const metadata: Metadata = { title: "Privacy Policy · TruthGuard AI" };

export default function PrivacyPage() {
  const contact = process.env.NEXT_PUBLIC_CONTACT_EMAIL ?? "privacy@truthguard.ai";

  return (
    <LegalShell title="Privacy Policy" updated="2026-10-29">
      <p>
        This policy explains what TruthGuard AI stores, why we store it, and how you can access or
        delete it. The deployment operator is responsible for the environment where this service runs.
      </p>

      <h2>What we collect</h2>
      <ul>
        <li><strong>Account data:</strong> full name, email address, password hash, role, and timestamps.</li>
        <li><strong>Analysis data:</strong> the text snippet, URL, or file name you submit, plus the resulting label, confidence, signals, model version, and content hash.</li>
        <li><strong>Feedback data:</strong> whether a result was correct, an optional corrected label, and an optional comment.</li>
        <li><strong>Operational data:</strong> session records, audit logs, and request identifiers used for security and troubleshooting.</li>
      </ul>

      <h2>How we use data</h2>
      <ul>
        <li>To authenticate you and keep your account secure.</li>
        <li>To display your analysis history and account statistics.</li>
        <li>To evaluate and improve the classifier using aggregated feedback.</li>
        <li>To investigate abuse, enforce rate limits, and meet legal obligations.</li>
      </ul>

      <h2>Cookies</h2>
      <p>
        We use a single httpOnly session cookie to keep you signed in. We do not use advertising or
        third-party analytics cookies. Signing out revokes the server-side session.
      </p>

      <h2>Third parties</h2>
      <p>
        Content analysis runs on our own service. We do not send your content to third-party
        generative AI providers. When you submit a URL, our server fetches that public page, subject
        to robots.txt and network safety checks.
      </p>

      <h2>Retention</h2>
      <p>
        Analysis records are retained according to the deployment configuration. Audit logs are kept
        for a limited security window, and expired sessions are removed automatically. You can delete
        individual analyses or your entire account at any time.
      </p>

      <h2>Your rights</h2>
      <p>
        From Account settings you can download a JSON export of your data or permanently delete your
        account. Deletion removes your profile, analyses, feedback, sessions, and audit entries. Some
        backup copies may persist for a short period until rotation completes.
      </p>

      <h2>Contact</h2>
      <p>
        Questions about this policy can be sent to <a className="text-primary hover:underline" href={`mailto:${contact}`}>{contact}</a>.
      </p>
    </LegalShell>
  );
}
