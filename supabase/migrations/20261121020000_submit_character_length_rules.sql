-- New community characters get the same reply-length rules as the catalog
-- (2026-11-21).
--
-- Every existing character's system_prompt starts with the "Keep every reply
-- short and natural" block. submit_character() stored the app's prompt as is,
-- so approved community characters would answer at full length. The rule is
-- now added in front on the server when it is not already there, so there is
-- one source of truth and old app versions behave the same.

begin;

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
  length_rules constant text :=
    E'Keep every reply short and natural, like a real chat message.\n\n'
    || E'Length rules:\n'
    || E'- Maximum 80 words per reply. Never exceed this.\n'
    || E'- Match the length to the user''s message. A greeting or simple question gets one or two short sentences. Only a detailed question or story moment gets a longer reply, and even then stay under 80 words.\n'
    || E'- Write one short action or expression at most, and only if it adds something.\n'
    || E'- Do not repeat what the user said, and do not pile on extra topics or questions.\n'
    || E'- Ask at most one question, and only when it fits.\n'
    || E'- Always finish your last sentence. If you''re running long, cut details instead of trailing off.\n\n';
  final_prompt text;
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

  -- The 5000 limit above applies to what the user wrote; the rules go on top.
  final_prompt := coalesce(p_system_prompt, '');
  if left(final_prompt, 22) <> 'Keep every reply short' then
    final_prompt := length_rules || final_prompt;
  end if;

  insert into public.characters (name, slug, description, greeting, system_prompt, avatar_url, category_id, view, approved)
  values (trim(p_name), 'pending', p_description, p_greeting, final_prompt, nullif(trim(p_avatar_url), ''), community_id, 0, false)
  returning id, slug into new_id, new_slug;

  return json_build_object('id', new_id, 'slug', new_slug, 'category_id', community_id, 'approved', false);
end;
$$;

revoke all on function public.submit_character(text, text, text, text, text) from public;
grant execute on function public.submit_character(text, text, text, text, text) to anon, authenticated;

commit;
