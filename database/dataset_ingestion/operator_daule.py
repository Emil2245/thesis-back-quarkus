"""Bounded stdlib data-only operator. Never retries a database subprocess."""
import argparse
import copy
import csv
from decimal import Decimal, InvalidOperation
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import time
import uuid

TYPES = ('EQUIPO', 'MANO_OBRA', 'MATERIAL', 'TRANSPORTE')
ROOT = Path(__file__).resolve().parents[2]
SOURCE = Path(__file__).resolve().parent / 'sources/dataset_maestro_daule_v1'
RELEASE = Path(__file__).parent / 'releases/daule-v1/release.json'
PACKAGE = ('README.md', 'apu_componentes_v1.csv', 'apu_conceptos_v1.csv',
           'apu_detalles_origen.csv', 'apu_origenes.csv', 'apus_posibles_equivalentes.csv',
           'apus_v1.csv', 'archivos_fuente.csv', 'fuentes.csv', 'insumo_aliases.csv',
           'insumos_posibles_duplicados.csv', 'insumos_v1.csv', 'manifiesto.json',
           'precios_observados_v1.csv', 'validaciones_consolidacion.csv')
POLICY = 'original-positive-min(source_id,dataset_origen,precio_observado_id);HM-placeholder-project-parameter;exact-identity;v1'


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'))


def digest(value):
    return hashlib.sha256(canonical(value).encode()).hexdigest()


def source_hashes(source):
    return {name: hashlib.sha256((source / name).read_bytes()).hexdigest() for name in PACKAGE}


def read_tables(source):
    tables = {}
    for name in ('insumos_v1', 'precios_observados_v1', 'apus_v1', 'apu_componentes_v1'):
        with (source / (name + '.csv')).open(encoding='utf-8-sig', newline='') as stream:
            tables[name] = list(csv.DictReader(stream))
    return tables


def unique(rows, key):
    result = {}
    for row in rows:
        value = row[key]
        if not value or value in result:
            raise ValueError('Missing/duplicate ' + key)
        result[value] = row
    return result


def number(value, integer_digits, positive=True):
    try:
        n = Decimal(value)
    except (InvalidOperation, ValueError):
        raise ValueError('Invalid numeric value') from None
    if not n.is_finite() or (positive and n <= 0) or n < 0:
        raise ValueError('Nonfinite/negative/nonpositive numeric value')
    if n >= Decimal(10) ** integer_digits or n != n.quantize(Decimal('0.000001')):
        raise ValueError('Numeric precision overflow')
    return value


def unit(value):
    if not value.strip() or len(value) > 10:
        raise ValueError('Invalid unit')
    return value


def price_key(row):
    return row['source_id'], row['dataset_origen'], row['precio_observado_id']


