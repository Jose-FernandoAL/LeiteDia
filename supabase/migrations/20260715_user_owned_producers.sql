alter table public.producers
  add column if not exists user_id uuid references public.profiles(id);

-- Conserva eventuais produtores já cadastrados, atribuindo-os a uma conta da cooperativa.
update public.producers p
set user_id = (
  select pr.id
  from public.profiles pr
  where pr.cooperative_id = p.cooperative_id
  order by case when pr.role = 'admin' then 0 else 1 end, pr.id
  limit 1
)
where p.user_id is null;

alter table public.producers
  alter column user_id set not null;

create index if not exists producers_user_name_idx
  on public.producers (user_id, name);

drop policy if exists "Cooperative members can view producers" on public.producers;
drop policy if exists "Admins can insert producers" on public.producers;
drop policy if exists "Admins can update producers" on public.producers;
drop policy if exists "Users can view own producers" on public.producers;
drop policy if exists "Users can insert own producers" on public.producers;
drop policy if exists "Users can update own producers" on public.producers;

create policy "Users can view own producers"
on public.producers for select to authenticated
using (user_id = auth.uid());

create policy "Users can insert own producers"
on public.producers for insert to authenticated
with check (
  user_id = auth.uid()
  and cooperative_id = (
    select cooperative_id from public.profiles
    where id = auth.uid() and active = true
  )
);

create policy "Users can update own producers"
on public.producers for update to authenticated
using (user_id = auth.uid())
with check (
  user_id = auth.uid()
  and cooperative_id = (
    select cooperative_id from public.profiles
    where id = auth.uid() and active = true
  )
);

