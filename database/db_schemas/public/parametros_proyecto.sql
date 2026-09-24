create table parametros_proyecto
(
    proyecto_id                    bigint                                                              not null
        primary key
        references proyecto
            on delete cascade,
    porcentaje_herramienta_menor   numeric(5, 4)            default 0.0500                             not null,
    porcentaje_indirecto           numeric(5, 4),
    iva                            numeric(5, 4)            default 0.1500                             not null,
    moneda                         varchar(10)              default 'USD'::character varying           not null,
    mostrar_secciones_vacias       boolean                  default true                               not null,
    sufijos_seccion_activos        boolean                  default true                               not null,
    mostrar_subtotales_seccion     boolean                  default true                               not null,
    mostrar_subtotales_pie         boolean                  default false                              not null,
    mostrar_nombre_proyecto_header boolean                  default false                              not null,
    enumerar_apus                  boolean                  default false                              not null,
    mensaje_footer                 text                     default 'Este precio no incluye IVA'::text not null,
    modo_codigo_rubro              varchar(12)              default 'AUTOGENERADO'::character varying  not null
        constraint parametros_proyecto_modo_codigo_rubro_check
            check ((modo_codigo_rubro)::text = ANY
                   ((ARRAY ['AUTOGENERADO'::character varying, 'MANUAL'::character varying])::text[])),
    updated_at                     timestamp with time zone default now()                              not null
);

alter table parametros_proyecto
    owner to postgres;

