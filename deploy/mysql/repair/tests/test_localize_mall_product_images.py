"""Offline tests by default; opt into MySQL with MALL_IMAGE_TEST_MYSQL_CONTAINER.

The MySQL tests create a unique, disposable database, never use business tables,
and never call the file-upload service. Supply MYSQL_PWD through the environment.
"""
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import re
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import uuid

SCRIPT = Path(__file__).resolve().parents[1] / 'localize-mall-product-images.py'
SPEC = importlib.util.spec_from_file_location('localize_mall_product_images', SCRIPT)
localize = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(localize)
OLD = 'https://img.alicdn.com/test.jpg'
NEW = 'http://localhost:48080/app-api/infra/file/1/get/test.jpg'


def change(table='product_spu', row_id=910001, field='pic_url', before=OLD, after=NEW):
    return dict(table=table, id=row_id, field=field, before=before, after=after)


def product():
    return dict(id=910001, pic_url=OLD, slider_pic_urls=json.dumps([OLD]),
                description='<p>保温材料</p><img src="' + OLD + '">')


class OfflineTests(unittest.TestCase):
    def test_mysql_disables_continue_on_error_and_reconnect(self):
        cfg = SimpleNamespace(container='test-container', database='test-database')
        with patch.object(localize, 'args', cfg, create=True), patch.object(localize.subprocess, 'run') as run:
            run.return_value = SimpleNamespace(returncode=0, stdout=b'OK', stderr=b'')
            self.assertEqual(localize.mysql('SELECT 1;'), 'OK')
            command = run.call_args.args[0]
            self.assertIn('--skip-force', command)
            self.assertIn('--skip-reconnect', command)

    def test_normal_result_is_required(self):
        with patch.object(localize, 'mysql', return_value=localize.COMMITTED):
            localize.apply_guarded_sql('test')
        with patch.object(localize, 'mysql', return_value=localize.ROLLED_BACK):
            with self.assertRaisesRegex(RuntimeError, 'No image fields.*committed'):
                localize.apply_guarded_sql('test')
        with patch.object(localize, 'mysql', return_value=''):
            with self.assertRaisesRegex(RuntimeError, 'Unexpected transaction result'):
                localize.apply_guarded_sql('test')

    def test_sql_error_is_not_reported_as_success(self):
        with patch.object(localize, 'mysql', side_effect=RuntimeError('connection lost')):
            with self.assertRaisesRegex(RuntimeError, 'connection lost'):
                localize.apply_guarded_sql('test')

    def test_checks_run_before_conditional_commit(self):
        sql = localize.guarded_sql([change()])
        self.assertNotIn('\nCOMMIT;', sql)
        self.assertLess(sql.index('ROW_COUNT()'), sql.index('PREPARE mall_images_finish'))
        self.assertLess(sql.index('SELECT COUNT(*) FROM product_spu'), sql.index('PREPARE mall_images_finish'))
        self.assertIn("IF(@mall_images_ok=1, 'COMMIT', 'DO 0')", sql)
        self.assertIn('ROLLBACK;', sql)
        self.assertIn("engine='InnoDB'", sql)
        self.assertIn('CAST(pic_url AS BINARY)', sql)

    def test_restore_has_the_same_atomic_guards(self):
        sql = localize.guarded_sql([change()], restore=True)
        self.assertIn('SET pic_url=' + localize.sql_value(OLD), sql)
        self.assertIn('<=> CAST(' + localize.sql_value(NEW) + ' AS BINARY)', sql)
        self.assertIn('tenant_id=1 AND deleted=0', sql)
        self.assertIn('ROW_COUNT() = 1', sql)

    def test_rejects_unsupported_fields_and_duplicate_changes(self):
        invalid = [change(table='wms_inventory'), change(field='price'), change(row_id=-1),
                   change(row_id=True), change(before=NEW)]
        for item in invalid:
            with self.subTest(item=item), self.assertRaises(ValueError):
                localize.guarded_sql([item])
        with self.assertRaises(ValueError):
            localize.guarded_sql([change(), change()])

    def test_lock_order_is_deterministic(self):
        items = [change(table='trade_order_item', row_id=5), change(), change(field='description')]
        self.assertEqual(localize.guarded_sql(items), localize.guarded_sql(list(reversed(items))))

    def test_dry_run_has_no_upload_or_database_write(self):
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp)
            (source / '910001.jpg').touch()
            cfg = SimpleNamespace(source=source, output=source / 'unused', apply=False)
            with patch.object(localize, 'args', cfg, create=True), patch.object(localize, 'rows', return_value=[product()]), \
                    patch.object(localize, 'mysql') as mysql, patch.object(localize, 'upload') as upload, contextlib.redirect_stdout(io.StringIO()):
                localize.main()
                mysql.assert_not_called()
                upload.assert_not_called()
                self.assertFalse(cfg.output.exists())

    def test_missing_local_image_fails_before_upload(self):
        with tempfile.TemporaryDirectory() as temp:
            cfg = SimpleNamespace(source=Path(temp), output=Path(temp) / 'unused', apply=True)
            with patch.object(localize, 'args', cfg, create=True), patch.object(localize, 'rows', return_value=[product()]), \
                    patch.object(localize, 'upload') as upload:
                with self.assertRaises(FileNotFoundError):
                    localize.main()
                upload.assert_not_called()

    def test_backup_and_guarded_restore_exist_before_apply(self):
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp)
            (source / '910001.jpg').touch()
            cfg = SimpleNamespace(source=source, output=source / 'backup', apply=True)
            def execute(sql):
                self.assertTrue((cfg.output / 'image-fields-before.json').is_file())
                self.assertEqual((cfg.output / 'apply.sql').read_text(encoding='utf8'), sql)
                self.assertIn('ROW_COUNT()', (cfg.output / 'restore.sql').read_text(encoding='utf8'))
            with patch.object(localize, 'args', cfg, create=True), patch.object(localize, 'rows', return_value=[product()]), \
                    patch.object(localize, 'mysql', return_value=''), patch.object(localize, 'upload', return_value=NEW), \
                    patch.object(localize, 'apply_guarded_sql', side_effect=execute) as apply, contextlib.redirect_stdout(io.StringIO()):
                localize.main()
                apply.assert_called_once()


