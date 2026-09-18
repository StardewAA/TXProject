<script lang="ts" setup>
import { Page } from '@vben/common-ui';
import { Alert } from 'ant-design-vue';
</script>
<template>
  <Page title="缺货采购对接预留">
    <Alert type="warning" show-icon message="旧 ERP 内存模拟已停用"
      description="ERP 缺货采购接口尚未完成，不能模拟成真实的已接收或库存就绪。订单库存预占、出库、退货入库请在交易订单详情的 ERP 库存履约记录中查看；本地测试采用独立模拟账本，不修改 WMS。" />
  </Page>
</template>
