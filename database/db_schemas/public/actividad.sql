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
    avance_por_periodo jsonb         default '{}'::jsonb not null
);

alter table actividad
    owner to postgres;

create index ix_actividad_cronograma
    on actividad (cronograma_id);