def transform(tables, prefix, base_name):
    if not re.fullmatch(r'[A-Z][A-Z0-9-]{0,10}', prefix):
        raise ValueError('Unsafe prefix')
    if not base_name.strip() or len(base_name) > 200:
        raise ValueError('Invalid base name')
    insumos = unique(tables['insumos_v1'], 'insumo_v1_id')
    apus = unique(tables['apus_v1'], 'apu_v1_id')
    unique(tables['precios_observados_v1'], 'precio_observado_id')
    unique(tables['apu_componentes_v1'], 'apu_componente_v1_id')
    observations = {}
    for p in tables['precios_observados_v1']:
        if p['insumo_v1_id'] not in insumos:
            raise ValueError('Missing observation FK')
        if p['precio_unitario']:
            number(p['precio_unitario'], 8)
            if p['unidad'] != insumos[p['insumo_v1_id']]['unidad']:
                raise ValueError('Observation unit mismatch')
            observations.setdefault(p['insumo_v1_id'], []).append(p)
    out = {'policy': POLICY, 'prefix': prefix, 'base': {'nombre': base_name},
           'insumos': [], 'plantillas': []}
    codes = {}
    for key, row in insumos.items():
        if row['tipo'] not in TYPES or row['es_herramienta_menor'] not in ('0', '1'):
            raise ValueError('Invalid insumo type/HM flag')
        if row['es_herramienta_menor'] == '1':
            if row['tipo'] != 'EQUIPO' or row['unidad'] != '%MO':
                raise ValueError('Invalid HM')
            continue
        unit(row['unidad'])
        if row['tipo'] in TYPES[:2] and row['unidad'] != 'h':
            raise ValueError('EQ/MO unit must be h')
        if not row['nombre_canonico'].strip() or not re.fullmatch(r'INSV1-[a-f0-9]{24}', key):
            raise ValueError('Invalid canonical insumo')
        candidates = observations.get(key, [])
        if not candidates:
            raise ValueError('No positive original price')
        p = min(candidates, key=price_key)
        code = prefix + '-' + key
        if len(code) > 50:
            raise ValueError('Code too long')
        codes[key] = code
        out['insumos'].append({'source_id': key, 'codigo': code, 'tipo': row['tipo'],
                               'descripcion': row['nombre_canonico'], 'unidad': row['unidad'],
                               'precio_unitario': p['precio_unitario'],
                               'observation_id': p['precio_observado_id'],
                               'price_source': list(price_key(p))})
    components = {key: {kind: [] for kind in TYPES} for key in apus}
    orders = set()
    for row in tables['apu_componentes_v1']:
        a, i, kind = row['apu_v1_id'], row['insumo_v1_id'], row['tipo_seccion']
        if a not in apus or i not in insumos or kind not in TYPES:
            raise ValueError('Missing component FK/type')
        identity = (a, row['orden'])
        if not row['orden'].isdigit() or int(row['orden']) < 1 or identity in orders:
            raise ValueError('Invalid/duplicate component order')
        orders.add(identity)
        if row['unidad'] != insumos[i]['unidad'] or kind != insumos[i]['tipo']:
            raise ValueError('Component unit/type mismatch')
        if row['es_herramienta_menor'] != insumos[i]['es_herramienta_menor']:
            raise ValueError('Component HM mismatch')
        if row['es_herramienta_menor'] == '1':
            number(row['porcentaje_herramienta_menor'], 1)
            line = {'esHerramientaMenor': True}
        else:
            line = {'insumoCodigo': codes[i], 'cantidad': number(row['cantidad'], 6)}
            if kind in TYPES[:2]:
                line['rendimiento'] = number(row['rendimiento'], 4)
            elif row['rendimiento']:
                raise ValueError('MAT/TR rendimiento must be absent')
        # CSV export order, intentionally not sorted by code or description.
        components[a][kind].append(line)
    for key, row in apus.items():
        unit(row['unidad'])
        number(row['rendimiento_general'], 4, positive=False)
        if not row['descripcion_canonica'].strip():
            raise ValueError('Empty canonical APU description')
        snapshot = {'secciones': [{'tipo': kind, 'lineas': components[key][kind]} for kind in TYPES]}
        out['plantillas'].append({'source_id': key, 'nombre': row['descripcion_canonica'],
                                 'descripcion_rubro': row['descripcion_canonica'],
                                 'unidad': row['unidad'], 'especificacion_tecnica': None,
                                 'snapshot_secciones': snapshot})
    return out


def uuid7():
    value = (int(time.time() * 1000) << 80) | (7 << 76)
    value |= secrets.randbits(12) << 64
    value |= (2 << 62) | secrets.randbits(62)
    return str(uuid.UUID(int=value))


def operator_hashes():
    return {name: hashlib.sha256((Path(__file__).parent / name).read_bytes()).hexdigest()
            for name in ('operator_daule.py', 'load.sql', 'insert.sql')}


def assign_ids(payload, hashes):
    release = copy.deepcopy(payload)
    for row in [release['base']] + release['insumos'] + release['plantillas']:
        row['public_id'] = uuid7()
    release['source_hashes'] = hashes
    release['operator_hashes'] = operator_hashes()
    release['release_sha256'] = digest(release)
    return release


def validate_release(release, payload, hashes):
    stripped = copy.deepcopy(release)
    checksum = stripped.pop('release_sha256', None)
    if checksum != digest(stripped) or stripped.pop('source_hashes', None) != hashes:
        raise ValueError('Release/source hash mismatch: STOP')
    if stripped.pop('operator_hashes', None) != operator_hashes():
        raise ValueError('Operator hash mismatch: STOP')
    seen = set()
    for row in [stripped['base']] + stripped['insumos'] + stripped['plantillas']:
        value = row.pop('public_id')
        identifier = uuid.UUID(value)
        if identifier.version != 7 or identifier.variant != uuid.RFC_4122 or value in seen:
            raise ValueError('Invalid/duplicate public UUIDv7')
        seen.add(value)
    if stripped != payload:
        raise ValueError('Prepared payload mismatch: STOP')
    return release


