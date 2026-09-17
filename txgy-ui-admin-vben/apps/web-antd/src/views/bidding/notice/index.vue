<!--
  公告 / 公示管理 —— **管理端页面**（菜单 6754 指向本文件，勿改路径）。
  职责：管理前台发布的内部公告 —— 全量台账（跨状态/跨租户视图）、状态分布、筛选检索、详情核对、
  下架/删除、运维动作（索引重建 / 公开同步）。
  工作台本体在 ./NoticeWorkbench.vue（前台发布页与管理端共用同一实现，靠显式 mode prop 区分身份，
  不再靠 route.path 嗅探——那种写法正是"前后台共用一个工作台"的根源）。
  口径（用户已裁定）：发布动作归前台（门户 · 公告发布 = 企业经办人发布），本页收敛为监管——
  全量台账/详情留痕/下架/运维（索引重建、公开同步）；起草/编辑/发布/重新发布/删除仅在门户。
  若将来内部公告需"前台提交 → 后台审核"，再在此增加审核动作与待审队列（依赖后端 PENDING_AUDIT/REJECTED）。
-->
<script lang="ts" setup>
import NoticeWorkbench from './NoticeWorkbench.vue';
</script>

<template>
  <NoticeWorkbench mode="admin" />
</template>
