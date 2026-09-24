create table presupuesto_rubro
(
    id             bigint generated always as identity
        primary key,
    presupuesto_id bigint                                 not null
        references presupuesto
            on delete cascade,
    apu_id         bigint                                 not null
        references apu
            on delete cascade,
    created_at     timestamp with time zone default now() not null,
    unique (presupuesto_id, apu_id)
);

alter table presupuesto_rubro
    owner to postgres;

