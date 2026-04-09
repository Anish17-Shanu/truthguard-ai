export type AuthPayload = {
  accessToken: string;
  expiresIn: number;
  userId: string;
  fullName: string;
  email: string;
  role: "USER" | "ADMIN";
};

const baseUrl = process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";

function authHeader() {
  if (typeof window === "undefined") return {};
  const token = localStorage.getItem("tg_token");
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers ?? {});
  const auth = authHeader();
  if (auth.Authorization) {
    headers.set("Authorization", auth.Authorization);
  }

  const response = await fetch(`${baseUrl}${path}`, {
    ...init,
    headers
  });

  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new Error(body.error ?? `API error ${response.status}`);
  }
  return response.json() as Promise<T>;
}
