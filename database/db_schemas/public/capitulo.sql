create table capitulo
(
    id             bigint generated always as identity
        primary key,
    presupuesto_id bigint                   not null
        references presupuesto
            on delete cascade,
    parent_id      bigint
        references capitulo
            on delete cascade,
    item           varchar(20)              not null,
    descripcion    text                     not null,
    orden          smallint                 not null,
    total          numeric(14, 6) default 0 not null,
    unique (presupuesto_id, item)
);

alter table capitulo
    owner to postgres;

create index ix_capitulo_presupuesto
    on capitulo (presupuesto_id);

create index ix_capitulo_parent
    on capitulo (parent_id);

