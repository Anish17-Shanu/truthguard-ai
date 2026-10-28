import type { Metadata } from "next";
import { Urbanist } from "next/font/google";
import { ThemeProvider } from "next-themes";

import "./globals.css";

const urbanist = Urbanist({ subsets: ["latin"] });

const siteUrl = process.env.SITE_URL ?? "http://localhost:3000";

export const metadata: Metadata = {
  metadataBase: new URL(siteUrl),
  title: {
    default: "TruthGuard AI",
    template: "%s | TruthGuard AI"
  },
  description:
    "Screen English news text for misinformation and propaganda patterns with a local classifier and transparent signals.",
  openGraph: {
    type: "website",
    siteName: "TruthGuard AI",
    title: "TruthGuard AI",
    description:
      "Screen English news text for misinformation and propaganda patterns. A screening aid, not a fact-checker.",
    url: "/"
  },
  twitter: {
    card: "summary",
    title: "TruthGuard AI",
    description: "Local misinformation screening for English news text."
  },
  robots: { index: true, follow: true }
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className={urbanist.className}>
        <ThemeProvider attribute="class" defaultTheme="system" enableSystem>
          {children}
        </ThemeProvider>
      </body>
    </html>
  );
}

