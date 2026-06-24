create extension if not exists pgcrypto;

create table if not exists public.agent_tasks (
  id uuid primary key default gen_random_uuid(),
  user_id text not null,
  source_app text not null,
  source_task_id text,
  target_agent text not null,
  project_id text not null,
  task_type text not null,
  mode text not null default 'plan_only',
  goal text not null,
  prompt text not null,
  context jsonb not null default '{}'::jsonb,
  permission jsonb not null default '{}'::jsonb,
  status text not null default 'pending',
  result jsonb,
  error jsonb,
  claimed_by text,
  claimed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint agent_tasks_status_check check (
    status in ('pending', 'running', 'waiting_approval', 'done', 'failed', 'cancelled')
  ),
  constraint agent_tasks_mode_check check (
    mode in ('plan_only', 'read_only', 'workspace_write')
  )
);

create index if not exists idx_agent_tasks_status_created_at
on public.agent_tasks (status, created_at);

create index if not exists idx_agent_tasks_user_id_created_at
on public.agent_tasks (user_id, created_at desc);

create index if not exists idx_agent_tasks_target_agent_status
on public.agent_tasks (target_agent, status);

create or replace function public.set_agent_tasks_updated_at()
returns trigger as $$
begin
  new.updated_at = now();
  return new;
end;
$$ language plpgsql;

drop trigger if exists trg_agent_tasks_updated_at on public.agent_tasks;
create trigger trg_agent_tasks_updated_at
before update on public.agent_tasks
for each row execute function public.set_agent_tasks_updated_at();

alter table public.agent_tasks enable row level security;

drop policy if exists "agent_tasks_select_own" on public.agent_tasks;
create policy "agent_tasks_select_own"
on public.agent_tasks
for select
to authenticated
using (auth.uid()::text = user_id);

drop policy if exists "agent_tasks_insert_own" on public.agent_tasks;
create policy "agent_tasks_insert_own"
on public.agent_tasks
for insert
to authenticated
with check (auth.uid()::text = user_id);

drop policy if exists "agent_tasks_update_own_limited" on public.agent_tasks;
create policy "agent_tasks_update_own_limited"
on public.agent_tasks
for update
to authenticated
using (auth.uid()::text = user_id)
with check (auth.uid()::text = user_id);

comment on table public.agent_tasks is
'MemoMind Agent Bridge cross-device task queue. Android clients should use anon key plus authenticated user access token; desktop bridge should use service role key from local environment variables only.';
