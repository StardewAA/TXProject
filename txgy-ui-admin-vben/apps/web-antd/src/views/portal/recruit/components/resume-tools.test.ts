import { createApp, nextTick } from 'vue';

import { afterEach, describe, expect, it, vi } from 'vitest';

// 用桩替换图标组件，避免测试里触发 Iconify 的图标数据请求
vi.mock('@vben/icons', () => ({
  IconifyIcon: {
    name: 'IconifyIcon',
    props: { icon: { default: '', type: String } },
    setup: () => () => null,
  },
}));

import {
  createResumeToolRequest,
  isUnhandledResumeToolRequest,
  RESUME_TOOL_ITEMS,
} from './resume-tools';
import ResumeToolsMenu from './resume-tools-menu.vue';

const mounted: Array<{ app: ReturnType<typeof createApp>; host: HTMLElement }> =
  [];

function mountMenu(props: Record<string, unknown> = {}) {
  const events: string[] = [];
  const host = document.createElement('div');
  document.body.append(host);
  const app = createApp(ResumeToolsMenu, {
    onAi: () => events.push('ai'),
    onEdit: () => events.push('edit'),
    onUpload: () => events.push('upload'),
    onVersion: () => events.push('version'),
    ...props,
  });
  app.mount(host);
  mounted.push({ app, host });
  return { events, host };
}

function getCards(host: HTMLElement) {
  return [...host.querySelectorAll<HTMLButtonElement>('.resume-tool-card')];
}

function getCardByTitle(host: HTMLElement, title: string) {
  return getCards(host).find(
    (card) => card.querySelector('.resume-tool-copy strong')?.textContent?.trim() === title,
  );
}

function getTrigger(host: HTMLElement) {
  return host.querySelector<HTMLButtonElement>('.resume-tools-trigger')!;
}

function getPanel(host: HTMLElement) {
  return host.querySelector<HTMLElement>('.resume-tools-panel')!;
}

afterEach(() => {
  while (mounted.length > 0) {
    const entry = mounted.pop()!;
    entry.app.unmount();
    entry.host.remove();
  }
});

describe('简历工具定义', () => {
  it('提供四张卡片，且全部使用已有 Iconify 图标', () => {
    expect(RESUME_TOOL_ITEMS).toHaveLength(4);
    expect(RESUME_TOOL_ITEMS.map((item) => item.title)).toEqual([
      'AI 简历助手',
      '在线编辑',
      '简历版本',
      '附件管理',
    ]);
    for (const item of RESUME_TOOL_ITEMS) {
      expect(item.icon.startsWith('lucide:')).toBe(true);
    }
  });

  it('四张卡片都对应真实动作，没有占位能力', () => {
    const kinds = RESUME_TOOL_ITEMS.map((item) => item.kind).sort();
    expect(kinds).toEqual(['ai', 'edit', 'upload', 'version']);
  });

  it('动作请求 nonce 全局递增，已消费的请求不会重复触发', () => {
    const first = createResumeToolRequest('upload');
    const second = createResumeToolRequest('upload');
    expect(first.action).toBe('upload');
    expect(second.nonce).toBeGreaterThan(first.nonce);
    // 模拟「消费后再点一次」：即使中间被清空，新请求的 nonce 也一定更大
    const third = createResumeToolRequest('upload');
    expect(third.nonce).toBeGreaterThan(second.nonce);
    expect(isUnhandledResumeToolRequest(third, second.nonce)).toBe(true);
    expect(isUnhandledResumeToolRequest(third, third.nonce)).toBe(false);
    expect(isUnhandledResumeToolRequest(second, third.nonce)).toBe(false);
    expect(isUnhandledResumeToolRequest(undefined, 0)).toBe(false);
  });

  it('编辑与上传共用同一套 nonce，跨动作也不会重复触发', () => {
    const edit = createResumeToolRequest('edit');
    const upload = createResumeToolRequest('upload');
    expect(edit.action).toBe('edit');
    expect(upload.nonce).toBeGreaterThan(edit.nonce);
    expect(isUnhandledResumeToolRequest(upload, edit.nonce)).toBe(true);
    expect(isUnhandledResumeToolRequest(edit, upload.nonce)).toBe(false);
  });
});

describe('简历工具菜单', () => {
  it('inline 形态直接展示四张卡片，不渲染触发器', () => {
    const { host } = mountMenu({ variant: 'inline' });
    expect(host.querySelector('.resume-tools-trigger')).toBeNull();
    expect(getCards(host)).toHaveLength(4);
    expect(getPanel(host).style.display).not.toBe('none');
  });

  it('点击在线编辑会抛出 edit 事件', async () => {
    const { events, host } = mountMenu({ variant: 'inline' });
    getCardByTitle(host, '在线编辑')!.click();
    await nextTick();
    expect(events).toEqual(['edit']);
  });

  it('点击附件管理会抛出 upload 事件', async () => {
    const { events, host } = mountMenu({ variant: 'inline' });
    getCardByTitle(host, '附件管理')!.click();
    await nextTick();
    expect(events).toEqual(['upload']);
  });

  it('点击简历版本会抛出 version 事件', async () => {
    const { events, host } = mountMenu({ variant: 'inline' });
    getCardByTitle(host, '简历版本')!.click();
    await nextTick();
    expect(events).toEqual(['version']);
  });

  it('点击 AI 简历助手会抛出 ai 事件', async () => {
    const { events, host } = mountMenu({ variant: 'inline' });
    getCardByTitle(host, 'AI 简历助手')!.click();
    await nextTick();
    expect(events).toEqual(['ai']);
  });

  it('dropdown 形态默认收起，点击触发器后展开', async () => {
    const { host } = mountMenu({ variant: 'dropdown' });
    const trigger = getTrigger(host);
    const panel = getPanel(host);
    expect(trigger.textContent).toContain('在线简历');
    expect(trigger.getAttribute('aria-expanded')).toBe('false');
    expect(panel.style.display).toBe('none');

    trigger.click();
    await nextTick();
    expect(trigger.getAttribute('aria-expanded')).toBe('true');
    expect(panel.style.display).not.toBe('none');
  });

  it('dropdown 形态支持 ESC 收起', async () => {
    const { host } = mountMenu({ variant: 'dropdown' });
    const trigger = getTrigger(host);
    trigger.click();
    await nextTick();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await nextTick();
    expect(trigger.getAttribute('aria-expanded')).toBe('false');
    expect(getPanel(host).style.display).toBe('none');
  });

  it('dropdown 形态支持点击外部收起', async () => {
    const { host } = mountMenu({ variant: 'dropdown' });
    const trigger = getTrigger(host);
    trigger.click();
    await nextTick();
    document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    await nextTick();
    expect(trigger.getAttribute('aria-expanded')).toBe('false');
  });

  it('选中任一能力后菜单收起，避免浮层挡住后续操作', async () => {
    const { host } = mountMenu({ variant: 'dropdown' });
    const trigger = getTrigger(host);
    trigger.click();
    await nextTick();
    getCardByTitle(host, 'AI 简历助手')!.click();
    await nextTick();
    expect(trigger.getAttribute('aria-expanded')).toBe('false');
  });

  it('不再渲染任何未接通占位说明', () => {
    const { host } = mountMenu({ variant: 'dropdown' });
    expect(host.querySelector('.resume-tools-notice')).toBeNull();
    expect(host.querySelector('.resume-tool-badge')).toBeNull();
  });
});
