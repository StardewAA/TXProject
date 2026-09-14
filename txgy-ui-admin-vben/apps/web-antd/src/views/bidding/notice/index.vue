<!--
  公告 / 公示管理 —— **管理端页面**（菜单 6754 指向本文件，勿改路径）。
  职责：管理前台发布的内部公告 —— 全量台账（跨状态/跨租户视图）、状态分布、筛选检索、详情核对、
  下架/删除、运维动作（索引重建 / 公开同步）。
  工作台本体在 ./NoticeWorkbench.vue（前台发布页与管理端共用同一实现，靠显式 mode prop 区分身份，
  不再靠 route.path 嗅探——那种写法正是"前后台共用一个工作台"的根源）。
  待业务口径确认后本页应进一步收敛：若内部公告需要"前台提交 → 后台审核"，管理端在此处增加审核动作与
  待审队列（依赖后端 PENDING_AUDIT/REJECTED 状态与 submit/approve/reject 端点，见 docs 契约）；
  若确认免审直发，则发布动作归前台、本页只保留监管动作。
-->
<script lang="ts" setup>
import NoticeWorkbench from './NoticeWorkbench.vue';
</script>

<template>
  <NoticeWorkbench mode="admin" />
</template>
