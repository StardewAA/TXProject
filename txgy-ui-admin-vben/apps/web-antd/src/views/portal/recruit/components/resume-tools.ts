/**
 * 招聘门户「简历工具」的共享定义。
 *
 * 这里只放纯数据与纯函数，供 index.vue（右上角菜单）与 online-resume.vue（简历页工具条）
 * 复用同一套卡片与动作请求，避免两处各写一份逻辑。
 */

import resumeAiIcon from '#/assets/imgs/resume-ai-icon.png';

/** 四张工具卡片的标识 */
export type ResumeToolKey = 'ai' | 'edit' | 'generate' | 'upload' | 'version';

/**
 * 点击卡片后的真实行为。
 *
 * 四张卡片目前都已接通真实能力：AI 诊断、在线编辑、简历版本、附件管理。
 * 若将来新增尚未接通的能力，应在此显式标注为占位，不要用假结果糊弄用户。
 */
export type ResumeToolKind = 'ai' | 'edit' | 'generate' | 'upload' | 'version';

export interface ResumeToolItem {
  /** 卡片唯一标识 */
  key: ResumeToolKey;
  /** 卡片标题。说明文案已从卡片上去掉，需要时从 git 历史里取回 */
  title: string;
  /** Iconify 图标名。有 iconImage 时不渲染，留着是它的降级值 */
  icon: string;
  /**
   * 位图图标（透明 PNG），给了就优先用它。
   *
   * 目前只有「AI简历」一张用：那张插画是机器人 + 文档 + 对话气泡三个元素，
   * 图标字体画不出来。代价是位图不跟随文字颜色，
   * 所以它所在的那张卡不能沿用「悬停翻成实心反白」——见 resume-tools-menu.vue。
   */
  iconImage?: string;
  /** 点击后真正会发生什么 */
  kind: ResumeToolKind;
}

/** 数组顺序就是菜单里的排布顺序，改动前先确认是不是真的要换位置 */
export const RESUME_TOOL_ITEMS: readonly ResumeToolItem[] = [
  {
    key: 'edit',
    title: '在线编辑',
    icon: 'lucide:square-pen',
    kind: 'edit',
  },
  {
    key: 'upload',
    title: '附件上传',
    icon: 'lucide:cloud-upload',
    kind: 'upload',
  },
  {
    key: 'ai',
    title: 'AI简历',
    /*
     * 机器人 + 对话气泡，对应「问几句话，就生成一份简历草稿」这件事。
     * 原来用的 sparkles 只是通用的「智能」点缀，四张卡里唯一没交代自己做什么的一张。
     * 必须留在 lucide 集合内 —— resume-tools.test.ts 断言所有图标都以 lucide: 开头。
     */
    icon: 'lucide:bot-message-square',
    /*
     * 插画的原始蓝和这张卡的绿色对不上（同级那张「附件简历制作」就用 --job-green），
     * 所以把蓝和青两档按明度重映射到了 #0C9653 → #5FD8A4 这条绿渐变上，
     * 只有色相变了，形体和深浅层次保持原样。要换回原色，替换这个 PNG 即可。
     */
    iconImage: resumeAiIcon,
    kind: 'ai',
  },
  {
    key: 'generate',
    title: '附件简历制作',
    icon: 'lucide:file-pen-line',
    kind: 'generate',
  },
];

/**
 * 跨页面传递的简历动作请求。
 *
 * `nonce` 全局单调递增（不随请求被清空而回退），消费方按 nonce 去重，
 * 保证同一次点击只触发一次动作，属性残留也不会被反复触发。
 */
export interface ResumeToolRequest {
  action: 'ai' | 'edit' | 'upload' | 'version';
  nonce: number;
}

let resumeToolRequestSeq = 0;

/** 生成一次新的动作请求，nonce 全局递增 */
export function createResumeToolRequest(
  action: ResumeToolRequest['action'],
): ResumeToolRequest {
  resumeToolRequestSeq += 1;
  return { action, nonce: resumeToolRequestSeq };
}

/** 该请求是否还没被消费过 */
export function isUnhandledResumeToolRequest(
  request: ResumeToolRequest | undefined,
  handledNonce: number,
): request is ResumeToolRequest {
  return Boolean(request && request.nonce > handledNonce);
}
