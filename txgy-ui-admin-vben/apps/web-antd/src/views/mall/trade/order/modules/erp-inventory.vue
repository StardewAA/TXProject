<script setup lang="ts">
import { ref, watch } from 'vue';
import { Alert, Button, InputNumber, message, Table } from 'ant-design-vue';
import { getErpInventory, retryErpInventory, seedErpMockInventory } from '#/api/mall/trade/erp-inventory';
import type { ErpInventoryEntry } from '#/api/mall/trade/erp-inventory';
const props = defineProps<{ orderId: number }>();
const rows = ref<ErpInventoryEntry[]>([]), loading = ref(false), failed = ref(false), busy = ref(false);
const skuId = ref<number>(), quantity = ref<number>();
const names: Record<string,string> = { RESERVE:'库存预占',RELEASE:'释放预占',OUTBOUND:'出库',RETURN:'退货入库' };
const statuses: Record<string,string> = { PENDING:'待 ERP 确认',CONFIRMED:'已确认',REJECTED:'处理失败' };
const columns = [
 { title:'业务',dataIndex:'operation' },{ title:'环境',dataIndex:'mode' },
 { title:'状态',dataIndex:'status' },{ title:'请求编号',dataIndex:'id' },
 { title:'处理说明',dataIndex:'message' },{ title:'操作',key:'action' },
];
async function load() {
 if (!props.orderId) return;
 loading.value=true;failed.value=false;
 try { rows.value=await getErpInventory(props.orderId); } catch {failed.value=true;} finally{loading.value=false;}
}
async function retry(id:string){
 busy.value=true;
 try{await retryErpInventory(id);message.info('已记录重试；未对接 ERP 时仍为待确认，不代表已经发送');await load();}
 finally{busy.value=false;}
}
async function seed(){
 if(!skuId.value || !Number.isInteger(skuId.value) || quantity.value===undefined || !Number.isInteger(quantity.value) || quantity.value<0)
   return void message.warning('填写有效SKU和非负整数数量');
 busy.value=true;
 try{await seedErpMockInventory(skuId.value,quantity.value);message.success('独立模拟库存已初始化，不影响 WMS');}
 finally{busy.value=false;}
}
watch(()=>props.orderId,load,{immediate:true});
</script>
<template>
 <section class="m-4 rounded border p-4">
  <h3 class="mb-3 font-bold">ERP 库存履约记录</h3>
  <Alert message="WMS 只读；待 ERP 确认不代表库存已锁定或出入库成功。模拟结果仅供测试。" type="warning" show-icon />
  <Button class="my-3" :loading="loading" @click="load">刷新处理结果</Button>
  <Alert v-if="failed" message="ERP记录加载失败，请重试" type="error" />
  <Table :data-source="rows" :columns="columns" row-key="id" :loading="loading" :pagination="false" :scroll="{ x: 900 }">
   <template #bodyCell="{column,record}">
    <template v-if="column.dataIndex==='operation'">{{names[record.operation]||record.operation}}</template>
    <template v-else-if="column.dataIndex==='mode'">{{record.mode==='mock'?'模拟测试':'待对接 / 正式回传'}}</template>
    <template v-else-if="column.dataIndex==='status'">{{statuses[record.status]||record.status}}</template>
    <template v-else-if="column.key==='action'"><Button v-if="record.status!=='CONFIRMED'" v-access:code="['trade:order:update']" :disabled="busy" @click="retry(record.id)">记录重试</Button></template>
   </template>
  </Table>
  <details v-access:code="['trade:order:update']" class="mt-4">
   <summary>本地模拟库存初始化（正式环境禁止）</summary>
   <p class="my-2">只初始化 trade_erp_mock_stock；相同 SKU 不允许重复充值或覆盖。需要先开启本地模拟配置。</p>
   <InputNumber v-model:value="skuId" :min="1" :precision="0" placeholder="商城 SKU ID" class="mr-2" />
   <InputNumber v-model:value="quantity" :min="0" :max="100000000" :precision="0" placeholder="测试数量" class="mr-2" />
   <Button :loading="busy" @click="seed">初始化测试库存</Button>
  </details>
 </section>
</template>
