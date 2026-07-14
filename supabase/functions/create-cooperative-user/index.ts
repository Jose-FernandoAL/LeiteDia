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
    if (!profile?.active || profile.role !== "admin") return new Response(JSON.stringify({ message: "Apenas administradores podem gerenciar usuários" }), { status: 403, headers });

    const body = await req.json();
    const action = String(body.action ?? "create");
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
