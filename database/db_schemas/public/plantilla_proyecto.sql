create table plantilla_proyecto
(
    id                  bigint generated always as identity
        primary key,
    public_id           uuid                     default uuidv7() not null
        unique,
    usuario_id          bigint                                    not null
        references usuario
            on delete cascade,
    nombre              text                                      not null,
    fecha_creacion      timestamp with time zone default now()    not null,
    snapshot_estructura jsonb                                     not null,
    descripcion         text
);

alter table plantilla_proyecto
    owner to postgres;

create index ix_plantilla_proyecto_usuario
    on plantilla_proyecto (usuario_id);

create trigger trg_public_id_immutable
    before update
        of public_id
    on plantilla_proyecto
    for each row
execute procedure fn_assert_public_id_immutable();

