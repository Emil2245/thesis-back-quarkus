create table descuento_global_snapshot
(
    presupuesto_id      bigint                                 not null
        primary key
        references presupuesto
            on delete cascade,
    porcentaje_aplicado numeric(5, 4)                          not null,
    valores_originales  jsonb                                  not null,
    created_at          timestamp with time zone default now() not null
);

alter table descuento_global_snapshot
    owner to postgres;

