<script setup lang="ts">
/* 设置 · 所属企业（独立页）—— 2026-09-29 改为**口径说明页**
 *
 * 背景（用户报错：「请求地址不存在:admin-api/bidding/subject-company/page」）：
 * 该页面原来调用的 /bidding/subject-company/*（所属企业名录 CRUD）**后端从未实施**，
 * 后端口径已定**不做**（本平台单租户部署：跨租户企业信息走「平台审查中心」，
 * 本企业主体以「主体档案」为准）。继续调只会让用户点进来就吃 404。
 *
 * 因此本页：**不发起任何接口请求**，只给出明确的去处，保证用户再也点不到那个 404。
 * 相应的前端 api 与三个专用组件（api/bidding/subjectCompany、CompanyManagePanel、
 * CompanyManageDrawer、CompanySelect）已一并移除，避免留一个再也调不通的调用点。
 *
 * 路由：菜单 component = 'bidding/settings/company/index'（见 sql/history 的设置区菜单脚本）；
 * 若要把本页从导航里彻底摘掉，见交付说明里的 SQL 建议（改 system_menu.visible，**不要 DELETE**：
 * 权限点靠 system_menu 行承载，删行会连带回收 system_role_menu 授权）。
 */
import { useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

import { message } from 'ant-design-vue';

const router = useRouter();

/** 目标菜单路径按**已注册路由**探测后再跳：菜单数据在库里、历史上有过命名空间迁移，
 *  写死单一路径一旦对不上就是新的"点进去 404"，正是本次要消灭的问题。 */
function goFirst(candidates: string[]): void {
  const hit = candidates.find((p) => router.getRoutes().some((r) => r.path === p));
  if (hit) {
    void router.push(hit);
    return;
  }
  message.warning('该入口在当前菜单配置下不可用，请从左侧菜单进入对应页面');
}

/** 本企业主体档案（设置区）：企业名称 / 统一社会信用代码等以这里为唯一来源 */
function goSubjectProfile(): void {
  goFirst(['/cert-settings/settings-subject', '/certificate/subject-profile', '/bidding/cert-settings/settings-subject']);
}
/** 平台审查中心：跨租户的企业资质/证照审查任务在这边筛选与处理 */
function goReviewCenter(): void {
  goFirst(['/certificate/review-center', '/bidding/certificate/review-center']);
}
</script>

<template>
  <div class="bid-ind ct-page">
    <div class="ct-head">
      <div>
        <h2 class="ct-title">所属企业</h2>
        <p class="ct-sub">本平台按单租户部署，企业主体信息统一由「主体档案」维护</p>
      </div>
    </div>

    <div class="ct-panel">
      <div class="ct-panel-b">
        <a-alert
          type="info"
          show-icon
          message="本页不再提供「所属企业名录」的维护"
          description="该功能未在本平台启用：企业主体以「主体档案」为准，跨租户的企业资质与证照在「平台审查中心」统一查看与审查。"
        />
        <div class="mt-4 flex flex-wrap gap-3">
          <a-button type="primary" @click="goSubjectProfile">
            <IconifyIcon icon="lucide:building-2" class="mr-1 inline-block align-middle" />
            去「主体档案」维护本企业信息
          </a-button>
          <a-button @click="goReviewCenter">
            <IconifyIcon icon="lucide:shield-check" class="mr-1 inline-block align-middle" />
            去「平台审查中心」查看企业审查任务
          </a-button>
        </div>
      </div>
    </div>
  </div>
</template>
