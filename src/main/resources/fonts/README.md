# Fuentes embebidas — Plan 031 (P-37)

Esta carpeta contiene las fuentes TrueType embebidas que los writers de
documentos exportan en línea con el canon del Plan 031 §PDF y §Seguridad.
Las fuentes viven en el classpath para que el archivo generado no dependa
de las fuentes instaladas en el host y para hacer verificable su origen.

## `LiberationSans-Regular.ttf`

| Atributo | Valor |
|---|---|
| SHA-256 | `76d04c18ea243f426b7de1f3ad208e927008f961dc5945e5aad352d0dfde8ee8` |
| Tamaño | 410 712 bytes |
| Tipo | TrueType Font data |
| Licencia | [SIL Open Font License 1.1](https://scripts.sil.org/OFL) |
| Origen | [Red Hat Liberation fonts](https://github.com/liberationfonts/liberation-fonts) — fork métrico compatible con las familias propietarias Arial / Helvetica / Times New Roman. |
| Uso | Writer PDF (`CronogramaPdfWriter`) como fuente embebida en cada página, vía `BaseFont.createFont(... BaseFont.EMBEDDED ...)` para garantizar que el PDF es reproducible sin dependencias externas. |

La licencia OFL 1.1 es copy-compatible con Apache-2.0 y permite el uso comercial,
la redistribución y la embedción en archivos binarios; el presente README
constituye la trazabilidad de origen exigida por Plan 031 §Plantillas.