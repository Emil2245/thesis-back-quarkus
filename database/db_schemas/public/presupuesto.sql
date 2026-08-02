create table presupuesto
(
    id                   bigint generated always as identity
        primary key,
    proyecto_id          bigint                                 not null
        references proyecto
            on delete cascade,
    version              smallint                               not null,
    es_vigente           boolean                  default false not null,
    origen_id            bigint
                                                                references presupuesto
                                                                    on delete set null,
    notas                text,
    porcentaje_indirecto numeric(5, 4)
        constraint presupuesto_porcentaje_indirecto_check
            check ((porcentaje_indirecto >= (0)::numeric) AND (porcentaje_indirecto <= 1.0000)),
    total                numeric(14, 6)           default 0     not null,
    created_at           timestamp with time zone default now() not null,
    updated_at           timestamp with time zone default now() not null,
    unique (proyecto_id, version)
);

alter table presupuesto
    owner to postgres;

create unique index ux_presupuesto_vigente
    on presupuesto (proyecto_id)
    where es_vigente;

create index ix_presupuesto_proyecto
    on presupuesto (proyecto_id);

