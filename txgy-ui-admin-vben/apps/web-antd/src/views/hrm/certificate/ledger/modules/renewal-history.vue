<script lang="ts" setup>
import type { HrmCertificateLedgerApi } from '#/api/hrm/certificate/ledger';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Empty, Spin, Tag } from 'ant-design-vue';

import { getCertificateRenewalList } from '#/api/hrm/certificate/ledger';

import { HrmCertificateRenewalType } from '../../constants';

defineOptions({ name: 'HrmCertificateRenewalHistory' });

const loading = ref(false);
const records = ref<HrmCertificateLedgerApi.CertificateRenewal[]>([]);
const certificateName = ref('');

function formatDateTime(value?: number | string) {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }
  const pad = (item: number) => String(item).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(
    date.getDate(),
  )} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function getRange(start?: number, end?: number) {
  if (!start && !end) {
    return '-';
  }
  return `${formatDateTime(start)} ~ ${formatDateTime(end)}`;
}

const [Modal, modalApi] = useVbenModal({
  showConfirmButton: false,
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      records.value = [];
      certificateName.value = '';
      return;
    }
    const data = modalApi.getData<HrmCertificateLedgerApi.CertificateLedger>();
    if (!data?.id) {
      return;
    }
    certificateName.value = data.name ?? '';
    loading.value = true;
    try {
      records.value = await getCertificateRenewalList(data.id);
    } finally {
      loading.value = false;
    }
  },
});
</script>

<template>
  <Modal :title="`续证/复审历史 - ${certificateName}`" class="w-[860px]">
    <Spin :spinning="loading">
      <div class="mx-4 max-h-[60vh] overflow-y-auto">
        <Empty v-if="records.length === 0" description="暂无续证或复审记录" />
        <div
          v-for="record in records"
          :key="record.id"
          class="mb-3 rounded-md border p-3"
        >
          <div class="mb-2 flex items-center justify-between">
            <div class="flex items-center gap-2">
              <Tag
                :color="
                  record.type === HrmCertificateRenewalType.REVIEW
                    ? 'processing'
                    : 'success'
                "
              >
                {{
                  record.type === HrmCertificateRenewalType.REVIEW
                    ? '复审'
                    : '续证'
                }}
              </Tag>
              <span class="font-medium">
                {{ record.newNo || record.oldNo || '-' }}
              </span>
            </div>
            <span class="text-muted-foreground text-sm">
              办理时间：{{ formatDateTime(record.renewTime) }}
            </span>
          </div>
          <div class="text-sm leading-6">
            <div>
              <span class="text-muted-foreground">原有效期：</span>
              {{ getRange(record.oldStartTime, record.oldEndTime) }}
            </div>
            <div>
              <span class="text-muted-foreground">新有效期：</span>
              {{ getRange(record.newStartTime, record.newEndTime) }}
            </div>
            <div v-if="record.reviewTime">
              <span class="text-muted-foreground">复审日期：</span>
              {{ formatDateTime(record.reviewTime) }}
            </div>
            <div>
              <span class="text-muted-foreground">发证机构：</span>
              {{ record.issuingAuthority || '-' }}
            </div>
            <div>
              <span class="text-muted-foreground">新附件：</span>
              {{ record.attachmentUrl || '-' }}
            </div>
            <div v-if="record.remark">
              <span class="text-muted-foreground">备注：</span>
              {{ record.remark }}
            </div>
          </div>
        </div>
      </div>
    </Spin>
  </Modal>
</template>
