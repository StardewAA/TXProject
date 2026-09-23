<template>
  <div class="mes-workshop-3d-config">
    <p class="tip-title">场景由 MES 实时数据自动驱动</p>
    <p class="tip-line">车间 / 工位 / 设备 / 工艺路线工序 / 生产任务 / 安灯记录，共 7 个接口。</p>
    <p class="tip-line">设备颜色对应当前状态：<span class="run">生产中</span> · <span class="fix">维护中</span> · <span class="stop">停机</span>。</p>
    <p class="tip-line">工位出现未处置安灯时会转为红色脉冲告警。</p>
    <p class="tip-line">车间与工位的空间位置来自本地布局文件，业务文案以数据库为准。</p>
  </div>
</template>

<script setup lang="ts">
import { PropType } from 'vue'
import { option } from './config'

const props = defineProps({
  optionData: {
    type: Object as PropType<typeof option>,
    required: true
  }
})
</script>

<style scoped>
.mes-workshop-3d-config {
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.9;
  color: #9fb3c8;
}
.mes-workshop-3d-config .tip-title {
  color: #e6f2ff;
  font-weight: 600;
  margin-bottom: 6px;
}
.mes-workshop-3d-config .tip-line {
  margin: 0;
}
.mes-workshop-3d-config .run {
  color: #22d38a;
}
.mes-workshop-3d-config .fix {
  color: #f5a623;
}
.mes-workshop-3d-config .stop {
  color: #8fa9c4;
}
</style>
