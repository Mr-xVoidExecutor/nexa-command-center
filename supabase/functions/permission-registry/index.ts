import { createClient } from "npm:@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });

const digest = async (value: string) => {
  const bytes = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(value),
  );
  return Array.from(new Uint8Array(bytes), (byte) =>
    byte.toString(16).padStart(2, "0")
  ).join("");
};

const randomToken = (length = 32) => {
  const bytes = crypto.getRandomValues(new Uint8Array(length));
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0"))
    .join("");
};

const adminClient = () =>
  createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    { auth: { persistSession: false, autoRefreshToken: false } },
  );

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (request.method !== "POST") return json({ error: "POST required" }, 405);

  const supabase = adminClient();
  let input: Record<string, unknown>;
  try {
    input = await request.json();
  } catch {
    return json({ error: "Invalid JSON request" }, 400);
  }

  const action = input.action;
  const authorization = request.headers.get("Authorization") ?? "";

  if (action === "create_enrollment") {
    const bearer = authorization.match(/^Bearer\s+(.+)$/i)?.[1];
    if (!bearer) return json({ error: "Sign in before creating an enrollment code" }, 401);
    const { data: userData, error: userError } = await supabase.auth.getUser(bearer);
    if (userError || !userData.user) return json({ error: "Session expired; sign in again" }, 401);

    const code = randomToken(32);
    const { error } = await supabase.from("permission_enrollment_codes").insert({
      code_hash: await digest(code),
      owner_id: userData.user.id,
      expires_at: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
    });
    if (error) return json({ error: "Could not create an enrollment code" }, 500);
    return json({ code, expires_in_seconds: 600 });
  }

  if (action === "redeem_enrollment") {
    const code = typeof input.code === "string" ? input.code.trim() : "";
    const appName = typeof input.app_name === "string" ? input.app_name.trim() : "";
    const packageName = typeof input.package_name === "string" ? input.package_name.trim() : "";
    if (code.length !== 64 || !/^[0-9a-f]+$/i.test(code)) {
      return json({ error: "Enrollment code is invalid or expired" }, 400);
    }
    if (!appName || appName.length > 120 || !/^[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+$/.test(packageName)) {
      return json({ error: "Valid app name and Android package name are required" }, 400);
    }
    const clientToken = randomToken();
    const { data: clientId, error } = await supabase.rpc("redeem_permission_enrollment", {
      p_code_hash: await digest(code),
      p_client_token_hash: await digest(clientToken),
      p_app_name: appName,
      p_package_name: packageName,
    });
    if (error || !clientId) {
      return json({ error: "Enrollment code is invalid, expired, or already used" }, 401);
    }
    return json({ client_app_id: clientId, client_token: clientToken });
  }

  if (action === "revoke_client") {
    const bearer = authorization.match(/^Bearer\s+(.+)$/i)?.[1];
    const clientId = typeof input.client_app_id === "string" ? input.client_app_id : "";
    if (!bearer || !/^[0-9a-f-]{36}$/i.test(clientId)) {
      return json({ error: "Sign in and provide a valid client ID" }, 401);
    }
    const { data: userData, error: userError } = await supabase.auth.getUser(bearer);
    if (userError || !userData.user) return json({ error: "Session expired; sign in again" }, 401);
    const { data: revokedClient, error } = await supabase.from("permission_clients")
      .update({ revoked_at: new Date().toISOString() })
      .eq("client_app_id", clientId)
      .eq("owner_id", userData.user.id)
      .is("revoked_at", null)
      .select("client_app_id")
      .maybeSingle();
    if (error) return json({ error: "Could not revoke client enrollment" }, 500);
    if (!revokedClient) return json({ error: "Client not found or already revoked" }, 404);
    const { error: eventError } = await supabase.from("permission_events").insert({
      client_app_id: clientId,
      event_type: "client_revoked",
    });
    if (eventError) return json({ error: "Client was revoked, but the event could not be recorded" }, 500);
    return json({ revoked: true });
  }

  if (action === "sync" || action === "revoke") {
    const bearer = authorization.match(/^Bearer\s+(.+)$/i)?.[1] ?? "";
    const clientId = typeof input.client_app_id === "string" ? input.client_app_id : "";
    if (!/^[0-9a-f-]{36}$/i.test(clientId) || !bearer) {
      return json({ error: "Valid client credentials are required" }, 401);
    }
    const { data: client, error: clientError } = await supabase
      .from("permission_clients")
      .select("client_app_id, client_token_hash, revoked_at")
      .eq("client_app_id", clientId)
      .maybeSingle();
    if (clientError || !client || client.revoked_at ||
      await digest(bearer) !== client.client_token_hash) {
      return json({ error: "Client credentials are invalid or revoked" }, 401);
    }

    if (action === "revoke") {
      const { error } = await supabase.from("permission_clients")
        .update({ revoked_at: new Date().toISOString() })
        .eq("client_app_id", clientId);
      if (error) return json({ error: "Could not revoke client enrollment" }, 500);
      const { error: eventError } = await supabase.from("permission_events").insert({
        client_app_id: clientId,
        event_type: "client_revoked",
      });
      if (eventError) return json({ error: "Client was revoked, but the event could not be recorded" }, 500);
      return json({ revoked: true });
    }

    const permissions = input.permissions;
    if (!Array.isArray(permissions) || permissions.length > 100) {
      return json({ error: "A permission snapshot of at most 100 entries is required" }, 400);
    }
    const normalized = [];
    for (const item of permissions) {
      if (!item || typeof item !== "object") return json({ error: "Invalid permission entry" }, 400);
      const permission = (item as Record<string, unknown>).permission;
      const category = (item as Record<string, unknown>).category;
      const status = (item as Record<string, unknown>).status;
      if (typeof permission !== "string" ||
        !/^android\.permission\.[A-Z0-9_]+$/.test(permission) ||
        typeof category !== "string" || category.length > 80 ||
        !["granted", "denied", "not_requested"].includes(String(status))) {
        return json({ error: "Permission entry failed validation" }, 400);
      }
      normalized.push({
        client_app_id: clientId,
        permission,
        category,
        status,
        updated_at: new Date().toISOString(),
      });
    }

    if (normalized.length) {
      const { error: snapshotError } = await supabase.from("permission_snapshots")
        .upsert(normalized, { onConflict: "client_app_id,permission" });
      if (snapshotError) return json({ error: "Could not save permission snapshot" }, 500);
    }
    let cleanup = supabase.from("permission_snapshots")
      .delete()
      .eq("client_app_id", clientId);
    if (normalized.length) {
      const currentPermissions = `(${normalized.map((item) => `"${item.permission}"`).join(",")})`;
      cleanup = cleanup.not("permission", "in", currentPermissions);
    }
    const { error: cleanupError } = await cleanup;
    if (cleanupError) return json({ error: "Could not reconcile permission snapshot" }, 500);

    const { error: updateError } = await supabase.from("permission_clients")
      .update({ last_seen_at: new Date().toISOString() })
      .eq("client_app_id", clientId);
    if (updateError) return json({ error: "Could not update client heartbeat" }, 500);
    const { error: eventError } = await supabase.from("permission_events").insert({
      client_app_id: clientId,
      event_type: "permission_snapshot",
      payload: { permission_count: normalized.length },
    });
    if (eventError) return json({ error: "Could not record permission update event" }, 500);
    return json({ updated: normalized.length });
  }

  return json({ error: "Unsupported action" }, 400);
});
