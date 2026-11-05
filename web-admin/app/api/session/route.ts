import { NextRequest, NextResponse } from "next/server";

const apiBase = (process.env.WORKSHOP_API_URL || "http://localhost:8080/api/v1").replace(/\/$/, "");
const cookieOptions = {
  httpOnly: true,
  secure: process.env.NODE_ENV === "production",
  sameSite: "strict" as const,
  path: "/",
};

async function refreshSession(refreshToken: string) {
  const response = await fetch(apiBase + "/auth/refresh", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ refreshToken, deviceLabel: "Workshop web admin" }),
    cache: "no-store",
  });
  if (!response.ok) return null;
  return response.json();
}

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    if (typeof body.email !== "string" || typeof body.password !== "string") {
      return NextResponse.json({ error: "Email and password are required" }, { status: 400 });
    }
    const response = await fetch(apiBase + "/auth/login", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ email: body.email, password: body.password, deviceLabel: "Workshop web admin" }),
      cache: "no-store",
    });
    const data = await response.json();
    if (!response.ok) return NextResponse.json(data, { status: response.status });
    const result = NextResponse.json({ user: data.user });
    result.cookies.set("workshop_access", data.accessToken, { ...cookieOptions, maxAge: data.expiresIn });
    result.cookies.set("workshop_refresh", data.refreshToken, { ...cookieOptions, maxAge: data.refreshExpiresIn });
    return result;
  } catch {
    return NextResponse.json({ error: "Unable to reach the workshop API. Check WORKSHOP_API_URL." }, { status: 503 });
  }
}

export async function GET(request: NextRequest) {
  const access = request.cookies.get("workshop_access")?.value;
  const refresh = request.cookies.get("workshop_refresh")?.value;
  if (!access && !refresh) return NextResponse.json({ error: "Sign in required" }, { status: 401 });
  try {
    let token = access;
    let response = token ? await fetch(apiBase + "/auth/me", { headers: { authorization: "Bearer " + token }, cache: "no-store" }) : null;
    let rotated: any = null;
    if (!response || response.status === 401) {
      if (!refresh) return NextResponse.json({ error: "Session expired" }, { status: 401 });
      rotated = await refreshSession(refresh);
      if (!rotated) return NextResponse.json({ error: "Session expired" }, { status: 401 });
      token = rotated.accessToken;
      response = await fetch(apiBase + "/auth/me", { headers: { authorization: "Bearer " + token }, cache: "no-store" });
    }
    const data = await response.json();
    const result = NextResponse.json(data, { status: response.status });
    if (rotated) {
      result.cookies.set("workshop_access", rotated.accessToken, { ...cookieOptions, maxAge: rotated.expiresIn });
      result.cookies.set("workshop_refresh", rotated.refreshToken, { ...cookieOptions, maxAge: rotated.refreshExpiresIn });
    }
    return result;
  } catch {
    return NextResponse.json({ error: "Unable to reach the workshop API" }, { status: 503 });
  }
}

export async function DELETE(request: NextRequest) {
  const refresh = request.cookies.get("workshop_refresh")?.value;
  try {
    if (refresh) await fetch(apiBase + "/auth/logout", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ refreshToken: refresh }),
      cache: "no-store",
    });
  } catch { /* local session is still cleared */ }
  const result = NextResponse.json({ status: "signed_out" });
  result.cookies.set("workshop_access", "", { ...cookieOptions, maxAge: 0 });
  result.cookies.set("workshop_refresh", "", { ...cookieOptions, maxAge: 0 });
  return result;
}
