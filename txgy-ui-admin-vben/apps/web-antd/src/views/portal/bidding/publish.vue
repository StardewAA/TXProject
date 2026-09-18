<!--
  门户 · 企业发布工作台（**前台自有页面**）
  为什么要有这一层：此前路由 /portal/bidding/publish 直接把管理端页面 #/views/bidding/notice/index.vue
  挂到门户下，组件再靠 route.path.startsWith('/portal') 猜身份 —— 前台等于没有自己的页面，
  前后台职责因此分不开（用户 2026-09-14 指出："你前台和后台不能共用一个工作台"）。
  现在：门户拥有自己的页面与面向企业的说明文案，发布工作台以 <NoticeWorkbench mode="portal" /> 显式复用。
  范围：仅本企业（当前登录租户）内部公告 —— 起草 → 发布 → 撤下 → 归档；
        外部采集公告的审核/发布在管理端「外部公告管理」，本页不做。
  口径（已裁定）：发布动作归前台（本页即发布入口）；管理端收敛为监管（全量/下架/留痕/运维）。
  仅存疑：内部公告是否需要"提交 → 平台审核"才能发布 —— 取决于后端是否补 PENDING_AUDIT/REJECTED（待后端）。
-->
<script lang="ts" setup>
import NoticeWorkbench from '#/views/bidding/notice/NoticeWorkbench.vue';
</script>

<template>
  <div class="portal-publish">
    <div class="pp-note">
      <b>本页发布的是本企业内部公告</b>：起草 → 发布 → 撤下 → 归档，作用于当前登录租户，发布后关注该项目的用户会收到动态。
      外部采集公告的审核与发布请到管理端「外部公告管理」；平台侧对本企业公告的下架/归档由平台管理员操作。
    </div>
    <NoticeWorkbench mode="portal" />
  </div>
</template>

<style scoped>
.portal-publish { padding: 12px 16px 0; }
.pp-note { max-width: 1360px; margin: 0 auto 10px; padding: 10px 14px; font-size: 12.5px; line-height: 1.7; color: #4a5561; background: #f4f7fb; border: 1px solid #dce6f2; }
.pp-note b { color: #17539a; }
</style>
