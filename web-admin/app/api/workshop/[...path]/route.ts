import { NextRequest, NextResponse } from "next/server";

const apiBase = (process.env.WORKSHOP_API_URL || "http://localhost:8080/api/v1").replace(/\/$/, "");
const cookieOptions = { httpOnly: true, secure: process.env.NODE_ENV === "production", sameSite: "strict" as const, path: "/" };

async function handler(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  const { path } = await context.params;
  const target = apiBase + "/" + path.map(encodeURIComponent).join("/") + request.nextUrl.search;
  let access = request.cookies.get("workshop_access")?.value;
  const refresh = request.cookies.get("workshop_refresh")?.value;
  const send = (token?: string) => fetch(target, {
    method: request.method,
    headers: {
      ...(token ? { authorization: "Bearer " + token } : {}),
      ...(request.headers.get("content-type") ? { "content-type": request.headers.get("content-type")! } : {}),
      ...(request.headers.get("idempotency-key") ? { "idempotency-key": request.headers.get("idempotency-key")! } : {}),
    },
    ...(request.method === "GET" || request.method === "HEAD" ? {} : { body: request.body }),
    // Streaming request bodies are forwarded without buffering.
    // @ts-expect-error Node fetch requires duplex for a streamed body.
    duplex: "half",
    cache: "no-store",
  });
  try {
    let upstream = await send(access);
    let rotated: any = null;
    if (upstream.status === 401 && refresh && !path[0].startsWith("auth")) {
      const renewed = await fetch(apiBase + "/auth/refresh", {
        method: "POST", headers: { "content-type": "application/json" },
        body: JSON.stringify({ refreshToken: refresh, deviceLabel: "Workshop web admin" }), cache: "no-store",
      });
      if (renewed.ok) {
        rotated = await renewed.json();
        access = rotated.accessToken;
        upstream = await send(access);
      }
    }
    const payload = await upstream.arrayBuffer();
    const response = new NextResponse(payload, { status: upstream.status, headers: {
      "content-type": upstream.headers.get("content-type") || "application/json",
      "cache-control": "no-store",
    } });
    if (rotated) {
      response.cookies.set("workshop_access", rotated.accessToken, { ...cookieOptions, maxAge: rotated.expiresIn });
      response.cookies.set("workshop_refresh", rotated.refreshToken, { ...cookieOptions, maxAge: rotated.refreshExpiresIn });
    }
    return response;
  } catch {
    return NextResponse.json({ error: "Unable to reach the workshop API. Check WORKSHOP_API_URL." }, { status: 503 });
  }
}

export const GET = handler;
export const POST = handler;
export const PATCH = handler;
export const DELETE = handler;
