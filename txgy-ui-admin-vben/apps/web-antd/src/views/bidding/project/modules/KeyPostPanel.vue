<script setup lang="ts">
/* 项目关键岗位变更监控（2026-09-28 自「安许风险联动」页迁入项目台账）
 *
 * 【为什么迁到项目域】按《电子证照域-三台账信息架构与界面重设计-2026-09-28》§1.2 判据一（主实体原则）：
 *   本功能的主实体是**项目**，不是企业也不是员工——它回答的是"这个项目的关键岗位在窗口内换了几次人"。
 *   两条独立证据：① 后端接口 GET /bidding/safety-permit/project/key-post-changes；
 *   ② 它复用的权限码是 bidding:project:query / bidding:project:update（指向项目域）。
 *   一处能力挂在"安许风险"名下、却用项目域权限码开门，本身就是归属错误的直接证据。
 *
 * 【为什么是嵌入而不是复制】安许风险页已于 2026-09-30 按主体拆到 safety-risk/panels/，
 *   本面板直接 import 项目侧面板 ProjectKeyPostPanel（内含 ④ 关键岗位变更监控 + ④b 登记弹窗），
 *   实现始终只有一份（口径依据《接口相似实现归并结论》：同一能力只留一处实现，复制必然漂移）。
 */
import ProjectKeyPostPanel from '../../safety-risk/panels/ProjectKeyPostPanel.vue';

defineProps<{ open: boolean }>();
const emit = defineEmits<{ 'update:open': [boolean] }>();
</script>

<template>
  <a-drawer
    :open="open"
    class="bid-ind"
    :width="1120"
    title="项目关键岗位变更监控"
    destroy-on-close
    @update:open="(v: boolean) => emit('update:open', v)"
  >
    <!-- 项目侧面板只含 ④ 关键岗位变更监控 + ④b 登记弹窗；其余能力（风险卡片/风险大盘/风险工单/
         多岗兼任/人员风险/规则配置）各有归属台账，都不在本面板里，避免"同一个功能出现在两处"。 -->
    <ProjectKeyPostPanel />
  </a-drawer>
</template>