MYSQL_CONTAINER = os.environ.get('MALL_IMAGE_TEST_MYSQL_CONTAINER')


@unittest.skipUnless(MYSQL_CONTAINER, 'Opt-in: isolated MySQL regression database')
class MySQLTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not os.environ.get('MYSQL_PWD'):
            raise RuntimeError('MYSQL_PWD must be supplied for MySQL tests')
        cls.database = 'mall_image_guard_test_' + uuid.uuid4().hex
        cls.created = False
        cls.args_patch = patch.object(localize, 'args', SimpleNamespace(container=MYSQL_CONTAINER, database='information_schema'), create=True)
        cls.args_patch.start()
        cls.addClassCleanup(cls.args_patch.stop)
        localize.mysql(f'CREATE DATABASE `{cls.database}` CHARACTER SET utf8mb4;')
        cls.created = True
        cls.addClassCleanup(cls.drop_database)
        localize.args.database = cls.database
        localize.mysql('\n'.join(
            f'CREATE TABLE {table} (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL DEFAULT 1, '
            'deleted INT NOT NULL DEFAULT 0, spu_id BIGINT, pic_url VARCHAR(2000), '
            'slider_pic_urls TEXT, description TEXT) ENGINE=InnoDB;'
            for table in localize.IMAGE_FIELDS))

    @classmethod
    def drop_database(cls):
        # This exact UUID database was created above. Never accept a user-selected
        # database or wildcard as a destructive test-cleanup target.
        if not cls.created or not re.fullmatch(r'mall_image_guard_test_[0-9a-f]{32}', cls.database):
            raise RuntimeError('Refusing unsafe test database cleanup')
        localize.args.database = 'information_schema'
        localize.mysql(f'DROP DATABASE `{cls.database}`;')

    def setUp(self):
        self.assertRegex(localize.args.database, r'^mall_image_guard_test_[0-9a-f]{32}$')
        localize.mysql('\n'.join(f'DELETE FROM {table};' for table in localize.IMAGE_FIELDS))
        p = product()
        localize.mysql('INSERT INTO product_spu (id,pic_url,slider_pic_urls,description) VALUES '
                       f"(910001,{localize.sql_value(p['pic_url'])},{localize.sql_value(p['slider_pic_urls'])},{localize.sql_value(p['description'])});")
        for table in ['product_sku', 'trade_order_item', 'trade_after_sale']:
            localize.mysql(f'INSERT INTO {table} (id,spu_id,pic_url) VALUES (101,910001,{localize.sql_value(OLD)});')

    def changes(self):
        p = product()
        return [change(field=f, before=p[f], after=p[f].replace(OLD, NEW)) for f in ['pic_url','slider_pic_urls','description']] + [
            change(table=t, row_id=101) for t in ['product_sku','trade_order_item','trade_after_sale']]

    def read_product(self):
        return localize.rows('product_spu', ['pic_url','slider_pic_urls','description'], 'id=910001')[0]

    def test_normal_update_and_guarded_restore(self):
        changes = self.changes()
        localize.apply_guarded_sql(localize.guarded_sql(changes))
        self.assertTrue(all(OLD not in v for k,v in self.read_product().items() if k != 'id'))
        localize.apply_guarded_sql(localize.guarded_sql(changes, restore=True))
        self.assertEqual(self.read_product(), product())

    def test_description_conflict_rolls_back_every_table_then_retry_succeeds(self):
        changes = self.changes()
        edited = product()['description'] + '<p>管理员新增说明</p>'
        # A separate connection commits an edit after the initial snapshot.
        localize.mysql('UPDATE product_spu SET description=' + localize.sql_value(edited) + ' WHERE id=910001;')
        with self.assertRaisesRegex(RuntimeError, 'rolled back'):
            localize.apply_guarded_sql(localize.guarded_sql(changes))
        current = self.read_product()
        self.assertEqual(current['pic_url'], OLD)
        self.assertEqual(current['description'], edited)
        for table in ['product_sku','trade_order_item','trade_after_sale']:
            self.assertEqual(localize.rows(table, ['pic_url'], 'id=101')[0]['pic_url'], OLD)
        # Re-read as main() does: original cover is intact, so it remains eligible.
        retry = [change(field=f, before=current[f], after=current[f].replace(OLD, NEW)) for f in ['pic_url','slider_pic_urls','description']]
        localize.apply_guarded_sql(localize.guarded_sql(retry))
        self.assertIn('管理员新增说明', self.read_product()['description'])
        self.assertNotIn(OLD, self.read_product()['description'])

    def test_late_history_conflict_rolls_back_prior_product_updates(self):
        localize.mysql('UPDATE trade_order_item SET pic_url=' + localize.sql_value('https://example.com/manual.jpg') + ' WHERE id=101;')
        with self.assertRaisesRegex(RuntimeError, 'rolled back'):
            localize.apply_guarded_sql(localize.guarded_sql(self.changes()))
        self.assertEqual(self.read_product(), product())

    def test_main_conflict_retry_and_completed_noop(self):
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp)
            (source / '910001.jpg').touch()
            cfg = SimpleNamespace(**vars(localize.args), source=source,
                                  output=source / 'attempt-1', apply=True)
            def concurrent_upload(_):
                edited = product()['description'] + '<p>管理员新增说明</p>'
                localize.mysql('UPDATE product_spu SET description=' + localize.sql_value(edited) + ' WHERE id=910001;')
                return NEW
            with patch.object(localize, 'args', cfg), patch.object(localize, 'upload', side_effect=concurrent_upload) as upload, \
                    contextlib.redirect_stdout(io.StringIO()):
                with self.assertRaisesRegex(RuntimeError, 'rolled back'):
                    localize.main()
                self.assertEqual(self.read_product()['pic_url'], OLD)
                self.assertTrue((cfg.output / 'image-fields-before.json').is_file())
                upload.side_effect = None
                upload.return_value = NEW
                cfg.output = source / 'attempt-2'
                localize.main()
                current = self.read_product()
                self.assertEqual(current['pic_url'], NEW)
                self.assertNotIn(OLD, current['description'])
                self.assertIn('管理员新增说明', current['description'])
                for table in ['product_sku','trade_order_item','trade_after_sale']:
                    self.assertEqual(localize.rows(table, ['pic_url'], 'id=101')[0]['pic_url'], NEW)
                cfg.output = source / 'attempt-3'
                localize.main()
                self.assertEqual(upload.call_count, 2)
                self.assertFalse(cfg.output.exists())

    def test_silent_truncation_is_verified_before_commit(self):
        changes = [change(after='https://example.com/' + 'x' * 2100)]
        with self.assertRaisesRegex(RuntimeError, 'rolled back'):
            localize.apply_guarded_sql("SET SESSION sql_mode='';\n" + localize.guarded_sql(changes))
        self.assertEqual(self.read_product(), product())

    def test_deleted_foreign_tenant_and_missing_rows_cannot_partially_commit(self):
        for mutation in ['UPDATE trade_order_item SET deleted=1 WHERE id=101;',
                         'UPDATE trade_order_item SET tenant_id=2 WHERE id=101;',
                         'DELETE FROM trade_order_item WHERE id=101;']:
            with self.subTest(mutation=mutation):
                localize.mysql('DELETE FROM trade_order_item WHERE id=101; '
                               'INSERT INTO trade_order_item (id,spu_id,pic_url) VALUES '
                               f'(101,910001,{localize.sql_value(OLD)});')
                localize.mysql(mutation)
                with self.assertRaisesRegex(RuntimeError, 'rolled back'):
                    localize.apply_guarded_sql(localize.guarded_sql(self.changes()))
                self.assertEqual(self.read_product(), product())

    def test_trailing_space_and_case_changes_are_detected(self):
        for value in [OLD + ' ', OLD.upper()]:
            with self.subTest(value=value):
                localize.mysql('UPDATE product_spu SET pic_url=' + localize.sql_value(value) + ' WHERE id=910001;')
                with self.assertRaisesRegex(RuntimeError, 'rolled back'):
                    localize.apply_guarded_sql(localize.guarded_sql(self.changes()))
                self.assertEqual(self.read_product()['pic_url'], value)

    def test_restore_conflict_does_not_partially_restore(self):
        changes = self.changes()
        localize.apply_guarded_sql(localize.guarded_sql(changes))
        localize.mysql('UPDATE trade_order_item SET pic_url=' + localize.sql_value('manual') + ' WHERE id=101;')
        with self.assertRaisesRegex(RuntimeError, 'rolled back'):
            localize.apply_guarded_sql(localize.guarded_sql(changes, restore=True))
        self.assertEqual(self.read_product()['pic_url'], NEW)

    def test_duplicate_apply_is_rejected_without_further_changes(self):
        sql = localize.guarded_sql(self.changes())
        localize.apply_guarded_sql(sql)
        before = self.read_product()
        with self.assertRaisesRegex(RuntimeError, 'rolled back'):
            localize.apply_guarded_sql(sql)
        self.assertEqual(self.read_product(), before)

    def test_nontransactional_table_refuses_entire_batch(self):
        localize.mysql('ALTER TABLE trade_order_item ENGINE=MyISAM;')
        try:
            with self.assertRaisesRegex(RuntimeError, 'rolled back'):
                localize.apply_guarded_sql(localize.guarded_sql(self.changes()))
            self.assertEqual(self.read_product(), product())
            self.assertEqual(localize.rows('trade_order_item', ['pic_url'], 'id=101')[0]['pic_url'], OLD)
        finally:
            localize.mysql('ALTER TABLE trade_order_item ENGINE=InnoDB;')

    def test_sql_error_before_commit_rolls_back_all_writes(self):
        sql = localize.guarded_sql(self.changes())
        sql = sql.replace('PREPARE mall_images_finish', 'SELECT missing_column FROM product_spu;\nPREPARE mall_images_finish', 1)
        with self.assertRaises(RuntimeError):
            localize.apply_guarded_sql(sql)
        self.assertEqual(self.read_product(), product())


if __name__ == '__main__':
    unittest.main()
