create table plantilla_apu
(
    id                     bigint generated always as identity
        primary key,
    public_id              uuid                     default uuidv7() not null
        unique,
    nombre                 text                                      not null,
    tipo                   varchar(10)                               not null
        constraint plantilla_apu_tipo_check
            check ((tipo)::text = ANY ((ARRAY ['SISTEMA'::character varying, 'PERSONAL'::character varying])::text[])),
    usuario_id             bigint
        references usuario
            on delete cascade,
    descripcion_rubro      text,
    unidad                 varchar(10),
    especificacion_tecnica text,
    snapshot_secciones     jsonb                                     not null,
    created_at             timestamp with time zone default now()    not null,
    updated_at             timestamp with time zone default now()    not null,
    busqueda_fts           tsvector generated always as ((
        setweight(to_tsvector('spanish_unaccent'::regconfig, COALESCE(nombre, ''::text)), 'A'::"char") ||
        setweight(to_tsvector('spanish_unaccent'::regconfig, COALESCE(descripcion_rubro, ''::text)),
                  'B'::"char"))) stored,
    constraint plantilla_apu_check
        check ((((tipo)::text = 'SISTEMA'::text) AND (usuario_id IS NULL)) OR
               (((tipo)::text = 'PERSONAL'::text) AND (usuario_id IS NOT NULL)))
);

alter table plantilla_apu
    owner to postgres;

create index ix_plantilla_usuario
    on plantilla_apu (usuario_id);

create index ix_plantilla_apu_busqueda_fts
    on plantilla_apu using gin (busqueda_fts);

create trigger trg_public_id_immutable
    before update
        of public_id
    on plantilla_apu
    for each row
execute procedure fn_assert_public_id_immutable();

