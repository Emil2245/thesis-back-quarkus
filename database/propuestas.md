classDiagram
direction BT
class actividad {
   bigint cronograma_id
   bigint rubro_id
   numeric(7,4) peso_ponderado
   jsonb avance_por_periodo
   uuid public_id
   bigint id
}
class apu {
   uuid public_id
   bigint presupuesto_id
   varchar(20) codigo
   text descripcion
   varchar(10) unidad
   numeric(5,4) porcentaje_indirecto
   numeric(5,4) porcentaje_descuento
   text especificacion_tecnica
   numeric(14,6) costo_directo
   numeric(14,6) costo_indirecto
   numeric(14,6) costo_total
   timestamp with time zone created_at
   timestamp with time zone updated_at
   bigint id
}
class apu_detalle {
   uuid public_id
   bigint seccion_id
   bigint insumo_id
   text descripcion
   smallint orden
   boolean es_herramienta_menor
   numeric(12,6) cantidad
   numeric(14,6) tarifa_jornal
   numeric(14,6) costo_hora
   numeric(10,6) rendimiento
   varchar(10) unidad
   numeric(14,6) precio_unitario_tarifa
   numeric(14,6) costo
   bigint id
}
class apu_seccion {
   bigint apu_id
   varchar(12) tipo
   numeric(14,6) subtotal
   smallint orden
   bigint id
}
class base_insumos {
   uuid public_id
   varchar(200) nombre
   varchar(10) tipo
   bigint usuario_id
   bigint proyecto_id
   boolean archivada
   timestamp with time zone created_at
   timestamp with time zone updated_at
   bigint id
}
class capitulo {
   bigint presupuesto_id
   bigint parent_id
   varchar(20) item
   text descripcion
   smallint orden
   numeric(14,6) total
   uuid public_id
   bigint id
}
class cronograma {
   bigint presupuesto_id
   varchar(10) unidad_tiempo
   smallint numero_periodos
   numeric(14,6) total_general_revisado
   timestamp with time zone fecha_revision
   timestamp with time zone updated_at
   uuid public_id
   char(64) presupuesto_fingerprint_revisado
   bigint id
}
class cronograma_actividad {
   bigint presupuesto_id
   bigint rubro_id
   timestamp with time zone created_at
   bigint id
}
class descuento_global_snapshot {
   numeric(5,4) porcentaje_aplicado
   jsonb valores_originales
   timestamp with time zone created_at
   bigint presupuesto_id
}
class firmante {
   uuid public_id
   bigint proyecto_id
   varchar(200) nombre
   varchar(300) cargo
   varchar(12) rol
   smallint orden
   bigint id
}
class flyway_schema_history {
   varchar(50) version
   varchar(200) description
   varchar(20) type
   varchar(1000) script
   integer checksum
   varchar(100) installed_by
   timestamp installed_on
   integer execution_time
   boolean success
   integer installed_rank
}
class insumo {
   uuid public_id
   bigint base_id
   varchar(50) codigo
   varchar(12) tipo
   text descripcion
   varchar(10) unidad
   numeric(14,6) precio_unitario
   timestamp with time zone created_at
   timestamp with time zone updated_at
   bigint id
}
class log_actividad {
   bigint usuario_id
   varchar(60) evento
   varchar(30) entidad
   bigint entidad_id
   jsonb detalle
   timestamp with time zone created_at
   uuid public_id
   uuid entidad_public_id
   bigint id
}
class parametros_proyecto {
   numeric(5,4) porcentaje_herramienta_menor
   numeric(5,4) porcentaje_indirecto
   numeric(5,4) iva
   varchar(10) moneda
   boolean mostrar_secciones_vacias
   boolean sufijos_seccion_activos
   boolean mostrar_subtotales_seccion
   boolean mostrar_subtotales_pie
   boolean mostrar_nombre_proyecto_header
   boolean enumerar_apus
   text mensaje_footer
   varchar(12) modo_codigo_rubro
   timestamp with time zone updated_at
   bigint proyecto_id
}
class parametros_sistema {
   numeric(5,4) porcentaje_herramienta_menor
   numeric(5,4) porcentaje_indirecto
   numeric(5,4) iva
   numeric(5,4) rango_hm_min
   numeric(5,4) rango_hm_max
   numeric(5,4) rango_ci_min
   numeric(5,4) rango_ci_max
   numeric(5,4) rango_descuento_min
   numeric(5,4) rango_descuento_max
   numeric(5,4) rango_iva_min
   numeric(5,4) rango_iva_max
   varchar(10) moneda
   boolean mostrar_secciones_vacias
   boolean sufijos_seccion_activos
   boolean mostrar_subtotales_seccion
   boolean mostrar_subtotales_pie
   boolean mostrar_nombre_proyecto_header
   boolean enumerar_apus
   text mensaje_footer
   varchar(12) modo_codigo_rubro
   timestamp with time zone updated_at
   smallint id
}
class plantilla_apu {
   uuid public_id
   text nombre
   varchar(10) tipo
   bigint usuario_id
   text descripcion_rubro
   varchar(10) unidad
   text especificacion_tecnica
   jsonb snapshot_secciones
   timestamp with time zone created_at
   timestamp with time zone updated_at
   tsvector busqueda_fts
   bigint id
}
class plantilla_proyecto {
   uuid public_id
   bigint usuario_id
   text nombre
   timestamp with time zone fecha_creacion
   jsonb snapshot_estructura
   text descripcion
   bigint id
}
class presupuesto {
   uuid public_id
   bigint proyecto_id
   smallint version
   boolean es_vigente
   bigint origen_id
   text notas
   numeric(5,4) porcentaje_indirecto
   numeric(14,6) total
   timestamp with time zone created_at
   timestamp with time zone updated_at
   bigint id
}
class presupuesto_descuento_global {
   numeric(5,4) porcentaje_actual
   timestamp with time zone updated_at
   bigint presupuesto_id
}
class presupuesto_rubro {
   bigint presupuesto_id
   bigint apu_id
   timestamp with time zone created_at
   bigint id
}
class proyecto {
   uuid public_id
   bigint usuario_id
   text nombre_proyecto
   varchar(50) codigo
   text descripcion
   smallint anio
   date fecha_inicio
   smallint plazo_ejecucion
   varchar(10) plazo_unidad
   varchar(12) estado
   varchar(200) direccion_institucional
   varchar(200) subdireccion_institucional
   bytea logo
   text titulo_et_1
   text titulo_et_2
   bigint plantilla_proyecto_origen_id
   timestamp with time zone created_at
   timestamp with time zone updated_at
   bigint id
}
class refresh_token {
   bigint usuario_id
   varchar(64) token_hash
   timestamp with time zone expira_en
   timestamp with time zone revocado_en
   timestamp with time zone created_at
   bigint id
}
class rubro {
   bigint capitulo_id
   bigint apu_id
   varchar(20) item
   varchar(20) codigo
   text descripcion
   varchar(10) unidad
   numeric(12,6) cantidad
   numeric(14,6) precio_unitario
   numeric(14,6) precio_total
   uuid public_id
   bigint id
}
class token_usuario {
   bigint usuario_id
   varchar(20) tipo
   varchar(64) token_hash
   varchar(320) email_destino
   timestamp with time zone expira_en
   timestamp with time zone usado_en
   timestamp with time zone created_at
   bigint id
}
class unidad_catalogo {
   varchar(100) descripcion
   varchar(10) codigo
}
class usuario {
   uuid public_id
   varchar(200) nombre
   varchar(320) email
   varchar(72) password_hash
   varchar(12) rol
   boolean email_verificado
   boolean activo
   timestamp with time zone created_at
   timestamp with time zone updated_at
   bigint id
}
class valor_referencia {
   varchar(100) valor
   text descripcion
   varchar(200) fuente
   timestamp with time zone updated_at
   varchar(50) clave
}

