/**
 * 三级编号（项目 / 招标项目 / 标段）口径与文书落点测试。
 *
 * 为什么必须有这组测试：这三种编号是**关注与投标流程跟踪的匹配键**
 * （notice-follow targetType = PROJECT / TENDER_PROJECT / SECTION），
 * 一旦取值口径或文书落点回归（编号丢失、错位、印两遍），症状是"发布成功但没人被通知"或"文书对不上编号"，
 * 线上极难发现。故把口径（noticeCodes.ts）与落点（noticeSections.ts / noticeHtmlConverter.ts）一起钉死。
 */
import { describe, expect, it } from 'vitest';

import {
  buildCodeItems,
  buildTrackTiers,
  missingTierHint,
  sectionCodesOf,
  trackSummary,
} from './noticeCodes';
import { convertNoticeToHtml } from './noticeHtmlConverter';
import { buildNoticeSections } from './noticeSections';

/** 真实形态：项目 I…h → 招标项目 I…h001 → 标段 I…h001001 / I…h001002 */
const ROOT = { projectCode: 'I610100202600000h', projectName: '某炼化项目' };
const TENDER = 'I610100202600000h001';
const SEC1 = 'I610100202600000h001001';
const SEC2 = 'I610100202600000h001002';
const PAYLOAD: Record<string, unknown> = {
  TENDER_PROJECT_CODE: TENDER,
  BID_SECTION_CODES: `${SEC1};${SEC2}`,
  NOTICE_NAME: '某项目施工招标公告',
  NOMINAL_FEE: '500',
};

describe('noticeCodes · 取值口径', () => {
  it('标段编号兼容 半角分号/中文分号/顿号/逗号/空白，并去重', () => {
    expect(sectionCodesOf({ BID_SECTION_CODES: `${SEC1};${SEC2}` })).toEqual([SEC1, SEC2]);
    expect(sectionCodesOf({ BID_SECTION_CODES: `${SEC1}；${SEC2}` })).toEqual([SEC1, SEC2]);
    expect(sectionCodesOf({ BID_SECTION_CODES: `${SEC1}、${SEC2}` })).toEqual([SEC1, SEC2]);
    expect(sectionCodesOf({ BID_SECTION_CODES: `${SEC1}, ${SEC2}` })).toEqual([SEC1, SEC2]);
    expect(sectionCodesOf({ BID_SECTION_CODES: `${SEC1};${SEC1}` })).toEqual([SEC1]);
  });

  it('标段编号单数键 BID_SECTION_CODE 兜底（中标结果公示数据集用单数）', () => {
    expect(sectionCodesOf({ BID_SECTION_CODE: SEC1 })).toEqual([SEC1]);
  });

  it('脏值当空：null/undefined/字符串 "null" 都不产生编号', () => {
    expect(sectionCodesOf({ BID_SECTION_CODES: 'null' })).toEqual([]);
    expect(sectionCodesOf({ BID_SECTION_CODES: '' })).toEqual([]);
    expect(sectionCodesOf(null)).toEqual([]);
    expect(buildTrackTiers({ payload: { TENDER_PROJECT_CODE: 'undefined' }, projectCode: null })).toEqual(
      expect.arrayContaining([expect.objectContaining({ level: 'TENDER_PROJECT', trackable: false })]),
    );
  });
});

describe('noticeCodes · 三级跟踪档位', () => {
  it('三级齐备：项目/招标项目各一档，标段**逐标段一档**（2026-10-02 修复合并键）', () => {
    const tiers = buildTrackTiers({ payload: PAYLOAD, projectCode: ROOT.projectCode });
    expect(tiers.map((t) => t.level)).toEqual(['PROJECT', 'TENDER_PROJECT', 'SECTION', 'SECTION']);
    expect(tiers.map((t) => t.levelLabel)).toEqual(['项目', '招标项目', '标段', '标段']);
    expect(tiers.every((t) => t.trackable)).toBe(true);
    expect(tiers[0]!.code).toBe(ROOT.projectCode);
    expect(tiers[1]!.code).toBe(TENDER);
    // 跟踪键必须完整编号（后端精确匹配）；展示短号 001/002
    expect(tiers[2]!.code).toBe(SEC1);
    expect(tiers[3]!.code).toBe(SEC2);
    expect(tiers[2]!.codeShort).toBe('001');
    expect(tiers[3]!.codeShort).toBe('002');
    expect(missingTierHint(tiers)).toBe('');
  });

  it('只发到某一级：缺档提示点名该级关注者收不到（不阻断发布，但要如实告知）', () => {
    const tiers = buildTrackTiers({ payload: { TENDER_PROJECT_CODE: TENDER }, projectCode: '' });
    expect(tiers.filter((t) => t.trackable).map((t) => t.level)).toEqual(['TENDER_PROJECT']);
    const hint = missingTierHint(tiers);
    expect(hint).toContain('缺 项目、标段 编号');
    expect(hint).toContain('收不到这条公告的动态');
  });

  it('全空：摘要明说没人能被通知', () => {
    const tiers = buildTrackTiers({ payload: {}, projectCode: undefined });
    // 空集仍保留一条不可跟踪的标段档位（缺档提示依赖它点名）；项目/招标项目各一档
    expect(tiers).toHaveLength(3);
    expect(tiers.some((t) => t.trackable)).toBe(false);
    expect(trackSummary(tiers)).toBe('未关联任何三级编号（发布后无人能被通知）');
    // 摘要用展示短号，不堆完整 23 位编号
    expect(trackSummary(buildTrackTiers({ payload: PAYLOAD, projectCode: ROOT.projectCode }))).toBe(
      `项目 ${ROOT.projectCode} · 招标项目 ${TENDER} · 标段 001 · 标段 002`,
    );
  });
});

