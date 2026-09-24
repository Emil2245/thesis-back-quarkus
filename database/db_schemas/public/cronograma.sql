create table cronograma
(
    id                               bigint generated always as identity
        primary key,
    presupuesto_id                   bigint                                    not null
        unique
        references presupuesto
            on delete cascade,
    unidad_tiempo                    varchar(10)                               not null
        constraint cronograma_unidad_tiempo_check
            check ((unidad_tiempo)::text = ANY
                   ((ARRAY ['SEMANA'::character varying, 'MES'::character varying])::text[])),
    numero_periodos                  smallint                                  not null,
    total_general_revisado           numeric(14, 6),
    fecha_revision                   timestamp with time zone,
    updated_at                       timestamp with time zone default now()    not null,
    public_id                        uuid                     default uuidv7() not null
        unique,
    presupuesto_fingerprint_revisado char(64)
        constraint chk_cronograma_fingerprint
            check ((presupuesto_fingerprint_revisado IS NULL) OR
                   (presupuesto_fingerprint_revisado ~ '^[0-9a-f]{64}$'::text)),
    constraint chk_cronograma_periodos_unidad
        check ((((unidad_tiempo)::text = 'SEMANA'::text) AND ((numero_periodos >= 1) AND (numero_periodos <= 520))) OR
               (((unidad_tiempo)::text = 'MES'::text) AND ((numero_periodos >= 1) AND (numero_periodos <= 120))))
);

alter table cronograma
    owner to postgres;

create trigger trg_public_id_immutable
    before update
        of public_id
    on cronograma
    for each row
execute procedure fn_assert_public_id_immutable();

