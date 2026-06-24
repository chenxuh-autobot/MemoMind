create or replace function public.memomind_request_user_id()
returns text
language sql
stable
as $$
  select coalesce(
    nullif(nullif(current_setting('request.headers', true), '')::json->>'x-memomind-user-id', ''),
    nullif(current_setting('request.header.x-memomind-user-id', true), '')
  );
$$;

drop policy if exists "agent_tasks_select_own" on public.agent_tasks;
drop policy if exists "agent_tasks_insert_own" on public.agent_tasks;
drop policy if exists "agent_tasks_update_own_limited" on public.agent_tasks;
drop policy if exists "agent_tasks_select_request_user" on public.agent_tasks;
drop policy if exists "agent_tasks_insert_request_user" on public.agent_tasks;
drop policy if exists "agent_tasks_update_request_user" on public.agent_tasks;

create policy "agent_tasks_select_request_user"
on public.agent_tasks
for select
to anon, authenticated
using (public.memomind_request_user_id() = user_id);

create policy "agent_tasks_insert_request_user"
on public.agent_tasks
for insert
to anon, authenticated
with check (public.memomind_request_user_id() = user_id);

create policy "agent_tasks_update_request_user"
on public.agent_tasks
for update
to anon, authenticated
using (public.memomind_request_user_id() = user_id)
with check (public.memomind_request_user_id() = user_id);

comment on function public.memomind_request_user_id() is
'Reads x-memomind-user-id from PostgREST request headers so MemoMind mobile clients can access only their own agent_tasks rows without requiring Supabase Auth in the MVP.';
