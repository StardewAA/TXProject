/**
 * 当前求职者的头像（模块级单例）。
 *
 * 顶栏在好几个页面各挂一次（门户外壳、职位详情、企业页、消息、附件制作），
 * 而头像上传发生在简历页与资料完善弹窗里。状态如果各自持有，
 * 传完头像顶栏还是旧的默认图标，得刷新才变 —— 所以放模块级：
 * 谁改了谁调 setRecruitAvatar()，所有挂载中的顶栏同步重渲染；
 * 页面之间跳转也不会丢（模块状态在 SPA 生命周期内一直活着）。
 *
 * 这里只放「本人上传的头像」。简历页上还有个「附件简历里提取出来的头像」，
 * 那是该页的展示便利，不往顶栏同步。
 */

import { ref } from 'vue';

import { getRecruitProfileResume } from '#/api/hrm/portal/resume';

/** 头像原始地址（服务端存的相对路径），渲染前过 getHrmRecruitFilePreviewUrl */
export const recruitAvatarUrl = ref('');

let loadPromise: Promise<void> | undefined;

function readAvatar(content?: string) {
  if (!content) return '';
  try {
    const parsed = JSON.parse(content) as { avatar?: unknown };
    return typeof parsed?.avatar === 'string' ? parsed.avatar.trim() : '';
  } catch {
    return '';
  }
}

/** 读一次「求职资料（系统）」。多个顶栏实例共用同一个请求 */
export function loadRecruitAvatar() {
  loadPromise ??= getRecruitProfileResume()
    .then((resume) => {
      recruitAvatarUrl.value = readAvatar(resume?.content);
    })
    .catch(() => {
      // 读不到就维持空值，顶栏退回默认头像，不打断页面
    });
  return loadPromise;
}

/** 上传或移除头像后由发起方调用，顶栏立刻跟着变 */
export function setRecruitAvatar(fileUrl: string) {
  recruitAvatarUrl.value = fileUrl;
}
