create table if not exists public.cooperative_licenses (
  cooperative_id uuid primary key,
  status text not null default 'trial' check (status in ('trial', 'full', 'blocked')),
  trial_started_at timestamptz not null default now(),
  trial_days integer not null default 7 check (trial_days between 1 and 90),
  activated_at timestamptz,
  notes text,
  updated_at timestamptz not null default now()
);

alter table public.cooperative_licenses enable row level security;

revoke all on table public.cooperative_licenses from anon, authenticated;

comment on table public.cooperative_licenses is
  'Licenças do LeiteDia. status=full libera a versão completa; trial inicia no primeiro login.';
