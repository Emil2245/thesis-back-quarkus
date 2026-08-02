create table log_actividad
(
    id         bigint generated always as identity
        primary key,
    usuario_id bigint
                                                      references usuario
                                                          on delete set null,
    evento     varchar(60)                            not null,
    entidad    varchar(30),
    entidad_id bigint,
    detalle    jsonb,
    created_at timestamp with time zone default now() not null
);

alter table log_actividad
    owner to postgres;

create index ix_log_fecha
    on log_actividad (created_at desc);

create index ix_log_usuario
    on log_actividad (usuario_id);

