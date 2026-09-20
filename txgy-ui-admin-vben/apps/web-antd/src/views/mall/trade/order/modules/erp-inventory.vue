<script setup lang="ts">
import { ref, watch } from 'vue';
import { Alert, Button, message, Table } from 'ant-design-vue';
import { getErpInventory, retryErpInventory } from '#/api/mall/trade/erp-inventory';
import type { ErpInventoryEntry } from '#/api/mall/trade/erp-inventory';
const props = defineProps<{ orderId: number }>();
const rows = ref<ErpInventoryEntry[]>([]), loading = ref(false), failed = ref(false), busy = ref(false);
const names: Record<string,string> = { RESERVE:'库存预占',RELEASE:'释放预占',OUTBOUND:'出库',RETURN:'退货入库' };
const statuses: Record<string,string> = { PENDING:'处理中',CONFIRMED:'已确认',REJECTED:'处理失败' };
const columns = [
 { title:'业务',dataIndex:'operation' },
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
 try{await retryErpInventory(id);message.info('已记录重试，请稍后刷新查看处理结果');await load();}
 finally{busy.value=false;}
}
watch(()=>props.orderId,load,{immediate:true});
</script>
<template>
 <section class="m-4 rounded border p-4">
  <h3 class="mb-3 font-bold">库存履约记录</h3>
  <Button class="my-3" :loading="loading" @click="load">刷新处理结果</Button>
  <Alert v-if="failed" message="库存履约记录加载失败，请重试" type="error" />
  <Table :data-source="rows" :columns="columns" row-key="id" :loading="loading" :pagination="false" :scroll="{ x: 900 }">
   <template #bodyCell="{column,record}">
    <template v-if="column.dataIndex==='operation'">{{names[record.operation]||record.operation}}</template>
    <template v-else-if="column.dataIndex==='status'">{{statuses[record.status]||record.status}}</template>
    <template v-else-if="column.key==='action'"><Button v-if="record.status!=='CONFIRMED'" v-access:code="['trade:order:update']" :disabled="busy" @click="retry(record.id)">记录重试</Button></template>
   </template>
  </Table>
 </section>
</template>
