<script setup lang="ts">
/* 设置 · 规则配置（独立页）
 *
 * 2026-09-28 新增：阈值/版本/灰度/读取审计原先只作为「安许风险联动」页的第 ⑤ 块存在，
 * 属"配置类能力"且被多个台账共同消费（预警窗口、A/B/C 配备要求、岗位变更窗口…），
 * 按三台账 IA（判据三 频率原则）归入设置区，作为跨台账的单一配置入口。
 *
 * 实现复用：内容走 RuleConfigPanel（与安许页同一份实现）。
 * 权限：bidding:rule-config:{query,update}（面板内逐动作判）。
 * 口径：生效值来自「租户级 → 灰度 → 平台默认 → 代码兜底」逐层解析；前端不内联任何阈值。 */
import RuleConfigPanel from '../../safety-risk/modules/RuleConfigPanel.vue';
</script>

<template>
  <div class="bid-ind ct-page">
    <div class="ct-head">
      <div>
        <h2 class="ct-title">规则配置</h2>
        <p class="ct-sub">阈值 · 发布与灰度 · 版本回滚 · 读取审计 · 被全部台账共同消费</p>
      </div>
    </div>
    <div class="ct-panel">
      <div class="ct-panel-b">
        <RuleConfigPanel />
      </div>
    </div>
  </div>
</template>
