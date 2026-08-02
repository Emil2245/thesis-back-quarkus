create table cronograma
(
    id                     bigint generated always as identity
        primary key,
    presupuesto_id         bigint                                 not null
        unique
        references presupuesto
            on delete cascade,
    unidad_tiempo          varchar(10)                            not null
        constraint cronograma_unidad_tiempo_check
            check ((unidad_tiempo)::text = ANY
                   ((ARRAY ['SEMANA'::character varying, 'MES'::character varying])::text[])),
    numero_periodos        smallint                               not null
        constraint cronograma_numero_periodos_check
            check (numero_periodos > 0),
    total_general_revisado numeric(14, 6),
    fecha_revision         timestamp with time zone,
    updated_at             timestamp with time zone default now() not null
);

alter table cronograma
    owner to postgres;

