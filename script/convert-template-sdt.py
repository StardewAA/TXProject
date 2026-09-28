# -*- coding: utf-8 -*-
"""
把「空白下划线填空」式 Word 模板改造成 SDT 内容控件版，供标书 Agent 的 FILL 阶段扫描/回填。

原理：
- 识别段落里连续的「下划线 run」（<w:rPr><w:u/> + 空文本），视为一个填空处；
- 取填空处前/后连续的非下划线文本，归一到「标准字段名」（如「1、根据你方招标工程招标编号为」→「招标编号」、
  「元（RMB￥」→「投标报价小写」），未命中关键词时回退到清理后的原文或「字段_N」；
- 把这些下划线 run 包进 <w:sdt><w:sdtPr><w:tag w:val="字段名"/></w:sdtPr><w:sdtContent>…</w:sdtContent></w:sdt>。

用法：python convert-template-sdt.py <输入.docx> [输出.docx]
默认输出：<输入>-SDT版.docx
"""
import re
import sys
import zipfile
import shutil
import os
from lxml import etree

W = 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'


def q(tag):
    return f'{{{W}}}{tag}'


def is_underline(run):
    rpr = run.find(q('rPr'))
    if rpr is None:
        return False
    return rpr.find(q('u')) is not None


def run_text(run):
    return ''.join((t.text or '') for t in run.findall('.//' + q('t')))


def clean_label(label):
    label = (label or '').strip()
    # 去掉末尾的冒号/顿号/逗号/空格，以及包裹的括号里内容说明（如「（盖单位章）」）
    label = re.sub(r'[：:、，。,\s]+$', '', label)
    return label


# 标准字段名 → 命中关键词（按顺序匹配，先命中者优先；关键词尽量具体，避免误判）。
# 标准名与 services/bid-agent-service/src/lib/templateFields.ts 的字段字典保持一致，
# 这样 SDT 标签名才能被 FILL 阶段按 normalizeName 模糊匹配到企业事实，实现自动回填。
FIELD_RULES = [
    # 报价金额：大写/小写先于通用「报价」，避免「报价大写」被归到小写
    ('投标报价大写', ['大写']),
    ('投标报价小写', ['小写', 'RMB', '￥', '¥']),
    # 费用项（更具体的在前）
    ('安全文明施工措施费', ['安全文明施工措施费', '安全文明施工']),
    ('措施项目费', ['措施项目费']),
    # 证照 / 资质
    ('安全生产许可证编号', ['安全生产许可证']),
    ('营业执照编号', ['营业执照', '统一社会信用代码', '信用代码', '注册号']),
    # 企业信息
    ('注册资本', ['注册资本', '注册资金']),
    ('经营范围', ['经营范围', '业务范围', '主营范围']),
    ('经营期限', ['经营期限', '营业期限', '经营年限']),
    ('成立时间', ['成立时间', '成立日期', '成立年月']),
    ('单位性质', ['单位性质', '企业性质', '经济性质', '单位类型']),
    ('法定代表人', ['法定代表人', '法人代表']),
    ('项目经理', ['项目经理', '项目负责人', '建造师']),
    ('地址', ['注册地址', '通讯地址', '联系地址', '地址', '住所']),
    # 工期
    ('工期天数', ['日历天', '工期']),
    # 招标 / 项目标识
    ('招标编号', ['招标编号', '招标项目编号', '招标工程编号', '项目编号']),
    ('标段号', ['标段号', '标段']),
    ('工程名称', ['工程名称', '项目名称']),
    ('投标人名称', ['投标人名称', '企业名称', '公司名称', '单位名称', '投标人']),
    # 报价兜底（更通用的「报价/总价」）
    ('投标报价小写', ['投标总价', '投标报价', '总报价', '报价', '投标价', '中标价', '合同额']),
]

