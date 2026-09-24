create table base_insumos
(
    id          bigint generated always as identity
        primary key,
    public_id   uuid                     default uuidv7() not null
        unique,
    nombre      varchar(200)                              not null,
    tipo        varchar(10)                               not null
        constraint base_insumos_tipo_check
            check ((tipo)::text = ANY
                   ((ARRAY ['CENTRAL'::character varying, 'PERSONAL'::character varying, 'PROYECTO'::character varying])::text[])),
    usuario_id  bigint
        references usuario
            on delete cascade,
    proyecto_id bigint
        references proyecto
            on delete cascade,
    archivada   boolean                  default false    not null,
    created_at  timestamp with time zone default now()    not null,
    updated_at  timestamp with time zone default now()    not null,
    constraint base_insumos_check
        check ((((tipo)::text = 'CENTRAL'::text) AND (usuario_id IS NULL) AND (proyecto_id IS NULL)) OR
               (((tipo)::text = 'PERSONAL'::text) AND (usuario_id IS NOT NULL) AND (proyecto_id IS NULL)) OR
               (((tipo)::text = 'PROYECTO'::text) AND (usuario_id IS NULL) AND (proyecto_id IS NOT NULL)))
);

alter table base_insumos
    owner to postgres;

create index ix_base_insumos_proyecto
    on base_insumos (proyecto_id);

create index ix_base_insumos_usuario
    on base_insumos (usuario_id);

create trigger trg_public_id_immutable
    before update
        of public_id
    on base_insumos
    for each row
execute procedure fn_assert_public_id_immutable();

