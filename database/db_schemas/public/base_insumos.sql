create table base_insumos
(
    id          bigint generated always as identity
        primary key,
    nombre      varchar(200)                           not null,
    tipo        varchar(10)                            not null
        constraint base_insumos_tipo_check
            check ((tipo)::text = ANY ((ARRAY ['CENTRAL'::character varying, 'PROYECTO'::character varying])::text[])),
    proyecto_id bigint
        references proyecto
            on delete cascade,
    archivada   boolean                  default false not null,
    created_at  timestamp with time zone default now() not null,
    updated_at  timestamp with time zone default now() not null,
    constraint base_insumos_check
        check ((((tipo)::text = 'CENTRAL'::text) AND (proyecto_id IS NULL)) OR
               (((tipo)::text = 'PROYECTO'::text) AND (proyecto_id IS NOT NULL)))
);

alter table base_insumos
    owner to postgres;

create index ix_base_insumos_proyecto
    on base_insumos (proyecto_id);

