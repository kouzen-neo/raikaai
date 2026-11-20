-- Community moderation (2026-11-20).
--
-- - New submissions are hidden (approved = false) until the owner approves
--   them in the Supabase dashboard (Table editor → characters → approved).
-- - Submitting goes through submit_character(), which rate-limits
--   (5 per minute and 30 per hour across all users) and validates input.
--   Direct inserts from the public key are no longer allowed.
-- - Only approved characters are readable and can gain views.

begin;

alter table public.characters
  add column if not exists approved boolean not null default false;

-- Everything published before moderation existed stays visible.
update public.characters set approved = true where approved = false;

create index if not exists characters_pending_idx
  on public.characters (created_at) where not approved;

-- Reads: approved rows only.
drop policy if exists characters_read on public.characters;
create policy characters_read on public.characters
for select to anon, authenticated
using (approved);

-- Writes: only through submit_character().
drop policy if exists characters_create on public.characters;

create or replace function public.submit_character(
  p_name text,
  p_description text default '',
  p_greeting text default '',
  p_system_prompt text default '',
  p_avatar_url text default null
)
returns json
language plpgsql
security definer
set search_path = ''
as $$
declare
  community_id bigint;
  recent_minute int;
  recent_hour int;
  new_id bigint;
  new_slug text;
begin
  if char_length(trim(coalesce(p_name, ''))) < 1 or char_length(p_name) > 60 then
    raise exception 'Name must be 1-60 characters';
  end if;
  if char_length(coalesce(p_description, '')) > 5000
     or char_length(coalesce(p_greeting, '')) > 5000
     or char_length(coalesce(p_system_prompt, '')) > 5000 then
    raise exception 'Text fields are limited to 5000 characters';
  end if;
  if p_avatar_url is not null and p_avatar_url !~* '^https://' then
    raise exception 'Avatar must be an https URL';
  end if;

  select id into community_id from public.categories where slug = 'community';

  select count(*) into recent_minute from public.characters
   where category_id = community_id and created_at > now() - interval '1 minute';
  select count(*) into recent_hour from public.characters
   where category_id = community_id and created_at > now() - interval '1 hour';
  if recent_minute >= 5 or recent_hour >= 30 then
    raise exception 'Too many submissions right now, try again later';
  end if;

  insert into public.characters (name, slug, description, greeting, system_prompt, avatar_url, category_id, view, approved)
  values (trim(p_name), 'pending', p_description, p_greeting, p_system_prompt, nullif(trim(p_avatar_url), ''), community_id, 0, false)
  returning id, slug into new_id, new_slug;

  return json_build_object('id', new_id, 'slug', new_slug, 'category_id', community_id, 'approved', false);
end;
$$;

revoke all on function public.submit_character(text, text, text, text, text) from public;
grant execute on function public.submit_character(text, text, text, text, text) to anon, authenticated;

-- Views count only for approved characters.
create or replace function public.increment_character_view(char_id integer)
returns void
language sql
security definer
set search_path = ''
as $$
  update public.characters
  set view = coalesce(view, 0) + 1
  where id = char_id and approved;
$$;

-- Internal helpers are not part of the public API.
revoke execute on function public.rls_auto_enable() from anon, authenticated, public;
revoke execute on function public.characters_prepare_submission() from anon, authenticated, public;
revoke execute on function public.set_updated_at() from anon, authenticated, public;

commit;
