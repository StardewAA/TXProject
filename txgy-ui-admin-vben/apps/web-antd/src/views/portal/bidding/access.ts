/* 门户前台判权 —— **单一真源**，口径与后台页面完全一致：**权限码未下发时放行**
 * （accessCodes 为空数组时 return true；最终由后端 @PreAuthorize 裁决），避免界面被锁死。
 *
 * 为什么门户需要它：门户此前 **11 个页面零判权**，而其中 4 处创建动作打的是后台权限码
 * （bidding:project:create / bidding:bid-section:create / bidding:notice:create）——
 * 无权限用户看得见按钮、点下去才 403；同一动作在后台是**隐藏**的（can() 包住按钮，见 views/bidding/project/index.vue）。
 * 同一套权限系统、两种表现 = 功能划分口径不一致。此处统一为"能就显示、不能就隐藏并说明如何开通"。
 *
 * 用法：const { can } = usePortalCan();  <Button v-if="can('bidding:project:create')">创建项目</Button>
 */
import { useAccess } from '@vben/access';
import { useAccessStore } from '@vben/stores';

export function usePortalCan() {
  const { hasAccessByCodes } = useAccess();
  const accessStore = useAccessStore();
  /** 权限判定：权限码未下发时放行（与后台 can() 同口径） */
  function can(code: string): boolean {
    const codes = accessStore.accessCodes ?? [];
    if (codes.length === 0) return true;
    return codes.includes('*') || codes.includes('*:*:*') || hasAccessByCodes([code]);
  }
  return { can };
}
