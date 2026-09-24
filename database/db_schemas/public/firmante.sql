create table firmante
(
    id          bigint generated always as identity
        primary key,
    public_id   uuid default uuidv7() not null
        unique,
    proyecto_id bigint                not null
        references proyecto
            on delete cascade,
    nombre      varchar(200)          not null,
    cargo       varchar(300)          not null,
    rol         varchar(12)           not null
        constraint firmante_rol_check
            check ((rol)::text = ANY
                   ((ARRAY ['CONSOLIDADO'::character varying, 'APROBADO'::character varying])::text[])),
    orden       smallint              not null,
    unique (proyecto_id, rol, orden)
);

alter table firmante
    owner to postgres;

create index ix_firmante_proyecto
    on firmante (proyecto_id);

create trigger trg_public_id_immutable
    before update
        of public_id
    on firmante
    for each row
execute procedure fn_assert_public_id_immutable();