def durable_create(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    # Exclusive creation prevents concurrent prepare overwriting the identity map.
    # An interrupted incomplete file is rejected, never silently regenerated.
    with path.open('x', encoding='utf-8') as stream:
        stream.write(canonical(value) + '\n')
        stream.flush()
        os.fsync(stream.fileno())
    descriptor = os.open(path.parent, os.O_RDONLY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def prepare(source, release_path, prefix, name):
    hashes = source_hashes(source)
    payload = transform(read_tables(source), prefix, name)
    if release_path.exists():
        return validate_release(json.loads(release_path.read_text()), payload, hashes)
    release = assign_ids(payload, hashes)
    durable_create(release_path, release)
    return release


def render_sql(release, apply=False, verify=False):
    encoded = canonical(release).replace('\\', '\\\\').replace('\n', '\\n').replace('\r', '\\r').replace('\t', '\\t')
    sql = (Path(__file__).parent / 'load.sql').read_text()
    sql = sql.replace('-- PAYLOAD', encoded + '\n\\.')
    sql = sql.replace('-- WRITE', '' if verify else (Path(__file__).parent / 'insert.sql').read_text())
    sql = sql.replace('-- END', 'COMMIT;' if apply and not verify else 'ROLLBACK;')
    return sql


def local_command():
    return ['docker', 'compose', '--project-directory', str(ROOT), '-f', str(ROOT / 'docker-compose.yml'),
            'exec', '-T', 'postgres', 'psql', '-X', '-q', '-A', '-t', '-v', 'ON_ERROR_STOP=1',
            '-U', 'postgres', '-d', 'propuestas']


def database_evidence(sql, release):
    try:
        result = subprocess.run(local_command(), input=sql, text=True, capture_output=True, timeout=180)
    except (subprocess.TimeoutExpired, OSError):
        raise ValueError('Database outcome uncertain. DO NOT RETRY APPLY. Reconcile using verify.') from None
    if result.returncode:
        raise ValueError('Database failed/uncertain. DO NOT RETRY APPLY. Run verify; inspect locally.')
    try:
        receipts = [json.loads(line) for line in result.stdout.splitlines() if line.startswith('{')]
        if len(receipts) != 1:
            raise ValueError('Missing/ambiguous evidence')
        evidence = receipts[0]
        if (evidence['release_sha256'] != release['release_sha256'] or
                evidence['source_hashes'] != release['source_hashes'] or
                evidence['readback'] != {'base': 1, 'insumos': len(release['insumos']),
                                         'plantillas': len(release['plantillas'])} or
                evidence['before_preservation'] != evidence['after_preservation']):
            raise ValueError('Invalid readback evidence')
    except (ValueError, KeyError, TypeError):
        raise ValueError('Outcome uncertain: invalid receipt. DO NOT RETRY APPLY; verify.') from None
    return evidence


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('prepare', 'dry-run', 'apply', 'verify', 'sql'))
    parser.add_argument('--source', type=Path, default=SOURCE)
    parser.add_argument('--release', type=Path, default=RELEASE)
    parser.add_argument('--prefix', default='DV1')
    parser.add_argument('--base-name', default='Daule V1')
    parser.add_argument('--confirm-local-apply', action='store_true')
    args = parser.parse_args()
    if args.action == 'prepare':
        release = prepare(args.source, args.release, args.prefix, args.base_name)
        print(canonical({'release_sha256': release['release_sha256'], 'insumos': len(release['insumos']),
                         'plantillas': len(release['plantillas'])}))
        return
    release = json.loads(args.release.read_text())
    validate_release(release, transform(read_tables(args.source), args.prefix, args.base_name), source_hashes(args.source))
    if args.action == 'apply' and not args.confirm_local_apply:
        parser.error('apply requires --confirm-local-apply AFTER independent verification')
    sql = render_sql(release, apply=args.action == 'apply', verify=args.action == 'verify')
    if args.action == 'sql':
        print(sql)
        return
    try:
        evidence = database_evidence(sql, release)
        receipt = {'action': args.action, 'release_sha256': release['release_sha256'], 'evidence': evidence}
        if args.action == 'apply':
            receipt['after_commit'] = database_evidence(render_sql(release, verify=True), release)
    except ValueError as error:
        raise SystemExit(str(error)) from None
    # Receipt failure after COMMIT is also reconciled by verify, never apply retry.
    path = args.release.parent / ('receipt-' + args.action + '-' + str(time.time_ns()) + '.json')
    durable_create(path, receipt)
    print(canonical(receipt))


if __name__ == '__main__':
    main()
