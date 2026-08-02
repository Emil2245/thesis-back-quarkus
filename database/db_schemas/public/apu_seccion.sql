create table apu_seccion
(
    id       bigint generated always as identity
        primary key,
    apu_id   bigint                   not null
        references apu
            on delete cascade,
    tipo     varchar(12)              not null
        constraint apu_seccion_tipo_check
            check ((tipo)::text = ANY
                   ((ARRAY ['EQUIPO'::character varying, 'MANO_OBRA'::character varying, 'MATERIAL'::character varying, 'TRANSPORTE'::character varying])::text[])),
    subtotal numeric(14, 6) default 0 not null,
    orden    smallint                 not null,
    unique (apu_id, tipo)
);

alter table apu_seccion
    owner to postgres;

create index ix_apu_seccion_apu
    on apu_seccion (apu_id);

