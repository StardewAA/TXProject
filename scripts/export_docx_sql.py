# -*- coding: utf-8 -*-
"""
从 docs/training-materials 的 docx 素材解析课程 + 题库 + 专项试卷，
生成可重复执行的 INSERT IGNORE SQL（先 DELETE 同批种子再插入，幂等）。
输出: sql/2026-09-21-training-docx-content.sql
"""
import docx, os, re, glob, json

BASE = r'D:\txgy-new\docs\training-materials'
COURSE_DIR = os.path.join(BASE, 'course-docs')
BANK_DIR = os.path.join(BASE, 'question-bank')
OUT = r'D:\txgy-new\sql\2026-09-21-training-docx-content.sql'

SEED_TAG = 'seed:docx-20260921'
DIFF = {'易': 1, '中': 2, '难': 3}
QTYPE = {'单选题': 1, '多选题': 2, '判断题': 3, '实操问答题': 4, '案例分析题': 5}
LVL = {'入门段': 1, '核心段': 2, '进阶段': 2, '技师段': 3}

def paras(path):
    d = docx.Document(path)
    return [p.text.strip() for p in d.paragraphs if p.text.strip()]

def esc(s):
    if s is None:
        return 'NULL'
    return "'" + s.replace('\\', '\\\\').replace("'", "''").replace('\n', '\\n') + "'"

def parse_bank(path):
    """解析分课题库 docx -> (meta, questions)"""
    lines = paras(path)
    meta = {}
    m = re.search(r'课程编码：([A-Za-z0-9\-]+)', lines[1] if len(lines) > 1 else '')
    if m:
        meta['code'] = m.group(1)
    m = re.search(r'(入门段|核心段|进阶段|技师段)', lines[1] if len(lines) > 1 else '')
    if m:
        meta['level'] = LVL[m.group(1)]
    qs = []
    section = None
    i = 0
    while i < len(lines):
        line = lines[i]
        sm = re.match(r'^[一二三四五六]、(.+?)（共', line)
        if sm:
            section = sm.group(1)
            i += 1
            continue
        qm = re.match(r'^(\d+)\.（难度：([易中难])）(.*)$', line)
        if qm and section:
            qtype = QTYPE.get(section)
            if qtype is None:
                i += 1
                continue
            q = {'qtype': qtype, 'stem': qm.group(3), 'difficulty': DIFF[qm.group(2)],
                 'options': [], 'answer': None, 'analysis': None}
            i += 1
            while i < len(lines):
                nxt = lines[i]
                if re.match(r'^(\d+)\.（难度：', nxt) or re.match(r'^[一二三四五六]、', nxt):
                    break
                om = re.match(r'^([A-H])[\.\、]?\s*(.*)$', nxt)
                if om and qtype in (1, 2):
                    q['options'].append({'key': om.group(1), 'text': om.group(2)})
                elif nxt.startswith('答案：'):
                    q['answer'] = nxt[3:].strip()
                elif nxt.startswith('参考答案：'):
                    q['answer'] = nxt[5:].strip()
                elif nxt.startswith('解析：'):
                    q['analysis'] = nxt[3:].strip()
                i += 1
            qs.append(q)
            continue
        i += 1
    return meta, qs

def parse_paper(path):
    """解析专项试卷 docx -> questions（填空/简答）"""
    lines = paras(path)
    qs = []
    section = None
    i = 0
    while i < len(lines):
        line = lines[i]
        sm = re.match(r'^[一二三四五六]、(.+?)$', line)
        if sm:
            section = sm.group(1)
            i += 1
            continue
        qm = re.match(r'^(\d+)[、\.]\s*(.*)$', line)
        if qm and section:
            q = {'qtype': 6 if '填空' in section else 7,
                 'stem': qm.group(2), 'difficulty': 2,
                 'options': [], 'answer': None, 'analysis': None}
            i += 1
            ans = []
            while i < len(lines):
                nxt = lines[i]
                if re.match(r'^(\d+)[、\.]', nxt) or re.match(r'^[一二三四五六]、', nxt):
                    break
                if nxt.startswith('答：'):
                    ans.append(nxt[2:].strip())
                elif nxt and not re.match(r'^答[:：]', nxt):
                    ans.append(nxt)
                i += 1
            if ans:
                q['answer'] = '\n'.join(ans)
            qs.append(q)
            continue
        i += 1
    return qs

def parse_course(path, code, level):
    """解析课程 docx -> course dict"""
    lines = paras(path)
    title = os.path.basename(path)
    title = re.sub(r'^\d+_', '', title).replace('.docx', '')
    summary = None
    for j, l in enumerate(lines):
        if l == '课程目标' and j + 1 < len(lines):
            summary = lines[j + 1]
            break
    return {'title': title, 'code': code, 'level': level, 'summary': summary}

