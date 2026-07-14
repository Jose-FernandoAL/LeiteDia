import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

Deno.serve(async (req) => {
  const headers = { "Content-Type": "application/json" };
  try {
    if (req.method !== "POST") return new Response(JSON.stringify({ message: "Método inválido" }), { status: 405, headers });
    const url = Deno.env.get("SUPABASE_URL")!;
    const caller = createClient(url, Deno.env.get("SUPABASE_ANON_KEY")!, { global: { headers: { Authorization: req.headers.get("Authorization") ?? "" } } });
    const admin = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
    const { data: authData, error: authError } = await caller.auth.getUser();
    if (authError || !authData.user) throw new Error("Sessão inválida");
    const { data: profile } = await admin.from("profiles").select("cooperative_id,role,active").eq("id", authData.user.id).single();
    const body = await req.json();
    const action = String(body.action ?? "create");
    if (!profile?.active) return new Response(JSON.stringify({ message: "Usuário desativado" }), { status: 403, headers });

    if (action === "license-status") {
      let { data: license } = await admin.from("cooperative_licenses").select("status,trial_started_at,trial_days").eq("cooperative_id", profile.cooperative_id).maybeSingle();
      if (!license) {
        const { data: createdLicense, error: licenseError } = await admin.from("cooperative_licenses").insert({ cooperative_id: profile.cooperative_id, status: "trial", trial_days: 7 }).select("status,trial_started_at,trial_days").single();
        if (licenseError) throw licenseError;
        license = createdLicense;
      }
      const started = new Date(license.trial_started_at);
      const trialEnd = new Date(started.getTime() + Number(license.trial_days) * 86400000);
      const full = license.status === "full";
      const remainingMs = trialEnd.getTime() - Date.now();
      return new Response(JSON.stringify({ status: license.status, trial_ends_at: trialEnd.toISOString(), days_remaining: full ? 9999 : Math.max(0, Math.ceil(remainingMs / 86400000)), active: full || remainingMs > 0 }), { status: 200, headers });
    }

    if (profile.role !== "admin") return new Response(JSON.stringify({ message: "Apenas administradores podem gerenciar usuários" }), { status: 403, headers });

    const loginId = String(body.login_id ?? "").trim().toUpperCase();
    const fullName = String(body.full_name ?? "").trim();
    const password = String(body.password ?? "");
    if (!/^[A-Z0-9_-]{3,30}$/.test(loginId) || fullName.length < 3 || (password && password.length < 6)) throw new Error("Dados do usuário inválidos");

    if (action === "update") {
      const userId = String(body.user_id ?? "");
      const { data: target } = await admin.from("profiles").select("id,cooperative_id,role").eq("id", userId).single();
      if (!target || target.cooperative_id !== profile.cooperative_id) throw new Error("Usuário não encontrado");
      if (target.role === "admin" && userId === authData.user.id && body.active === false) throw new Error("O administrador não pode desativar a própria conta");
      const authChanges: Record<string, unknown> = { email: `${loginId.toLowerCase()}@leitedia.local`, user_metadata: { login_id: loginId, full_name: fullName } };
      if (password) authChanges.password = password;
      const { error: authUpdateError } = await admin.auth.admin.updateUserById(userId, authChanges);
      if (authUpdateError) throw authUpdateError;
      const { error: profileUpdateError } = await admin.from("profiles").update({ login_id: loginId, full_name: fullName, active: body.active !== false }).eq("id", userId);
      if (profileUpdateError) throw profileUpdateError;
      return new Response(JSON.stringify({ id: userId, login_id: loginId }), { status: 200, headers });
    }

    if (password.length < 6) throw new Error("A senha deve ter pelo menos 6 caracteres");
    const { data: created, error: createError } = await admin.auth.admin.createUser({ email: `${loginId.toLowerCase()}@leitedia.local`, password, email_confirm: true, user_metadata: { login_id: loginId, full_name: fullName } });
    if (createError || !created.user) throw createError ?? new Error("Não foi possível criar o usuário");
    const { error: profileError } = await admin.from("profiles").insert({ id: created.user.id, cooperative_id: profile.cooperative_id, login_id: loginId, full_name: fullName, role: "user", active: true });
    if (profileError) { await admin.auth.admin.deleteUser(created.user.id); throw profileError; }
    return new Response(JSON.stringify({ id: created.user.id, login_id: loginId }), { status: 201, headers });
  } catch (error) {
    return new Response(JSON.stringify({ message: error instanceof Error ? error.message : "Erro interno" }), { status: 400, headers });
  }
});