describe('noticeCodes · 文书编号行', () => {
  it('编号行顺序：项目编号 → 项目名称 → 招标项目编号 → 标段（包）编号 → 标段（包）数量', () => {
    const items = buildCodeItems({ payload: PAYLOAD, ...ROOT });
    expect(items.map((i) => i.label)).toEqual([
      '项目编号',
      '项目名称',
      '招标项目编号',
      '标段（包）编号',
      '标段（包）数量',
    ]);
    expect(items[0]!.value).toBe(ROOT.projectCode);
    expect(items[3]!.value).toBe(`${SEC1}；${SEC2}`); // 显示用全角分号
    expect(items[4]!.value).toBe('2'); // 未填 NOTICE_BID_NUM 时按标段个数兜底
  });

  it('项目名称缺失时回退数据集 PROJECT_NAME；空值不占位', () => {
    const items = buildCodeItems({ payload: { ...PAYLOAD, PROJECT_NAME: '数据集里的项目名' }, projectCode: ROOT.projectCode });
    expect(items.find((i) => i.label === '项目名称')!.value).toBe('数据集里的项目名');
    const onlyProject = buildCodeItems({ payload: {}, projectCode: ROOT.projectCode });
    expect(onlyProject.map((i) => i.label)).toEqual(['项目编号']);
  });
});

describe('noticeSections · 编号块进首节且只印一次', () => {
  it('招标公告首节带出三级编号，且模板里的编号字段不再重复出现', () => {
    const sections = buildNoticeSections('TENDER_NOTICE', PAYLOAD, ROOT);
    expect(sections[0]!.title).toBe('一、项目与标段');
    expect(sections[0]!.items.slice(0, 5).map((i) => i.label)).toEqual([
      '项目编号',
      '项目名称',
      '招标项目编号',
      '标段（包）编号',
      '标段（包）数量',
    ]);
    // 全篇标签唯一性：招标项目编号 / 标段（包）编号 / 项目编号 各只出现一次（不因模板再印一遍）
    const allLabels = sections.flatMap((s) => s.items.map((i) => i.label));
    expect(allLabels.filter((l) => l === '招标项目编号')).toHaveLength(1);
    expect(allLabels.filter((l) => l === '相关标段（包）编号')).toHaveLength(0); // 旧模板标签被编号块取代
    expect(allLabels.filter((l) => l === '项目编号')).toHaveLength(1);
    // 非编号字段仍在各自分节里（编号块只接管编号，不吞别的字段）
    expect(sections.flatMap((s) => s.items).some((i) => i.value === '500')).toBe(true);
  });

  it('不传 root（历史调用点）时行为不变：编号仍由模板行带出，不出现项目编号行', () => {
    const sections = buildNoticeSections('TENDER_NOTICE', PAYLOAD);
    const labels = sections[0]!.items.map((i) => i.label);
    expect(labels).toContain('招标项目编号');
    expect(labels).not.toContain('项目编号');
  });

  it('未知类型 + 有编号：编号块单独成节（编号一定要进文书）', () => {
    const sections = buildNoticeSections('SOMETHING_ELSE', PAYLOAD, ROOT);
    expect(sections).toHaveLength(1);
    expect(sections[0]!.title).toBe('一、项目与编号');
    expect(sections[0]!.items.map((i) => i.label)).toContain('标段（包）编号');
  });

  it('中标结果公示（单数 BID_SECTION_CODE）也能进编号块', () => {
    const sections = buildNoticeSections('WIN_RESULT_PUBLICITY', { BID_SECTION_CODE: SEC1 }, ROOT);
    expect(sections[0]!.items.find((i) => i.label === '标段（包）编号')!.value).toBe(SEC1);
  });
});

describe('noticeHtmlConverter · 正文 HTML 印出三级编号', () => {
  it('三级编号各出现一次，且标题/正文仍在', () => {
    const html = convertNoticeToHtml(
      {
        title: '某项目施工招标公告',
        noticeType: 'TENDER_NOTICE',
        projectCode: ROOT.projectCode,
        projectName: ROOT.projectName,
        payload: PAYLOAD,
        content: '正文第一段。',
        internalFlag: true,
        publisherName: '某招标人',
      },
      { mode: 'print' },
    );
    expect(html).toContain('某项目施工招标公告');
    expect(html).toContain('正文第一段。');
    // 注意：项目编号是招标项目编号/标段编号的**前缀**（I…h ⊂ I…h001 ⊂ I…h001001），
    // 故按子串 split 会把嵌套算成多次；这里用"右侧不再是编码字符"的边界计数，才是真正的出现次数。
    const countWhole = (hay: string, code: string): number =>
      (hay.match(new RegExp(`${code.replaceAll(/[.*+?^${}()|[\]\\]/g, '\\$&')}(?![0-9A-Za-z])`, 'g')) ?? []).length;
    for (const code of [ROOT.projectCode, TENDER, SEC1, SEC2]) {
      expect(countWhole(html, code), `${code} 应恰好出现一次`).toBe(1);
    }
    expect(html).toContain('项目名称');
    expect(html).toContain(ROOT.projectName);
  });
});