def main():
    bank_files = sorted(glob.glob(os.path.join(BANK_DIR, '*题库.docx')))
    course_files = sorted(glob.glob(os.path.join(COURSE_DIR, '*.docx')))
    paper_files = sorted([f for f in glob.glob(os.path.join(BANK_DIR, '*.docx')) if '题库' not in os.path.basename(f)])

    assert len(bank_files) == 12, f'题库数量异常: {len(bank_files)}'
    assert len(course_files) == 12, f'课程数量异常: {len(course_files)}'

    courses = []
    all_qs = []
    for i, bf in enumerate(bank_files):
        meta, qs = parse_bank(bf)
        course = parse_course(course_files[i], meta.get('code'), meta.get('level'))
        courses.append(course)
        for q in qs:
            q['course_code'] = course['code']
        all_qs.extend(qs)
    for pf in paper_files:
        qs = parse_paper(pf)
        for q in qs:
            q['course_code'] = 'PAPER-' + os.path.basename(pf)[:6]
        all_qs.extend(qs)

    # ---------- 生成 SQL ----------
    sql = []
    sql.append('-- =====================================================================')
    sql.append('-- 云培训学院 · 教材/题库基础数据（docx 素材导入）')
    sql.append('-- 来源: docs/training-materials/course-docs（12 门课）')
    sql.append('--        docs/training-materials/question-bank（12 题库 420 题 + 7 专项试卷）')
    sql.append('-- 题型: 1单选 2多选 3判断 4实操问答 5案例分析 6填空 7简答; 难度: 1易 2中 3难')
    sql.append('-- 幂等: 先 DELETE 同批种子(creator=seed:docx-20260921) 再 INSERT，可重复执行')
    sql.append('-- 执行: mysql -uroot -p --default-character-set=utf8mb4 ruoyi-vue-pro < 本文件')
    sql.append('-- =====================================================================')
    sql.append('')

    # 课程
    sql.append('DELETE FROM `training_course` WHERE `creator` = %s;' % esc(SEED_TAG))
    sql.append('INSERT INTO `training_course` (`root_id`,`version`,`is_current`,`code`,`title`,`subtitle`,`summary`,`level`,`credit`,`duration_minutes`,`industry`,`biz_tags`,`lecturer`,`media_type`,`media_count`,`comment_count`,`audit_count`,`status`,`sort`,`creator`,`updater`,`deleted`,`tenant_id`) VALUES')
    rows = []
    for j, c in enumerate(courses, 1):
        rows.append('  (NULL, 1, b\'1\', %s, %s, NULL, %s, %s, 2.00, 45, %s, %s, %s, 1, 1, 0, 0, 3, %d, %s, %s, b\'0\', 1)'
                    % (esc(c['code']), esc(c['title']), esc(c['summary']), c['level'] or 'NULL',
                       esc('防腐保温'), esc('["云培训学院"]'), esc('云培训学院'),
                       j, esc(SEED_TAG), esc(SEED_TAG)))
    sql.append(',\n'.join(rows) + ';')
    sql.append('')

    # 题目
    sql.append('DELETE FROM `training_question` WHERE `creator` = %s;' % esc(SEED_TAG))
    sql.append('INSERT INTO `training_question` (`qtype`,`stem`,`options_json`,`answer`,`analysis`,`difficulty`,`category_id`,`status`,`creator_id`,`creator_name`,`creator`,`deleted`,`tenant_id`) VALUES')
    rows = []
    for q in all_qs:
        opts = ''
        if q['options']:
            opts = json.dumps([{'key': o['key'], 'text': o['text']} for o in q['options']], ensure_ascii=False)
        rows.append('  (%d, %s, %s, %s, %s, %d, NULL, 1, NULL, %s, %s, b\'0\', 1)'
                    % (q['qtype'], esc(q['stem']), esc(opts), esc(q['answer']), esc(q['analysis']),
                       q['difficulty'], esc(SEED_TAG), esc(SEED_TAG)))
    sql.append(',\n'.join(rows) + ';')
    sql.append('')

    with open(OUT, 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(sql))

    # 统计
    print('课程:', len(courses))
    for c in courses:
        print('  ', c['code'], c['title'], 'level=', c['level'])
    from collections import Counter
    cnt = Counter(q['qtype'] for q in all_qs)
    print('题目总数:', len(all_qs))
    for k in sorted(cnt):
        print('  qtype=%d: %d' % (k, cnt[k]))
    print('输出:', OUT)

if __name__ == '__main__':
    main()
