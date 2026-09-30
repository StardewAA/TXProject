/* 临时组件挂载实测（跑完即删）：关联公告弹窗的「本站公告」块与维度口径文案 */
import { createApp, h, nextTick, ref } from 'vue';
import { createRouter, createWebHashHistory } from 'vue-router';

import { beforeEach, describe, expect, it, vi } from 'vitest';

const fetchNoticePage = vi.fn();
const fetchInternalNotices = vi.fn();
vi.mock('../api', () => ({
  CODE_DIMS: [
    { dim: 'projectCode', label: '项目编号', param: 'projectCode', coverage: 0.887 },
    { dim: 'tenderProjectCode', label: '招标项目编号', param: 'tenderProjectCode', coverage: 0.01 },
    { dim: 'bidSectionCode', label: '标段编号', param: 'bidSectionCode', coverage: 0.15 },
  ],
  CODE_LOW_COVERAGE: 0.3,
  fetchInternalNotices: (...a: unknown[]) => fetchInternalNotices(...a),
  fetchNoticePage: (...a: unknown[]) => fetchNoticePage(...a),
}));
vi.mock('@vben/icons', () => ({
  // 图标会去外网拉 iconify 数据（离线会 ETIMEDOUT），换成空组件
  IconifyIcon: { name: 'IconifyIcon', props: ['icon'], render: () => null },
}));

import NoticeLinkModal from './NoticeLinkModal.vue';

const router = createRouter({
  history: createWebHashHistory(),
  routes: [{ component: { template: '<div/>' }, path: '/:all(.*)*' }],
});

const flush = async () => {
  for (let i = 0; i < 12; i += 1) await nextTick();
  await new Promise((r) => setTimeout(r, 20));
  for (let i = 0; i < 12; i += 1) await nextTick();
};

/** 生产里 open 由父级 v-model 翻转（组件 watch(open) 才加载），这里如实模拟该翻转 */
async function mountWith(props: Record<string, unknown>) {
  const open = ref(false);
  const host = document.createElement('div');
  document.body.append(host);
  const app = createApp({
    render: () =>
      h(NoticeLinkModal, {
        ...props,
        open: open.value,
        'onUpdate:open': (v: boolean) => {
          open.value = v;
        },
      }),
  });
  app.use(router);
  app.mount(host);
  open.value = true;
  await flush();
  return { app, host, text: () => document.body.textContent || '' };
}

describe('关联公告弹窗 · 本站块', () => {
  beforeEach(() => {
    fetchNoticePage.mockReset();
    fetchInternalNotices.mockReset();
    document.body.innerHTML = '';
  });

  it('项目编号维度：本站块列出本站公告，点标题进 internal-detail', async () => {
    fetchNoticePage.mockResolvedValue({ list: [], total: 0 });
    fetchInternalNotices.mockResolvedValue({
      list: [{ id: '2100512348173434882', publisherName: '某招标人', publishTime: 1789000000000, title: '本站-招标公告-A' }],
      total: 1,
    });
    const view = await mountWith({ followKey: 'I610100202600000h', followType: 'PROJECT', subjectName: '某项目' });
    const text = view.text();
    expect(text).toContain('本站-招标公告-A');
    expect(text).toContain('本平台发布的公开公告');
    expect(text).toContain('本站公告见上方'); // 本站块有数据时不能说"没有任何公告"
    expect(fetchInternalNotices).toHaveBeenCalledWith(
      expect.objectContaining({ pageNo: 1, projectCode: 'I610100202600000h' }),
    );
    const link = [...document.body.querySelectorAll('a.nl-title')].find((a) =>
      (a.textContent || '').includes('本站-招标公告-A'),
    ) as HTMLAnchorElement;
    link.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    await flush();
    expect(router.currentRoute.value.path).toBe('/portal/bidding/internal-detail/2100512348173434882');
    view.app.unmount();
  });

  it('标段维度：不发本站请求，并明说该维度只列外部采集公告', async () => {
    fetchNoticePage.mockResolvedValue({ list: [{ id: '1', title: '外部-标段公告' }], total: 1 });
    const view = await mountWith({ followKey: '001', followType: 'SECTION' });
    const text = view.text();
    expect(fetchInternalNotices).not.toHaveBeenCalled();
    expect(text).toContain('本站公告目前只支持按「项目编号」查询');
    expect(text).toContain('外部-标段公告');
    view.app.unmount();
  });
});
