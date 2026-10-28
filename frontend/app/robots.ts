import type { MetadataRoute } from "next";

export const dynamic = "force-dynamic";

export default function robots(): MetadataRoute.Robots {
  const base = (process.env.SITE_URL ?? "https://truthguard.ai").replace(/\/$/, "");
  return {
    rules: [
      {
        userAgent: "*",
        allow: "/",
        disallow: ["/api/", "/dashboard", "/detection", "/results", "/history", "/settings", "/admin"]
      }
    ],
    sitemap: `${base}/sitemap.xml`
  };
}
