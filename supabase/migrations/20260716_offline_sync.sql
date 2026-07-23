alter table public.milk_entries
  add column if not exists client_entry_id uuid;

create unique index if not exists milk_entries_user_client_entry_uidx
  on public.milk_entries (user_id, client_entry_id);

comment on column public.milk_entries.client_entry_id is
  'Identificador gerado no celular para impedir duplicidade durante sincronização offline.';
