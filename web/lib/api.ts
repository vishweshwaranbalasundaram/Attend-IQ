"use client";

import { auth } from "@/lib/firebase";

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const user = auth.currentUser;
  if (!user) throw new Error("Please sign in again to continue.");
  const token = await user.getIdToken();
  const response = await fetch(`${process.env.NEXT_PUBLIC_API_BASE_URL ?? "/api"}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
      ...init.headers,
    },
    cache: "no-store",
  });
  if (!response.ok) {
    const body = await response.json().catch(() => null) as { message?: string } | null;
    throw new Error(body?.message ?? `Request failed (${response.status}).`);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}
