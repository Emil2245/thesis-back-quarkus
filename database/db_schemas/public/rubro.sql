create table rubro
(
    id              bigint generated always as identity
        primary key,
    capitulo_id     bigint                          not null
        references capitulo
            on delete cascade,
    apu_id          bigint                          not null
        unique
        references apu
            on delete restrict,
    item            varchar(20)                     not null,
    codigo          varchar(20)                     not null,
    descripcion     text                            not null,
    unidad          varchar(10)                     not null,
    cantidad        numeric(12, 6)                  not null
        constraint rubro_cantidad_check
            check (cantidad >= (0)::numeric),
    precio_unitario numeric(14, 6) default 0        not null,
    precio_total    numeric(14, 6) default 0        not null,
    public_id       uuid           default uuidv7() not null
        unique
);

alter table rubro
    owner to postgres;

create index ix_rubro_capitulo
    on rubro (capitulo_id);

create trigger trg_public_id_immutable
    before update
        of public_id
    on rubro
    for each row
execute procedure fn_assert_public_id_immutable();

