create table insumo
(
    id              bigint generated always as identity
        primary key,
    base_id         bigint                                 not null
        references base_insumos
            on delete cascade,
    codigo          varchar(50)                            not null,
    tipo            varchar(12)                            not null
        constraint insumo_tipo_check
            check ((tipo)::text = ANY
                   ((ARRAY ['EQUIPO'::character varying, 'MANO_OBRA'::character varying, 'MATERIAL'::character varying, 'TRANSPORTE'::character varying])::text[])),
    descripcion     text                                   not null,
    unidad          varchar(10)                            not null,
    precio_unitario numeric(14, 6)                         not null
        constraint insumo_precio_unitario_check
            check (precio_unitario > (0)::numeric),
    created_at      timestamp with time zone default now() not null,
    updated_at      timestamp with time zone default now() not null,
    unique (base_id, codigo),
    constraint insumo_check
        check (((tipo)::text <> ALL ((ARRAY ['EQUIPO'::character varying, 'MANO_OBRA'::character varying])::text[])) OR
               ((unidad)::text = 'h'::text))
);

alter table insumo
    owner to postgres;

create index ix_insumo_base
    on insumo (base_id);

