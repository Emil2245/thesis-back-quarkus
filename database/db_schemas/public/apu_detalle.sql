create table apu_detalle
(
    id                     bigint generated always as identity
        primary key,
    seccion_id             bigint                       not null
        references apu_seccion
            on delete cascade,
    insumo_id              bigint
        references insumo
            on delete restrict,
    apu_auxiliar_id        bigint
        references apu
            on delete restrict,
    descripcion            text                         not null,
    orden                  smallint                     not null,
    es_herramienta_menor   boolean        default false not null,
    cantidad               numeric(12, 6)
        constraint apu_detalle_cantidad_check
            check (cantidad > (0)::numeric),
    tarifa_jornal          numeric(14, 6)
        constraint apu_detalle_tarifa_jornal_check
            check (tarifa_jornal > (0)::numeric),
    costo_hora             numeric(14, 6) default 0     not null,
    rendimiento            numeric(10, 6)
        constraint apu_detalle_rendimiento_check
            check (rendimiento > (0)::numeric),
    unidad                 varchar(10),
    precio_unitario_tarifa numeric(14, 6)
        constraint apu_detalle_precio_unitario_tarifa_check
            check (precio_unitario_tarifa > (0)::numeric),
    costo                  numeric(14, 6) default 0     not null,
    constraint apu_detalle_check
        check (NOT ((insumo_id IS NOT NULL) AND (apu_auxiliar_id IS NOT NULL))),
    constraint apu_detalle_check1
        check ((NOT es_herramienta_menor) OR
               ((insumo_id IS NULL) AND (apu_auxiliar_id IS NULL) AND (tarifa_jornal IS NULL) AND
                (rendimiento IS NULL) AND (cantidad IS NULL)))
);

alter table apu_detalle
    owner to postgres;

create index ix_apu_detalle_seccion
    on apu_detalle (seccion_id);

create index ix_apu_detalle_insumo
    on apu_detalle (insumo_id);

create index ix_apu_detalle_auxiliar
    on apu_detalle (apu_auxiliar_id);

