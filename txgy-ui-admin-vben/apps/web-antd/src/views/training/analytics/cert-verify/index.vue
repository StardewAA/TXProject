<script lang="ts" setup>
import { ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Button, Card, Descriptions, Input, message } from 'ant-design-vue';

import PageHeaderBar from '../../components/page-header-bar.vue';

defineOptions({ name: 'TrainingCertVerify' });

const certCode = ref('');
const verifyResult = ref<Record<string, any>>();
const loading = ref(false);

// TODO[模块九-证书验真] 对接 /admin-api/training/analytics/cert-verify/my 真实验真
function handleVerify() {
  if (!certCode.value.trim()) {
    message.warning('请输入证书编号');
    return;
  }
  loading.value = true;
  setTimeout(() => {
    loading.value = false;
    verifyResult.value = {
      certCode: certCode.value.trim(),
      valid: false,
      revoked: false,
      message: '验真功能开发中，当前未查询到证书信息',
    };
  }, 300);
}
</script>

<template>
  <Page>
    <div class="flex flex-col gap-4 pb-4">
      <PageHeaderBar
        crumb="云培训学院 / 模块 09 / 97"
        title="证书公开验真"
        description="输入证书编号查询证书有效性、吊销状态与原因。独立公共 H5 免登录页面将在后续迭代上线。"
      />

      <Card :bordered="false" title="验真查询">
        <div class="flex items-center gap-3">
          <Input
            v-model:value="certCode"
            class="max-w-md"
            allow-clear
            placeholder="请输入证书编号，如 TX2026C0001"
            @press-enter="handleVerify"
          />
          <Button type="primary" :loading="loading" @click="handleVerify"> 验真 </Button>
        </div>
      </Card>

      <Card v-if="verifyResult" :bordered="false" title="验真结果">
        <Descriptions :column="1" bordered size="small">
          <Descriptions.Item label="证书编号">{{ verifyResult.certCode }}</Descriptions.Item>
          <Descriptions.Item label="是否有效">
            {{ verifyResult.valid ? '有效' : '无效' }}
          </Descriptions.Item>
          <Descriptions.Item label="吊销状态">
            {{ verifyResult.revoked ? '已吊销' : '未吊销' }}
          </Descriptions.Item>
          <Descriptions.Item label="说明">{{ verifyResult.message }}</Descriptions.Item>
        </Descriptions>
      </Card>
    </div>
  </Page>
</template>
