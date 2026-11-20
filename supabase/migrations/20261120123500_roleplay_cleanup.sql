-- RaikaAI roleplay catalog cleanup (2026-11-20).
--
-- 1. Security: the public could create categories; SECURITY DEFINER functions
--    had no fixed search_path.
-- 2. User submissions go to a dedicated "Community" category, get a unique
--    server-made slug and start at 0 views.
-- 3. Consistent slugs: categories in kebab case, characters as
--    <category-slug>-<name>.
-- 4. Indexes for search (trigram on name), Trending (view) and the
--    per-category list (category_id, name).

begin;

-- ── 1. Security ──────────────────────────────────────────────────────
drop policy if exists categories_create on public.categories;

create or replace function public.increment_character_view(char_id integer)
returns void
language sql
security definer
set search_path = ''
as $$
  update public.characters
  set view = coalesce(view, 0) + 1
  where id = char_id;
$$;

alter function public.get_random(integer) set search_path = '';
alter function public.set_updated_at() set search_path = '';

create or replace function public.slugify(t text)
returns text
language sql
immutable
set search_path = ''
as $$
  select trim(both '-' from regexp_replace(lower(coalesce(t, '')), '[^a-z0-9]+', '-', 'g'));
$$;

-- ── 2. Community submissions ─────────────────────────────────────────
insert into public.categories (name, slug)
values ('Community', 'community')
on conflict (slug) do nothing;

do $$
declare
  community_id bigint;
begin
  select id into community_id from public.categories where slug = 'community';
  execute format('alter table public.characters alter column category_id set default %s', community_id);
end;
$$;

create or replace function public.characters_prepare_submission()
returns trigger
language plpgsql
set search_path = ''
as $$
declare
  community_id bigint;
begin
  select id into community_id from public.categories where slug = 'community';
  if new.category_id is null then
    new.category_id := community_id;
  end if;
  if new.category_id = community_id then
    -- Unique slug made by the server, so two "Lilith"s never collide.
    new.slug := 'community-' || public.slugify(new.name) || '-' || substr(md5(random()::text || clock_timestamp()::text), 1, 6);
    new.view := 0;
  end if;
  return new;
end;
$$;

drop trigger if exists characters_prepare_submission on public.characters;
create trigger characters_prepare_submission
before insert on public.characters
for each row execute function public.characters_prepare_submission();

drop policy if exists characters_create on public.characters;
create policy characters_create on public.characters
for insert to anon, authenticated
with check (
  category_id = (select id from public.categories where slug = 'community')
  and char_length(trim(both from name)) >= 1
  and char_length(name) <= 60
  and char_length(coalesce(description, '')) <= 5000
  and char_length(coalesce(greeting, '')) <= 5000
  and char_length(coalesce(system_prompt, '')) <= 5000
  and coalesce(view, 0) = 0
);

-- ── 3. Consistent slugs ──────────────────────────────────────────────
update public.categories set slug = 'honkai-star-rail' where slug = 'HSR';
update public.categories set slug = 'arknights-endfield' where slug = 'arknight-endfield';

-- Two passes: temporary slugs first, so no new slug collides with an old one
-- that has not been rewritten yet (the unique check runs per row).
update public.characters set slug = 'tmp-' || id;

with ranked as (
  select
    ch.id,
    cat.slug || '-' || public.slugify(ch.name) as base,
    row_number() over (partition by cat.slug || '-' || public.slugify(ch.name) order by ch.id) as rn
  from public.characters ch
  join public.categories cat on cat.id = ch.category_id
)
update public.characters ch
set slug = case when r.rn = 1 then r.base else r.base || '-' || ch.id end
from ranked r
where r.id = ch.id;

-- ── 4. Indexes ───────────────────────────────────────────────────────
create extension if not exists pg_trgm with schema extensions;

create index if not exists characters_name_trgm_idx
  on public.characters using gin (name extensions.gin_trgm_ops);
create index if not exists characters_view_idx
  on public.characters (view desc);
create index if not exists characters_category_name_idx
  on public.characters (category_id, name);
drop index if exists public.characters_name_slug_idx;

commit;
