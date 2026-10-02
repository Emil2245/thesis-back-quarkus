"""Behavior tests: no database and no filesystem writes."""
import copy
import csv
import json
from unittest.mock import patch
import subprocess
import unittest
import uuid
from pathlib import Path
import operator_daule as op

SOURCE = Path(__file__).resolve().parent / 'sources/dataset_maestro_daule_v1'


class RelocationTests(unittest.TestCase):
    def test_default_source_is_backend_local(self):
        expected = Path(__file__).resolve().parent / 'sources/dataset_maestro_daule_v1'
        self.assertEqual(expected, op.SOURCE)
        self.assertEqual(expected, SOURCE)
        self.assertTrue(expected.is_dir())

    def test_checked_in_release_preserves_payload_and_identities(self):
        release = json.loads(op.RELEASE.read_text())
        invariant = {k: v for k, v in release.items()
                     if k not in ('operator_hashes', 'release_sha256')}
        self.assertEqual('5ee1e2760c6e94b5dbc3d06a2ef15df1ac6ee793e620abbb288d77c8f1f70176',
                         op.digest(invariant))
        payload = op.transform(op.read_tables(op.SOURCE), 'DV1', 'Daule V1')
        hashes = op.source_hashes(op.SOURCE)
        self.assertEqual(release, op.validate_release(release, payload, hashes))
        self.assertEqual(hashes, release['source_hashes'])
        # In-memory artifacts only: production release and source stay untouched.
        for field in ('source_hashes', 'operator_hashes'):
            damaged = copy.deepcopy(release)
            damaged[field][next(iter(damaged[field]))] = 'tampered'
            damaged['release_sha256'] = op.digest(
                {k: v for k, v in damaged.items() if k != 'release_sha256'})
            with self.subTest(field=field), self.assertRaises(ValueError):
                op.validate_release(damaged, payload, hashes)
        damaged = copy.deepcopy(release)
        damaged['base']['public_id'] = str(uuid.uuid4())
        damaged['release_sha256'] = op.digest(
            {k: v for k, v in damaged.items() if k != 'release_sha256'})
        with self.assertRaises(ValueError):
            op.validate_release(damaged, payload, hashes)
        damaged = copy.deepcopy(release)
        damaged['release_sha256'] = 'tampered'
        with self.assertRaises(ValueError):
            op.validate_release(damaged, payload, hashes)


class TransformTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tables = op.read_tables(SOURCE)
        cls.payload = op.transform(cls.tables, 'DV1', 'Daule V1')

    def test_real_counts_and_original_prices(self):
        self.assertEqual(1372, len(self.payload['insumos']))
        self.assertEqual(937, len(self.payload['plantillas']))
        multi = 0
        for row in self.payload['insumos']:
            candidates = [p for p in self.tables['precios_observados_v1']
                          if p['insumo_v1_id'] == row['source_id'] and p['precio_unitario']]
            multi += len(candidates) > 1
            chosen = min(candidates, key=op.price_key)
            self.assertEqual(chosen['precio_unitario'], row['precio_unitario'])
            self.assertEqual(chosen['precio_observado_id'], row['observation_id'])
        self.assertEqual(38, multi)

    def test_snapshot_whitelist(self):
        hm = 0
        for template in self.payload['plantillas']:
            sections = template['snapshot_secciones']['secciones']
            self.assertEqual(list(op.TYPES), [s['tipo'] for s in sections])
            for section in sections:
                for line in section['lineas']:
                    if 'esHerramientaMenor' in line:
                        self.assertEqual({'esHerramientaMenor': True}, line)
                        hm += 1
                    else:
                        expected = {'insumoCodigo', 'cantidad'}
                        if section['tipo'] in op.TYPES[:2]:
                            expected.add('rendimiento')
                        self.assertEqual(expected, set(line))
        self.assertEqual(937, hm)
        exceptions = [c for c in self.tables['apu_componentes_v1']
                      if c['porcentaje_herramienta_menor'] == '0.010000']
        self.assertEqual(2, len(exceptions))

    def test_price_order_not_median(self):
        rows = [{'source_id': s, 'dataset_origen': 'X', 'precio_observado_id': s,
                 'precio_unitario': p} for s, p in [('A', '9'), ('B', '2'), ('C', '3')]]
        self.assertEqual('9', min(rows, key=op.price_key)['precio_unitario'])

    def test_invalid_sources(self):
        mutations = [
            ('insumos_v1', 'tipo', 'BAD'),
            ('insumos_v1', 'unidad', 'too-long-unit'),
            ('apu_componentes_v1', 'insumo_v1_id', 'missing'),
            ('precios_observados_v1', 'precio_unitario', 'NaN'),
            ('apu_componentes_v1', 'cantidad', '-1'),
        ]
        for table, field, value in mutations:
            with self.subTest(table=table, field=field):
                tables = copy.deepcopy(self.tables)
                index = next(i for i, r in enumerate(tables[table])
                             if r.get('es_herramienta_menor', '0') == '0')
                tables[table][index][field] = value
                with self.assertRaises(ValueError):
                    op.transform(tables, 'DV1', 'Daule V1')
        tables = copy.deepcopy(self.tables)
        tables['insumos_v1'].append(tables['insumos_v1'][0])
        with self.assertRaises(ValueError):
            op.transform(tables, 'DV1', 'Daule V1')

    def test_uuid_and_reprepare_validation(self):
        prepared = op.assign_ids(self.payload, {'a': 'hash'})
        ids = [prepared['base']['public_id']] + [r['public_id'] for r in
              prepared['insumos'] + prepared['plantillas']]
        self.assertEqual(len(ids), len(set(ids)))
        for value in ids:
            self.assertEqual(7, uuid.UUID(value).version)
            self.assertEqual(uuid.RFC_4122, uuid.UUID(value).variant)
        self.assertEqual(prepared, op.validate_release(prepared, self.payload, {'a': 'hash'}))
        with self.assertRaises(ValueError):
            op.validate_release(prepared, self.payload, {'a': 'changed'})
        damaged = copy.deepcopy(prepared)
        damaged['plantillas'][0]['unidad'] = 'bad'
        with self.assertRaises(ValueError):
            op.validate_release(damaged, self.payload, {'a': 'hash'})

    def test_distinct_original_codes_and_export_order(self):
        with (SOURCE / 'insumo_aliases.csv').open(encoding='utf-8-sig', newline='') as stream:
            aliases = list(csv.DictReader(stream))
        mapped = {r['source_id']: r['codigo'] for r in self.payload['insumos']}
        for code in ('E53', 'E54'):
            identities = {r['insumo_v1_id'] for r in aliases if r['codigo_original'] == code}
            self.assertGreaterEqual(len(identities), 2)
            self.assertEqual(len(identities), len({mapped[i] for i in identities}))
        for template in self.payload['plantillas']:
            for section in template['snapshot_secciones']['secciones']:
                source = [r for r in self.tables['apu_componentes_v1']
                          if r['apu_v1_id'] == template['source_id'] and r['tipo_seccion'] == section['tipo']]
                expected = [mapped[r['insumo_v1_id']] for r in source if r['es_herramienta_menor'] == '0']
                actual = [r['insumoCodigo'] for r in section['lineas'] if 'insumoCodigo' in r]
                self.assertEqual(expected, actual)

    def test_hash_package_excludes_notes(self):
        self.assertEqual(15, len(op.source_hashes(SOURCE)))
        self.assertNotIn('NOTAS_INTEGRACION_BACKEND.md', op.PACKAGE)

    def test_same_preparation_keeps_full_identity_map(self):
        release = op.assign_ids(self.payload, op.source_hashes(SOURCE))
        with patch.object(Path, 'exists', return_value=True), \
                patch.object(Path, 'read_text', return_value=json.dumps(release)):
            # operator hashes use read_bytes, not read_text.
            self.assertEqual(release, op.prepare(SOURCE, Path('release.json'), 'DV1', 'Daule V1'))

    def test_uncertain_outcome_never_retries(self):
        release = op.assign_ids(self.payload, {})
        with patch.object(op.subprocess, 'run', side_effect=subprocess.TimeoutExpired('psql', 180)) as run:
            with self.assertRaisesRegex(ValueError, 'DO NOT RETRY APPLY'):
                op.database_evidence('sql', release)
            self.assertEqual(1, run.call_count)
        with patch.object(op.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0, '', '')):
            with self.assertRaisesRegex(ValueError, 'DO NOT RETRY APPLY'):
                op.database_evidence('sql', release)

    def test_sql_safety_and_local_command(self):
        release = op.assign_ids(self.payload, {})
        sql = op.render_sql(release)
        self.assertTrue(sql.rstrip().endswith('ROLLBACK;'))
        self.assertIn('pg_advisory_xact_lock', sql)
        self.assertIn('LOCK TABLE base_insumos, insumo, plantilla_apu', sql)
        self.assertIn('COPY stage_payload FROM STDIN', sql)
        self.assertNotIn('ON CONFLICT', sql)
        self.assertNotIn('setval', sql)
        self.assertIn('Partial prepared set: STOP', sql)
        self.assertIn("b.tipo='CENTRAL'", sql)
        self.assertIn('Existing payload/ownership/archival mismatch', sql)
        self.assertIn('Full readback mismatch', sql)
        self.assertIn("SET LOCAL lock_timeout = '5s'", sql)
        self.assertNotIn('INSERT INTO', op.render_sql(release, verify=True))
        self.assertTrue(op.render_sql(release, apply=True).rstrip().endswith('COMMIT;'))
        self.assertIn('ON_ERROR_STOP=1', op.local_command())
        self.assertIn('-X', op.local_command())
        self.assertEqual('propuestas', op.local_command()[-1])


if __name__ == '__main__':
    unittest.main()
