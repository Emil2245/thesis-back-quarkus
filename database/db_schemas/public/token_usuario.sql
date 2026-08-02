create table token_usuario
(
    id            bigint generated always as identity
        primary key,
    usuario_id    bigint                                 not null
        references usuario
            on delete cascade,
    tipo          varchar(20)                            not null
        constraint token_usuario_tipo_check
            check ((tipo)::text = ANY
                   ((ARRAY ['VERIFICACION_EMAIL'::character varying, 'RESET_PASSWORD'::character varying, 'INVITACION'::character varying, 'CAMBIO_EMAIL'::character varying])::text[])),
    token_hash    varchar(64)                            not null
        unique,
    email_destino varchar(320),
    expira_en     timestamp with time zone               not null,
    usado_en      timestamp with time zone,
    created_at    timestamp with time zone default now() not null
);

alter table token_usuario
    owner to postgres;

create index ix_token_usuario
    on token_usuario (usuario_id);

