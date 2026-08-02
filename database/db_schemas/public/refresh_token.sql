create table refresh_token
(
    id          bigint generated always as identity
        primary key,
    usuario_id  bigint                                 not null
        references usuario
            on delete cascade,
    token_hash  varchar(64)                            not null
        unique,
    expira_en   timestamp with time zone               not null,
    revocado_en timestamp with time zone,
    created_at  timestamp with time zone default now() not null
);

alter table refresh_token
    owner to postgres;

create index ix_refresh_usuario
    on refresh_token (usuario_id);

