create table actividad
(
    id                 bigint generated always as identity
        primary key,
    cronograma_id      bigint                            not null
        references cronograma
            on delete cascade,
    rubro_id           bigint                            not null
        unique
        references rubro
            on delete cascade,
    peso_ponderado     numeric(7, 4) default 0           not null,
    avance_por_periodo jsonb         default '{}'::jsonb not null,
    public_id          uuid          default uuidv7()    not null
        unique
);

alter table actividad
    owner to postgres;

create index ix_actividad_cronograma
    on actividad (cronograma_id);

create trigger trg_public_id_immutable
    before update
        of public_id
    on actividad
    for each row
execute procedure fn_assert_public_id_immutable();