actividad  -->  cronograma : cronograma_id:id
actividad  -->  rubro : rubro_id:id
apu  -->  presupuesto : presupuesto_id:id
apu_detalle  -->  apu_seccion : seccion_id:id
apu_detalle  -->  insumo : insumo_id:id
apu_seccion  -->  apu : apu_id:id
base_insumos  -->  proyecto : proyecto_id:id
base_insumos  -->  usuario : usuario_id:id
capitulo  -->  capitulo : parent_id:id
capitulo  -->  presupuesto : presupuesto_id:id
cronograma  -->  presupuesto : presupuesto_id:id
cronograma_actividad  -->  presupuesto : presupuesto_id:id
cronograma_actividad  -->  rubro : rubro_id:id
descuento_global_snapshot  -->  presupuesto : presupuesto_id:id
firmante  -->  proyecto : proyecto_id:id
insumo  -->  base_insumos : base_id:id
log_actividad  -->  usuario : usuario_id:id
parametros_proyecto  -->  proyecto : proyecto_id:id
plantilla_apu  -->  usuario : usuario_id:id
plantilla_proyecto  -->  usuario : usuario_id:id
presupuesto  -->  presupuesto : origen_id:id
presupuesto  -->  proyecto : proyecto_id:id
presupuesto_descuento_global  -->  presupuesto : presupuesto_id:id
presupuesto_rubro  -->  apu : apu_id:id
presupuesto_rubro  -->  presupuesto : presupuesto_id:id
proyecto  -->  plantilla_proyecto : plantilla_proyecto_origen_id:id
proyecto  -->  usuario : usuario_id:id
refresh_token  -->  usuario : usuario_id:id
rubro  -->  apu : apu_id:id
rubro  -->  capitulo : capitulo_id:id
token_usuario  -->  usuario : usuario_id:id
