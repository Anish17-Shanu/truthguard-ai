"use client";

import { useEffect, useMemo, useState } from "react";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

export default function AdminPage() {
  const [authorized, setAuthorized] = useState(false);

  useEffect(() => {
    setAuthorized(localStorage.getItem("tg_role") === "ADMIN");
  }, []);

  const tips = useMemo(
    () => [
      "Monitor high-confidence fake/propaganda detections daily.",
      "Review user feedback records before triggering retraining.",
      "Track model drift via weekly precision/recall metrics.",
      "Audit logs for suspicious usage spikes and abuse patterns."
    ],
    []
  );

  if (!authorized) {
    return <p className="text-sm text-muted-foreground">Admin access required.</p>;
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Admin Operations</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3 text-sm">
        {tips.map((tip) => (
          <p key={tip} className="rounded-md border border-border bg-muted/30 p-3">
            {tip}
          </p>
        ))}
      </CardContent>
    </Card>
  );
}
