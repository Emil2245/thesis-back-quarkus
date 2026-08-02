create table usuario
(
    id               bigint generated always as identity
        primary key,
    nombre           varchar(200)                                                  not null,
    email            varchar(320)                                                  not null
        unique,
    password_hash    varchar(72)                                                   not null,
    rol              varchar(12)              default 'USUARIO'::character varying not null
        constraint usuario_rol_check
            check ((rol)::text = ANY
                   ((ARRAY ['USUARIO'::character varying, 'SUPER_ADMIN'::character varying])::text[])),
    email_verificado boolean                  default false                        not null,
    activo           boolean                  default true                         not null,
    created_at       timestamp with time zone default now()                        not null,
    updated_at       timestamp with time zone default now()                        not null
);

alter table usuario
    owner to postgres;

