create table proyecto
(
    id                         bigint generated always as identity
        primary key,
    usuario_id                 bigint                                                         not null
        references usuario
            on delete restrict,
    nombre_proyecto            text                                                           not null,
    codigo                     varchar(50),
    descripcion                text,
    anio                       smallint                                                       not null,
    fecha_inicio               date,
    plazo_ejecucion            smallint
        constraint proyecto_plazo_ejecucion_check
            check (plazo_ejecucion > 0),
    plazo_unidad               varchar(10)
        constraint proyecto_plazo_unidad_check
            check ((plazo_unidad)::text = ANY
                   ((ARRAY ['SEMANA'::character varying, 'MES'::character varying])::text[])),
    estado                     varchar(12)              default 'BORRADOR'::character varying not null
        constraint proyecto_estado_check
            check ((estado)::text = ANY
                   ((ARRAY ['BORRADOR'::character varying, 'EN_PROCESO'::character varying, 'FINALIZADO'::character varying])::text[])),
    direccion_institucional    varchar(200)                                                   not null,
    subdireccion_institucional varchar(200),
    logo                       bytea,
    created_at                 timestamp with time zone default now()                         not null,
    updated_at                 timestamp with time zone default now()                         not null
);

alter table proyecto
    owner to postgres;

create index ix_proyecto_usuario
    on proyecto (usuario_id);

