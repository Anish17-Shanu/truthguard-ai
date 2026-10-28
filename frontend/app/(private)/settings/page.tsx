import { redirect } from "next/navigation";

import { getSessionUser } from "@/lib/backend";
import { AccountSettings } from "./account-settings";

export const dynamic = "force-dynamic";

export default async function SettingsPage() {
  const user = await getSessionUser();
  if (!user) {
    redirect("/login");
  }

  return <AccountSettings user={user} />;
}
