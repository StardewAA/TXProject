/**
 * 招聘门户「简历工具」的共享定义。
 *
 * 这里只放纯数据与纯函数，供 index.vue（右上角菜单）与 online-resume.vue（简历页工具条）
 * 复用同一套卡片与动作请求，避免两处各写一份逻辑。
 */

/** 四张工具卡片的标识 */
export type ResumeToolKey = 'ai' | 'edit' | 'upload' | 'version';

/**
 * 点击卡片后的真实行为。
 *
 * 四张卡片目前都已接通真实能力：AI 诊断、在线编辑、简历版本、附件管理。
 * 若将来新增尚未接通的能力，应在此显式标注为占位，不要用假结果糊弄用户。
 */
export type ResumeToolKind = 'ai' | 'edit' | 'upload' | 'version';

export interface ResumeToolItem {
  /** 卡片唯一标识 */
  key: ResumeToolKey;
  /** 卡片标题 */
  title: string;
  /** 卡片说明 */
  description: string;
  /** 使用仓库已有的 Iconify 图标名，不引入图片资源 */
  icon: string;
  /** 点击后真正会发生什么 */
  kind: ResumeToolKind;
}

export const RESUME_TOOL_ITEMS: readonly ResumeToolItem[] = [
  {
    key: 'ai',
    title: 'AI 简历助手',
    description: '诊断完整度与岗位匹配',
    icon: 'lucide:sparkles',
    kind: 'ai',
  },
  {
    key: 'edit',
    title: '在线编辑',
    description: '逐项完善在线简历',
    icon: 'lucide:square-pen',
    kind: 'edit',
  },
  {
    key: 'version',
    title: '简历版本',
    description: '按岗位保留投递版本',
    icon: 'lucide:git-branch',
    kind: 'version',
  },
  {
    key: 'upload',
    title: '附件管理',
    description: '上传与管理 PDF / Word 简历',
    icon: 'lucide:cloud-upload',
    kind: 'upload',
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
