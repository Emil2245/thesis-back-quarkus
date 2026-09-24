create table presupuesto_descuento_global
(
    presupuesto_id    bigint                                 not null
        primary key
        references presupuesto
            on delete cascade,
    porcentaje_actual numeric(5, 4)            default 0     not null,
    updated_at        timestamp with time zone default now() not null
);

alter table presupuesto_descuento_global
    owner to postgres;

