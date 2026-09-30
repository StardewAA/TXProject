<script setup lang="ts">
import type { SystemUserApi } from '#/api/system/user';

import { onMounted, ref } from 'vue';

import { Button, Input, message, Modal, Select, Table, Tag } from 'ant-design-vue';

import { requestClient } from '#/api/request';
import { getSimpleUserList } from '#/api/system/user';
interface Application { id: number; memberId: number; identity: string; name: string; creditCode: string; contactName: string; contactMobile: string; address: string; introduction: string; status: number; licenseData?: string; auditRemark?: string; enterpriseId?: number; shopId?: number; }
const busy=ref(false); const page=ref(1); const rows=ref<Application[]>([]); const status=ref<number>(); const total=ref(0);
const adminUserId=ref<number>(); const adminUsers=ref<SystemUserApi.UserSimple[]>([]); const boundShopId=ref(''); const existingId=ref(''); const opened=ref(false); const remark=ref(''); const selected=ref<Application>();
const columns=[{title:'申请编号',dataIndex:'id'},{title:'会员编号',dataIndex:'memberId'},{title:'企业',dataIndex:'name'},{title:'申请身份',key:'identity'},{title:'审核状态',key:'status'},{title:'操作',key:'action'}];
async function load(){busy.value=true;try{const data=await requestClient.get<{list:Application[];total:number}>('/product/enterprise-application/page',{params:{pageNo:page.value,pageSize:10,status:status.value}});rows.value=data.list;total.value=data.total;}finally{busy.value=false;}}
async function detail(id:number){selected.value=await requestClient.get<Application>('/product/enterprise-application/get',{params:{id}});remark.value='';existingId.value='';adminUserId.value=undefined;boundShopId.value=String(selected.value.shopId||'');opened.value=true;}
async function audit(result:number){if(!selected.value || busy.value)return;if(!remark.value.trim())return void message.warning('请填写审核意见');if(existingId.value && !/^[1-9]\d*$/.test(existingId.value))return void message.warning('企业编号格式不正确');if(result===1&&selected.value.identity==='supplier'&&!adminUserId.value)return void message.warning('供应企业审核通过前，请选择要关联的启用后台经营账号');busy.value=true;try{await requestClient.put('/product/enterprise-application/audit',undefined,{params:{id:selected.value.id,status:result,remark:remark.value,existingEnterpriseId:existingId.value||undefined,adminUserId:result===1&&selected.value.identity==='supplier'?adminUserId.value:undefined}});message.success(result===1?(selected.value.identity==='supplier'?'认证通过，已关联企业店铺和所选后台经营账号，并写入企业/店铺授权':'认证通过，已开通企业采购身份'):'已驳回，申请人可修改重提');opened.value=false;await load();}finally{busy.value=false;}}
async function bindShop(){if(!selected.value || busy.value)return;if(!/^[1-9]\d*$/.test(boundShopId.value))return void message.warning('请填写有效店铺编号');busy.value=true;try{await requestClient.put('/product/enterprise-application/bind-shop',undefined,{params:{id:selected.value.id,shopId:boundShopId.value}});message.success('已绑定指定店铺，仅对该店铺授权');opened.value=false;await load();}finally{busy.value=false;}}
onMounted(async()=>{await Promise.all([load(),getSimpleUserList().then((users)=>{adminUsers.value=users.filter((user)=>!!user.id)}).catch((cause)=>{console.error('[enterprise-application] admin users failed',cause);adminUsers.value=[]})]);});
</script>
<template>
 <section class="mb-4 rounded bg-card p-4">
  <div class="mb-4 flex items-center gap-4"><h2>商城会员企业认证申请</h2><Select v-model:value="status" allow-clear placeholder="全部状态" style="width:140px" :options="[{label:'待审核',value:0},{label:'通过',value:1},{label:'驳回',value:2}]" @change="page = 1;load()" /><Button @click="load">刷新</Button></div>
  <p class="mb-4">企业入驻不会创建新租户。采购企业通过后开通 PC 采购成员关系；供应企业审核时必须选择本租户现有的启用后台账号，系统会关联企业、店铺及经营范围，不生成或展示默认密码。</p>
  <Table :columns="columns" :data-source="rows" row-key="id" :loading="busy" :pagination="{current:page,pageSize:10,total}" @change="p=>{page = p.current || 1;load()}">
   <template #bodyCell="{column,record}"><template v-if="column.key === 'identity'">{{ record.identity === 'supplier' ? '供应商' : '采购商' }}</template><Tag v-if="column.key === 'status'">{{ ['待审核','通过','驳回'][record.status] }}</Tag><Button v-if="column.key === 'action'" type="link" @click="detail(record.id)">查看 / 审核</Button></template>
  </Table>
  <Modal v-model:open="opened" title="企业注册及资质认证审核" :footer="null" width="780px" @after-close="selected = undefined">
   <template v-if="selected">
<h3>{{ selected.name }}</h3><p>信用代码：{{ selected.creditCode }}</p><p>联系人：{{ selected.contactName }} / {{ selected.contactMobile }}</p><p>地址：{{ selected.address }}</p><p>{{ selected.introduction }}</p><img v-if="selected.licenseData" :src="selected.licenseData" alt="私有营业执照" style="max-width:100%;max-height:400px;object-fit:contain" />
    <p v-if="selected.auditRemark">审核意见：{{ selected.auditRemark }}</p>
    <div v-if="selected.status === 1 && selected.identity === 'supplier'" class="my-4"><p>已关联店铺：{{ selected.shopId || '暂无' }}。店铺经营须使用独立后台账号并授予企业、店铺及操作权限；此处仅用于历史店铺关系修复，不会自动授予账号权限。</p><Input v-model:value="boundShopId" placeholder="本企业启用店铺编号" /><Button class="mt-2" :loading="busy" @click="bindShop">重新绑定店铺</Button></div>
    <div v-if="selected.status === 0" class="mt-4"><p>如企业档案已存在，核实该会员身份后填写企业编号；不填写则新建企业档案（信用代码重复时会拒绝）。</p><Input v-model:value="existingId" placeholder="已有企业编号（选填）" class="mb-4" /><div v-if="selected.identity === 'supplier'" class="mb-4"><p>选择本租户已创建且启用的后台经营账号；审核通过后会立即写入企业与店铺授权。</p><Select v-model:value="adminUserId" show-search option-filter-prop="label" placeholder="后台经营账号（必选）" style="width:100%" :options="adminUsers.map((user)=>({value:user.id,label:`${user.nickname}（${user.username} / ID ${user.id}）`}))" /><p v-if="!adminUsers.length" class="mt-2 text-warning">未读取到启用后台账号，请先在系统管理创建或启用账号后再审核。</p></div><Input.TextArea v-model:value="remark" :maxlength="500" placeholder="审核意见（必填）" /><div class="mt-4 flex gap-4"><Button type="primary" :loading="busy" @click="audit(1)">审核通过</Button><Button danger :loading="busy" @click="audit(2)">驳回</Button></div></div>
   </template>
  </Modal>
 </section>
</template>
