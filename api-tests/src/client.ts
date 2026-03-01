// Base HTTP client for HiveApp API tests
const BASE_URL = "http://localhost:8080";

export async function post(path: string, body: unknown, token?: string) {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };
  if (token) headers["Authorization"] = `Bearer ${token}`;

  const res = await fetch(`${BASE_URL}${path}`, {
    method: "POST",
    headers,
    body: JSON.stringify(body),
  });

  const data = await res.json().catch(() => null);
  return { status: res.status, data };
}

export async function get(path: string, token?: string) {
  const headers: Record<string, string> = {};
  if (token) headers["Authorization"] = `Bearer ${token}`;

  const res = await fetch(`${BASE_URL}${path}`, { headers });
  const data = await res.json().catch(() => null);
  return { status: res.status, data };
}

export function log(label: string, res: { status: number; data: unknown }) {
  const ok = res.status >= 200 && res.status < 300;
  const icon = ok ? "✅" : "❌";
  console.log(`\n${icon} [${res.status}] ${label}`);
  console.log(JSON.stringify(res.data, null, 2));
}
