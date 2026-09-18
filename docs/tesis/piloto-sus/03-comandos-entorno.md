# Comandos del entorno

Requisitos: PostgreSQL 18 limpio, Java/Gradle, Bruno CLI 4.1 y los repositorios `thesis-back-quarkus` y `../thesis-front-react`.

Backend:

```bash
cd thesis-back-quarkus
./gradlew --console=plain quarkusDev
```

Credenciales seed (V004 directo, sin bootstrap manual): `admin@uce.edu.ec` / `admin` (SUPER_ADMIN), `john@uce.edu.ec` / `User123123`, `ana@gmail.com` / `User123123`. El `dev.bru` ya trae `adminEmail`/`adminPassword` por defecto; para override: `bru run ... --env dev --env-var adminEmail=admin@uce.edu.ec --env-var adminPassword=admin`.

El panel `12-admin` usa el SUPER_ADMIN sembrado por V004. No se añade otro seed de producción: para una cuenta temporal distinta, promover una cuenta con `UPDATE usuario SET rol='SUPER_ADMIN' WHERE email='john@uce.edu.ec';`. La request `TC-12-00a` debe fallar con 401 si esas variables siguen vacías.

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
