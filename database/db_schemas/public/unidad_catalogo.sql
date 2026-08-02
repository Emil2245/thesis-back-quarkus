create table unidad_catalogo
(
    codigo      varchar(10)  not null
        primary key,
    descripcion varchar(100) not null
);

alter table unidad_catalogo
    owner to postgres;