# 签字/盖章类字段：保留原词，交由 Java 侧 inferFillBy 判 manual，不做标准名归一（避免丢失人工填写信号）
SIGNATURE_WORDS = ('签字', '签名', '盖章', '签章', '印章', '印鉴', '签署', '单位章')


def _is_text_run(el):
    return el.tag == q('r') and not is_underline(el)


def leading_text(children, idx):
    """留白前连续的非下划线 run 文本（保持原顺序）"""
    parts = []
    k = idx - 1
    while k >= 0 and _is_text_run(children[k]):
        parts.append(run_text(children[k]))
        k -= 1
    parts.reverse()
    return ''.join(parts)


def trailing_text(children, after_idx):
    """留白后连续的非下划线 run 文本"""
    parts = []
    k = after_idx
    while k < len(children) and _is_text_run(children[k]):
        parts.append(run_text(children[k]))
        k += 1
    return ''.join(parts)


def fallback_name(label, tail, fallback_counter):
    name = label or tail
    if not name or len(name) > 25:
        return f'字段_{fallback_counter}'
    return name


def standard_field_name(label, tail, fallback_counter):
    """把留白前后的原始文本归一到标准字段名；无法命中时回退到清理后的标签/序号。"""
    label = clean_label(label)
    tail = clean_label(tail)
    text = label + tail
    if any(w in text for w in SIGNATURE_WORDS):
        return fallback_name(label, tail, fallback_counter)
    for std, keywords in FIELD_RULES:
        if any(kw in text for kw in keywords):
            return std
    return fallback_name(label, tail, fallback_counter)


def build_sdt(name):
    sdt = etree.Element(q('sdt'))
    sdt_pr = etree.SubElement(sdt, q('sdtPr'))
    tag = etree.SubElement(sdt_pr, q('tag'))
    tag.set(q('val'), name)
    content = etree.SubElement(sdt, q('sdtContent'))
    return sdt, content


def convert(src, dst):
    with zipfile.ZipFile(src) as z:
        root = etree.fromstring(z.read('word/document.xml'))

    field_counter = 0
    total_sdt = 0

    for p in root.iter(q('p')):
        children = list(p)
        # 逐子元素扫描，识别「连续下划线 run 组」
        idx = 0
        while idx < len(children):
            child = children[idx]
            if child.tag != q('r') or not is_underline(child):
                idx += 1
                continue
            # 收集连续下划线 run
            group = []
            j = idx
            while j < len(children) and children[j].tag == q('r') and is_underline(children[j]):
                group.append(children[j])
                j += 1
            # 留白前/后连续的非下划线文本，用作字段名归一化的上下文
            label = leading_text(children, idx)
            tail = trailing_text(children, j)
            field_counter += 1
            name = standard_field_name(label, tail, field_counter)
            # 用 sdt 替换这些 run：在原位置插入 sdt，再把原 run 移入 sdtContent
            sdt, content = build_sdt(name)
            p.insert(idx, sdt)
            for r in group:
                p.remove(r)
                content.append(r)
            total_sdt += 1
            # 重新取 children（结构已变）
            children = list(p)
            idx += 1  # 跳过刚插入的 sdt

    # 写回：重建 zip，替换 document.xml（避免追加模式产生重复条目）
    new_xml = etree.tostring(root, xml_declaration=True, encoding='UTF-8', standalone=True)
    tmp = dst + '.tmp'
    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(tmp, 'w', zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            data = new_xml if item.filename == 'word/document.xml' else zin.read(item.filename)
            zout.writestr(item, data)
    shutil.move(tmp, dst)

    print(f'转换完成：{dst}')
    print(f'识别填空处 {field_counter} 个，生成 SDT 内容控件 {total_sdt} 个')


if __name__ == '__main__':
    src = sys.argv[1] if len(sys.argv) > 1 else r'C:\Users\Administrator\Desktop\投标标书.docx'
    dst = sys.argv[2] if len(sys.argv) > 2 else os.path.splitext(src)[0] + '-SDT版.docx'
    convert(src, dst)
