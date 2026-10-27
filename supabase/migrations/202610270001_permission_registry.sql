create table if not exists public.permission_clients (
    client_app_id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users(id) on delete cascade,
    app_name text not null,
    package_name text not null,
    client_token_hash text not null,
    enrolled_at timestamptz not null default now(),
    last_seen_at timestamptz,
    revoked_at timestamptz
);

create table if not exists public.permission_enrollment_codes (
    code_hash text primary key,
    owner_id uuid not null references auth.users(id) on delete cascade,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null,
    redeemed_at timestamptz
);

create table if not exists public.permission_snapshots (
    client_app_id uuid not null references public.permission_clients(client_app_id) on delete cascade,
    permission text not null,
    category text not null,
    status text not null check (status in ('granted', 'denied', 'not_requested')),
    updated_at timestamptz not null default now(),
    primary key (client_app_id, permission)
);

create table if not exists public.permission_events (
    id bigint generated always as identity primary key,
    client_app_id uuid not null references public.permission_clients(client_app_id) on delete cascade,
    event_type text not null check (event_type in ('permission_snapshot', 'client_revoked')),
    payload jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create index if not exists permission_clients_owner_last_seen_idx
    on public.permission_clients (owner_id, last_seen_at desc);
create index if not exists permission_snapshots_client_updated_idx
    on public.permission_snapshots (client_app_id, updated_at desc);
create index if not exists permission_events_client_created_idx
    on public.permission_events (client_app_id, created_at desc);

alter table public.permission_clients enable row level security;
alter table public.permission_enrollment_codes enable row level security;
alter table public.permission_snapshots enable row level security;
alter table public.permission_events enable row level security;

drop policy if exists "Owners can read their permission clients" on public.permission_clients;
create policy "Owners can read their permission clients"
    on public.permission_clients for select to authenticated
    using (owner_id = (select auth.uid()));

drop policy if exists "Owners can read their client permission snapshots" on public.permission_snapshots;
create policy "Owners can read their client permission snapshots"
    on public.permission_snapshots for select to authenticated
    using (
        exists (
            select 1
            from public.permission_clients client
            where client.client_app_id = permission_snapshots.client_app_id
              and client.owner_id = (select auth.uid())
        )
    );

drop policy if exists "Owners can read their client permission events" on public.permission_events;
create policy "Owners can read their client permission events"
    on public.permission_events for select to authenticated
    using (
        exists (
            select 1
            from public.permission_clients client
            where client.client_app_id = permission_events.client_app_id
              and client.owner_id = (select auth.uid())
        )
    );

create or replace function public.redeem_permission_enrollment(
    p_code_hash text,
    p_client_token_hash text,
    p_app_name text,
    p_package_name text
)
returns uuid
language plpgsql
security definer
set search_path = pg_catalog, public, pg_temp
as $$
declare
    enrollment permission_enrollment_codes%rowtype;
    new_client_id uuid;
begin
    select *
    into enrollment
    from public.permission_enrollment_codes
    where code_hash = p_code_hash
      and redeemed_at is null
      and expires_at > now()
    for update;

    if not found then
        raise exception 'Enrollment code is invalid, expired, or already used';
    end if;

    update public.permission_enrollment_codes
    set redeemed_at = now()
    where code_hash = p_code_hash;

    insert into public.permission_clients (
        owner_id, app_name, package_name, client_token_hash
    )
    values (
        enrollment.owner_id,
        left(trim(p_app_name), 120),
        left(trim(p_package_name), 255),
        p_client_token_hash
    )
    returning client_app_id into new_client_id;

    return new_client_id;
end;
$$;

revoke all on function public.redeem_permission_enrollment(text, text, text, text) from public, anon, authenticated;
grant execute on function public.redeem_permission_enrollment(text, text, text, text) to service_role;

revoke all on public.permission_clients, public.permission_enrollment_codes,
    public.permission_snapshots, public.permission_events from anon, authenticated;
grant select (
    client_app_id, owner_id, app_name, package_name, enrolled_at, last_seen_at, revoked_at
) on public.permission_clients to authenticated;
grant select on public.permission_snapshots, public.permission_events to authenticated;

do $$
begin
    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'permission_clients'
    ) then
        alter publication supabase_realtime add table public.permission_clients;
    end if;
    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'permission_snapshots'
    ) then
        alter publication supabase_realtime add table public.permission_snapshots;
    end if;
exception
    when undefined_object then
        raise notice 'Create the supabase_realtime publication before enabling live registry updates.';
end $$;
