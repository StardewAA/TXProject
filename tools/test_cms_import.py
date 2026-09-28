import unittest
from prepare_cms_laws import split_articles, number
from import_cms_laws import stable_id, normalize, build_records
from pathlib import Path

class ImportValidationTest(unittest.TestCase):
    def test_sequence_rejects_missing_duplicate_and_out_of_order(self):
        for text in ('第一条 内容\n第三条 内容', '第一条 内容\n第一条 重复', '第二条 内容\n第一条 内容'):
            with self.subTest(text=text), self.assertRaises(ValueError):
                split_articles(text,2)

    def test_whitespace_and_paragraphs(self):
        result=split_articles('第一 条内容\n续段\n第二章 总则\n第二条 内容',2)
        self.assertEqual(result[0]['text'],'第一 条内容\n续段')
        self.assertEqual(result[1]['number'],2)
        self.assertEqual(number('一千二百四十二'),1242)

    def test_tenant_scoped_safe_ids(self):
        first=stable_id('cms-import:2005:air-2018:1')
        self.assertEqual(first,stable_id('cms-import:2005:air-2018:1'))
        self.assertNotEqual(first,stable_id('cms-import:1:air-2018:1'))
        self.assertLess(first,2**53)
        self.assertEqual(normalize('ＧＢ／Ｔ 23257—2017'),'GB/T232572017')

if __name__=='__main__':
    unittest.main()
