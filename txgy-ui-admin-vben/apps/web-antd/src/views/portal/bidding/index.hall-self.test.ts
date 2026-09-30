/**
 * 公告大厅「本站」来源的界面行为测试（2026-09-23）。
 *
 * 数据口径已有单测（hallSelfSource.test.ts：合并/分页/筛选翻译）；这里只钉界面三件事：
 *   ① 本站行带「本站」标记、性质列显示平台认定的公告类型、点行去本站详情页（不是外站详情）；
 *   ② 外站行点行仍去外部公告详情；
 *   ③ 两种口径提示：带外部专属筛选时提示"当前筛选只作用于外部采集公告"；来源站=本站时说明本站口径。
 */
import { createApp, h, nextTick } from 'vue';
import { createRouter, createWebHashHistory } from 'vue-router';

import { beforeEach, describe, expect, it, vi } from 'vitest';

const fetchHallPageMock = vi.fn();
const fetchNoticePageMock = vi.fn();
vi.mock('./api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api')>();
  return {
    ...actual,
    fetchHallPage: (...a: unknown[]) => fetchHallPageMock(...a),
    fetchNoticePage: (...a: unknown[]) => fetchNoticePageMock(...a),
  };
});
vi.mock('@vben/icons', () => ({
  IconifyIcon: { name: 'IconifyIcon', props: ['icon'], render: () => null },
}));

import HallPage from './index.vue';

const router = createRouter({
  history: createWebHashHistory(),
  routes: [{ component: { template: '<div/>' }, path: '/:all(.*)*' }],
});

const flush = async () => {
  for (let i = 0; i < 12; i += 1) await nextTick();
  await new Promise((r) => setTimeout(r, 20));
  for (let i = 0; i < 12; i += 1) await nextTick();
};

async function mountHall() {
  document.body.innerHTML = '';
  const host = document.createElement('div');
  document.body.append(host);
  const app = createApp({ render: () => h(HallPage) });
  app.use(router);
  app.mount(host);
  await flush();
  return { app, text: () => document.body.textContent || '' };
}

const SELF_ROW = {
  id: '2100512348173434882',
  noticeTypeName: '招标公告',
  publishTime: 1789000000000,
  sourceKind: 'SELF',
  sourceName: '本站',
  title: '本站-最严格闭环公告',
};
const EXT_ROW = {
  id: '2099733406579638273',
  noticeTypeRaw: '采购公告',
  publishTime: 1789401600000,
  sourceKind: 'EXTERNAL',
  sourceName: '秦源电子招标平台',
  title: '外部-机房设备采购公告',
};

describe('公告大厅 · 本站来源界面', () => {
  beforeEach(() => {
    fetchHallPageMock.mockReset();
    fetchNoticePageMock.mockReset();
    fetchHallPageMock.mockResolvedValue({ list: [SELF_ROW, EXT_ROW], total: 2 });
    fetchNoticePageMock.mockResolvedValue({
      list: [{ id: 'x', sourceCode: 'QYZB', sourceName: '秦源电子招标平台', title: '探测用' }],
      total: 1,
    });
  });

  it('本站行：带「本站」标记 + 性质列显示公告类型 + 点行去本站详情', async () => {
    const view = await mountHall();
    const text = view.text();
    expect(text).toContain('本站-最严格闭环公告');
    expect(text).toContain('本站'); // 行内标记
    expect(text).toContain('招标公告'); // 性质列：平台认定的类型，而不是 "—"
    expect(text).toContain('外部-机房设备采购公告');

    const selfLink = [...document.body.querySelectorAll('.table-row')].find((el) =>
      (el.textContent || '').includes('本站-最严格闭环公告'),
    ) as HTMLElement;
    selfLink.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    await flush();
    expect(router.currentRoute.value.path).toBe('/portal/bidding/internal-detail/2100512348173434882');
    view.app.unmount();
  });

  it('外站行：点行仍去外部公告详情', async () => {
    const view = await mountHall();
    const extLink = [...document.body.querySelectorAll('.table-row')].find((el) =>
      (el.textContent || '').includes('外部-机房设备采购公告'),
    ) as HTMLElement;
    extLink.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    await flush();
    expect(router.currentRoute.value.path).toBe('/portal/bidding/detail/2099733406579638273');
    view.app.unmount();
  });

  it('来源站筛选：下拉里钉着「本站（本平台发布）」；点了之后出现本站口径说明', async () => {
    const view = await mountHall();
    const pill = [...document.body.querySelectorAll('.bid-pill')].find(
      (el) => (el.textContent || '').trim() === '本站（本平台发布）',
    ) as HTMLElement;
    expect(pill).toBeTruthy();
    pill.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    await flush();
    expect(fetchHallPageMock).toHaveBeenCalledWith(expect.objectContaining({ sourceCode: '__self__' }));
    expect(view.text()).toContain('本站 = 本平台发布的公开公告');
    view.app.unmount();
  });

  it('带外部专属筛选：出现"当前筛选只作用于外部采集公告"提示', async () => {
    const view = await mountHall();
    const pill = [...document.body.querySelectorAll('.bid-pill')].find(
      (el) => (el.textContent || '').trim() === 'HTML 网页',
    ) as HTMLElement;
    expect(pill).toBeTruthy();
    pill.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    await flush();
    expect(fetchHallPageMock).toHaveBeenCalledWith(expect.objectContaining({ contentType: 'HTML' }));
    expect(view.text()).toContain('当前筛选只作用于外部采集公告');
    view.app.unmount();
  });
});
