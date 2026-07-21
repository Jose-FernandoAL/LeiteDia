-- Ajustes de segurança para a candidata de entrega.
-- Evita backups automáticos duplicados no mesmo dia/rótulo.

create or replace function public.create_data_backup(target_user_id uuid default auth.uid(), backup_label text default null)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  actor public.profiles%rowtype;
  target public.profiles%rowtype;
  backup_id uuid;
begin
  select * into actor from public.profiles where id = auth.uid() and active = true;
  select * into target from public.profiles where id = target_user_id and active = true;
  if actor.id is null or target.id is null or actor.cooperative_id <> target.cooperative_id
     or (actor.role <> 'admin' and actor.id <> target.id) then
    raise exception 'Sem permissão para criar este backup.' using errcode = '42501';
  end if;

  if backup_label like 'Automático diário %' then
    select id into backup_id from public.data_backups
    where owner_user_id = target.id and label = backup_label
    order by created_at desc limit 1;
    if backup_id is not null then
      return jsonb_build_object('id', backup_id, 'existing', true);
    end if;
  end if;

  insert into public.data_backups(cooperative_id, owner_user_id, created_by, label, payload)
  values (target.cooperative_id, target.id, actor.id, backup_label,
    jsonb_build_object(
      'version', 1,
      'producers', coalesce((select jsonb_agg(to_jsonb(p)) from public.producers p where p.user_id = target.id), '[]'::jsonb),
      'milk_entries', coalesce((select jsonb_agg(to_jsonb(m)) from public.milk_entries m where m.user_id = target.id), '[]'::jsonb)
    )) returning id into backup_id;
  return jsonb_build_object('id', backup_id, 'existing', false);
end;
$$;

revoke all on function public.create_data_backup(uuid,text) from public, anon;
grant execute on function public.create_data_backup(uuid,text) to authenticated;
