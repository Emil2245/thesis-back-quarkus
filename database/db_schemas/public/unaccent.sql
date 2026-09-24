create function unaccent(regdictionary, text) returns text
    stable
    strict
    parallel safe
    language c
as
$$
begin
-- missing source code
end;
$$;

alter function unaccent(regdictionary, text) owner to postgres;

create function unaccent(text) returns text
    stable
    strict
    parallel safe
    language c
as
$$
begin
-- missing source code
end;
$$;

alter function unaccent(text) owner to postgres;

