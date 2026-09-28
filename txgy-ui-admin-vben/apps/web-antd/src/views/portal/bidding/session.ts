/**
 * 门户账号信息引导 —— **「登录态有效」与「能识别出当前账号」是两件事**
 *
 * 背景（2026-09-29 用户反馈：意向投标提示需登录，但用户明明是登录的；报错不应写成用户没登录）：
 *   门户登录**只持久化 token**（同 views/portal/recruit/messages.vue 的口径），
 *   userStore.userInfo 由路由守卫在「非公开门户页」才触发加载；
 *   公告大厅/详情是公开页（路由 meta.ignoreAccess），守卫直接放行 → 已登录用户的 userInfo 仍为空。
 *   后果：依赖 userId 的动作（意向投标建档）把**已登录**用户误判成「需登录」。
 *
 * 口径：
 *   · 无 token              → anonymous：确实没登录，引导登录页（此时说「需登录」是正确的）；
 *   · 有 token 但取不到账号  → system：**系统侧问题**，绝不能写成用户没登录，
 *                             调用方应提示「系统未能加载账号信息」并允许重试。
 */
import { useAccessStore, useUserStore } from '@vben/stores';

import { useAuthStore } from '#/store';

export type PortalAccountResult =
  | { message: string; ok: false; reason: 'system' }
  | { ok: false; reason: 'anonymous' }
  | { ok: true; userId: number | string };

/** 取当前账号 userId：必要时先补一次用户信息加载（幂等，可重复调用） */
export async function ensurePortalAccount(): Promise<PortalAccountResult> {
  const userStore = useUserStore();
  const accessStore = useAccessStore();

  const existed = userStore.userInfo?.userId;
  if (existed !== undefined && existed !== null) {
    return { ok: true, userId: existed };
  }
  if (!accessStore.accessToken) {
    return { ok: false, reason: 'anonymous' };
  }
  try {
    const info = await useAuthStore().fetchUserInfo();
    // [2026-10-02 修复] 双保险：后端 user 标识字段是 id，vben 口径是 userId
    // （auth store 的 fetchUserInfo 已做映射；这里再兜一层，避免任何调用路径拿到 undefined）
    const userId = info?.user?.userId ?? (info?.user as any)?.id;
    if (userId === undefined || userId === null) {
      return {
        message: '系统未能加载当前账号信息（登录态有效，但账号数据缺失）',
        ok: false,
        reason: 'system',
      };
    }
    return { ok: true, userId };
  } catch (e) {
    return {
      message: '系统未能加载当前账号信息：' + ((e as Error)?.message || String(e)),
      ok: false,
      reason: 'system',
    };
  }
}