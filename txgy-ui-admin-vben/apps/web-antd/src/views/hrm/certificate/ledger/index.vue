<script lang="ts" setup>
import type { HrmCertificateLedgerApi } from '#/api/hrm/certificate';

import { onMounted, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';
import { downloadFileFromBlobPart, openWindow } from '@vben/utils';

import { Button, Card, message, Select, Space, Table, Tag } from 'ant-design-vue';

import {
  exportCertificateLedger,
  getCertificateExpireWarning,
  getCertificateLedgerPage,
  getCertificateTypeSimpleList,
  HrmCertificateExpireStatus,
  HrmCertificateExpireStatusOptions,
  HrmCertificateVerifyStatus,
  HrmCertificateVerifyStatusOptions,
  verifyCertificate,
} from '#/api/hrm/certificate';

defineOptions({ name: 'HrmCertificateLedger' });

const loading = ref(false);
const list = ref<HrmCertificateLedgerApi.Ledger[]>([]);
const total = ref(0);
const types = ref<any[]>([]);
const warningMode = ref(false);

const query = ref<HrmCertificateLedgerApi.LedgerPageParams>({
  pageNo: 1,
  pageSize: 20,
});

async function loadTypes() {
  types.value = await getCertificateTypeSimpleList();
}

async function loadList() {
  loading.value = true;
  try {
    if (warningMode.value) {
      // 预警清单：临期（默认 90 天内）+ 已过期
      list.value = await getCertificateExpireWarning(90);
      total.value = list.value.length;
      return;
    }
    const page = await getCertificateLedgerPage(query.value);
    list.value = page.list;
    total.value = page.total;
  } finally {
    loading.value = false;
  }
}

watch(
  () => [
    query.value.typeId,
    query.value.expireStatus,
    query.value.verifyStatus,
    query.value.pageNo,
    warningMode.value,
  ],
  () => loadList(),
);

onMounted(async () => {
  await loadTypes();
  await loadList();
});

/** 核验：通过 / 不通过 */
async function handleVerify(
  row: HrmCertificateLedgerApi.Ledger,
  verifyStatus: number,
) {
  try {
    await verifyCertificate(row.id!, verifyStatus);
    message.success('核验结果已保存');
    await loadList();
  } catch {
    // 拦截提示由后端返回（例如未上传扫描件不允许核验通过）
  }
}

async function handleExport() {
  const data = await exportCertificateLedger({ ...query.value, pageNo: 1 });
  downloadFileFromBlobPart({ fileName: '证照台账.xls', source: data });
  message.success('导出成功');
}

const expireTagColor = (status?: number) =>
  status === HrmCertificateExpireStatus.EXPIRED
    ? 'error'
    : status === HrmCertificateExpireStatus.EXPIRING
      ? 'warning'
      : 'success';

const verifyTagColor = (status?: number) =>
  status === HrmCertificateVerifyStatus.VERIFIED
    ? 'success'
    : status === HrmCertificateVerifyStatus.REJECTED
      ? 'error'
      : 'default';
</script>

<template>
  <Page auto-content-height>
    <Card :bordered="false">
      <Space class="mb-3" wrap>
        <Select
          v-model:value="query.typeId"
          :options="types.map((item) => ({ label: item.name, value: item.id }))"
          allow-clear
          class="min-w-[220px]"
          placeholder="全部证照类型"
          show-search
          :filter-option="
            (input: string, option: any) =>
              String(option?.label ?? '')
                .toLowerCase()
                .includes(input.toLowerCase())
          "
        />
        <Select
          v-model:value="query.expireStatus"
          :options="[...HrmCertificateExpireStatusOptions]"
          allow-clear
          class="min-w-[130px]"
          placeholder="到期状态"
        />
        <Select
          v-model:value="query.verifyStatus"
          :options="[...HrmCertificateVerifyStatusOptions]"
          allow-clear
          class="min-w-[140px]"
          placeholder="核验状态"
        />
        <Button
          :type="warningMode ? 'primary' : 'default'"
          @click="warningMode = !warningMode"
        >
          {{ warningMode ? '退出预警清单' : '只看到期预警（90 天内到期 + 已过期）' }}
        </Button>
        <Button @click="handleExport">导出台账</Button>
      </Space>

      <Table
        :columns="[
          { title: '姓名', dataIndex: 'employeeName', width: 100 },
          { title: '工号', dataIndex: 'jobNumber', width: 110 },
          { title: '联系电话', dataIndex: 'mobile', width: 130 },
          { title: '证照类型', dataIndex: 'typeName', width: 190 },
          { title: '证件名称', dataIndex: 'name', width: 180 },
          { title: '证号', dataIndex: 'no', width: 170 },
          { title: '发证机关', dataIndex: 'issuingAuthority', width: 160 },
          { title: '有效期止', dataIndex: 'endTime', width: 130 },
          { title: '距到期', key: 'remainDays', width: 100 },
          { title: '到期状态', key: 'expireStatus', width: 100 },
          { title: '核验状态', key: 'verifyStatus', width: 110 },
          { title: '操作', key: 'action', width: 220, fixed: 'right' },
        ]"
        :data-source="list"
        :loading="loading"
        :pagination="
          warningMode
            ? false
            : {
                current: query.pageNo,
                pageSize: query.pageSize,
                total,
                onChange: (page: number) => (query.pageNo = page),
              }
        "
        :row-key="(r: HrmCertificateLedgerApi.Ledger) => String(r.id)"
        :scroll="{ x: 1800 }"
        bordered
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'remainDays'">
            <span v-if="record.remainDays !== null && record.remainDays !== undefined">
              {{
                record.remainDays < 0
                  ? `已过期 ${Math.abs(record.remainDays)} 天`
                  : `${record.remainDays} 天`
              }}
            </span>
            <span v-else>-</span>
          </template>
          <template v-else-if="column.key === 'expireStatus'">
            <Tag v-if="record.expireStatus" :color="expireTagColor(record.expireStatus)">
              {{ record.expireStatusName }}
            </Tag>
            <span v-else>-</span>
          </template>
          <template v-else-if="column.key === 'verifyStatus'">
            <Tag :color="verifyTagColor(record.verifyStatus)">
              {{ record.verifyStatusName }}
            </Tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <Button
              v-if="record.fileUrls?.length"
              type="link"
              @click="openWindow(record.fileUrls[0]!)"
            >
              看扫描件
            </Button>
            <Button type="link" @click="handleVerify(record, 1)">核验通过</Button>
            <Button danger type="link" @click="handleVerify(record, 2)">
              核验不通过
            </Button>
          </template>
        </template>
      </Table>
    </Card>
  </Page>
</template>
