create table apu
(
    id                   bigint generated always as identity
        primary key,
    presupuesto_id       bigint                                 not null
        references presupuesto
            on delete cascade,
    codigo               varchar(20)                            not null,
    descripcion          text                                   not null,
    unidad               varchar(10)                            not null,
    es_auxiliar          boolean                  default false not null,
    porcentaje_indirecto numeric(5, 4)
        constraint apu_porcentaje_indirecto_check
            check ((porcentaje_indirecto >= (0)::numeric) AND (porcentaje_indirecto <= 1.0000)),
    porcentaje_descuento numeric(5, 4)            default 0     not null
        constraint apu_porcentaje_descuento_check
            check ((porcentaje_descuento >= (0)::numeric) AND (porcentaje_descuento <= 0.5000)),
    costo_directo        numeric(14, 6)           default 0     not null,
    costo_indirecto      numeric(14, 6)           default 0     not null,
    costo_total          numeric(14, 6)           default 0     not null,
    created_at           timestamp with time zone default now() not null,
    updated_at           timestamp with time zone default now() not null,
    unique (presupuesto_id, codigo)
);

alter table apu
    owner to postgres;

create index ix_apu_presupuesto
    on apu (presupuesto_id);

