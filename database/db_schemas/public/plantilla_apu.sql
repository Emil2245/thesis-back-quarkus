create table plantilla_apu
(
    id                 bigint generated always as identity
        primary key,
    nombre             text                                   not null,
    tipo               varchar(10)                            not null
        constraint plantilla_apu_tipo_check
            check ((tipo)::text = ANY ((ARRAY ['SISTEMA'::character varying, 'PERSONAL'::character varying])::text[])),
    usuario_id         bigint
        references usuario
            on delete cascade,
    descripcion_rubro  text,
    unidad             varchar(10),
    snapshot_secciones jsonb                                  not null,
    created_at         timestamp with time zone default now() not null,
    updated_at         timestamp with time zone default now() not null,
    constraint plantilla_apu_check
        check ((((tipo)::text = 'SISTEMA'::text) AND (usuario_id IS NULL)) OR
               (((tipo)::text = 'PERSONAL'::text) AND (usuario_id IS NOT NULL)))
);

alter table plantilla_apu
    owner to postgres;

create index ix_plantilla_usuario
    on plantilla_apu (usuario_id);

