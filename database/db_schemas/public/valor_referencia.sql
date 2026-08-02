create table valor_referencia
(
    clave       varchar(50)                            not null
        primary key,
    valor       varchar(100)                           not null,
    descripcion text                                   not null,
    fuente      varchar(200)                           not null,
    updated_at  timestamp with time zone default now() not null
);

alter table valor_referencia
    owner to postgres;

