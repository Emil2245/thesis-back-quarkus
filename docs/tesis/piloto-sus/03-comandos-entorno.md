# Comandos del entorno

Requisitos: PostgreSQL 18 limpio, Java/Gradle, Bruno CLI 4.1 y los repositorios `thesis-back-quarkus` y `../thesis-front-react`.

Backend:

```bash
cd thesis-back-quarkus
./gradlew --console=plain quarkusDev
```

Bootstrap desechable (solo para la corrida local, no es migración): `psql "$QUARKUS_DATASOURCE_JDBC_URL" -v ON_ERROR_STOP=1 -c "UPDATE usuario SET rol='SUPER_ADMIN' WHERE email='john.doe@uce.edu.ec';"`; luego ejecutar `bru run ... --env dev --env-var adminEmail=john.doe@uce.edu.ec --env-var adminPassword=Clave1234`.

El panel `12-admin` requiere un SUPER_ADMIN que V004 no siembra. No se añade migración ni seed de producción: proporcionar `adminEmail` y `adminPassword` mediante un environment local no versionado, o ejecutar el procedimiento operativo de desarrollo aprobado para promover temporalmente una cuenta sembrada de John/Ana usando su hash existente. La request `TC-12-00a` debe fallar con 401 si esas variables siguen vacías.

Frontend:

```bash
cd ../thesis-front-react
pnpm install
pnpm run dev
```

Ejecutar la colección con el backend arriba:

```bash
( cd api/bruno && bru run 12-admin/ --env dev )
```

La corrida dinámica exige PostgreSQL 18 desechable, fast-jar y un bootstrap externo del SUPER_ADMIN. Evidencia medida (2026-09-09): Bruno CLI 4.1.0, 16/16 requests y 27/27 tests JS, 0 fallos/errores/skips, 9,993 ms CLI / 13.55 s wall. John Doe se promovió temporalmente a SUPER_ADMIN solo en la BD desechable; las credenciales se pasaron mediante variables CLI.
