create table cronograma_actividad
(
    id             bigint generated always as identity
        primary key,
    presupuesto_id bigint                                 not null
        references presupuesto
            on delete cascade,
    rubro_id       bigint                                 not null
        references rubro
            on delete cascade,
    created_at     timestamp with time zone default now() not null,
    unique (presupuesto_id, rubro_id)
);

alter table cronograma_actividad
    owner to postgres;

