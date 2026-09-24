create table apu
(
    id                     bigint generated always as identity
        primary key,
    public_id              uuid                     default uuidv7() not null
        unique,
    presupuesto_id         bigint                                    not null
        references presupuesto
            on delete cascade,
    codigo                 varchar(20)                               not null,
    descripcion            text                                      not null,
    unidad                 varchar(10)                               not null,
    porcentaje_indirecto   numeric(5, 4),
    porcentaje_descuento   numeric(5, 4)            default 0        not null,
    especificacion_tecnica text,
    costo_directo          numeric(14, 6)           default 0        not null,
    costo_indirecto        numeric(14, 6)           default 0        not null,
    costo_total            numeric(14, 6)           default 0        not null,
    created_at             timestamp with time zone default now()    not null,
    updated_at             timestamp with time zone default now()    not null,
    unique (presupuesto_id, codigo)
);

alter table apu
    owner to postgres;

create index ix_apu_presupuesto
    on apu (presupuesto_id);

create trigger trg_public_id_immutable
    before update
        of public_id
    on apu
    for each row
execute procedure fn_assert_public_id_immutable();

