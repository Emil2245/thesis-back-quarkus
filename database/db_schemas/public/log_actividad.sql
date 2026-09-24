create table log_actividad
(
    id                bigint generated always as identity
        primary key,
    usuario_id        bigint
                                                                references usuario
                                                                    on delete set null,
    evento            varchar(60)                               not null,
    entidad           varchar(30),
    entidad_id        bigint,
    detalle           jsonb,
    created_at        timestamp with time zone default now()    not null,
    public_id         uuid                     default uuidv7() not null,
    entidad_public_id uuid
);

alter table log_actividad
    owner to postgres;

create index ix_log_fecha
    on log_actividad (created_at desc);

create index ix_log_usuario
    on log_actividad (usuario_id);

create unique index ux_log_actividad_public_id
    on log_actividad (public_id);

create trigger trg_log_actividad_public_id_immutable
    before update
        of public_id
    on log_actividad
    for each row
execute procedure fn_assert_public_id_immutable();

