"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { api, errorMessage } from "@/lib/api";
import type { SessionUser } from "@/lib/types";

export function AccountSettings({ user }: { user: SessionUser }) {
  const router = useRouter();
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [error, setError] = useState("");
  const [deleting, setDeleting] = useState(false);

  const deleteAccount = async (event: FormEvent) => {
    event.preventDefault();
    setError("");

    if (confirmation !== "DELETE") {
      setError('Type DELETE to confirm account removal.');
      return;
    }
    if (!password) {
      setError("Enter your password to confirm.");
      return;
    }

    setDeleting(true);
    try {
      await api("/account", { method: "DELETE", body: JSON.stringify({ password }) });
      router.replace("/");
      router.refresh();
    } catch (err) {
      setError(errorMessage(err));
      setDeleting(false);
    }
  };

  return (
    <div className="space-y-6">
      <h1 className="text-3xl font-bold">Account settings</h1>

      <Card>
        <CardHeader>
          <CardTitle>Profile</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-3 text-sm sm:grid-cols-2">
          <div><p className="text-muted-foreground">Full name</p><p className="font-medium">{user.fullName}</p></div>
          <div><p className="text-muted-foreground">Email</p><p className="font-medium">{user.email}</p></div>
          <div><p className="text-muted-foreground">Role</p><p className="font-medium">{user.role}</p></div>
          <div>
            <p className="text-muted-foreground">Member since</p>
            <p className="font-medium">{user.createdAt ? new Date(user.createdAt).toLocaleDateString() : "—"}</p>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Export your data</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3 text-sm text-muted-foreground">
          <p>Download a JSON file containing your profile, analysis history, and feedback.</p>
          <Button asChild variant="outline">
            <a href="/api/account/export" download>Download export</a>
          </Button>
        </CardContent>
      </Card>

      <Card className="border-red-500/40">
        <CardHeader>
          <CardTitle className="text-red-600 dark:text-red-400">Delete account</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={deleteAccount} className="space-y-4 text-sm">
            <p className="text-muted-foreground">
              This permanently deletes your profile, analyses, feedback, and sessions. This action cannot be undone.
            </p>
            <div className="space-y-1">
              <label htmlFor="deletePassword" className="font-medium">Password</label>
              <Input
                id="deletePassword"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </div>
            <div className="space-y-1">
              <label htmlFor="deleteConfirmation" className="font-medium">Type DELETE to confirm</label>
              <Input
                id="deleteConfirmation"
                value={confirmation}
                onChange={(event) => setConfirmation(event.target.value)}
              />
            </div>
            {error && <p role="alert" className="text-red-500">{error}</p>}
            <Button type="submit" disabled={deleting} className="bg-red-600 hover:bg-red-700">
              {deleting ? "Deleting..." : "Delete my account"}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
