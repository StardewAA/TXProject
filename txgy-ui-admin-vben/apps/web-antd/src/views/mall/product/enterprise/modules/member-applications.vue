<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { Button, Input, Modal, Select, Table, Tag, message } from 'ant-design-vue';
import { requestClient } from '#/api/request';
interface Application { id: number; memberId: number; identity: string; name: string; creditCode: string; contactName: string; contactMobile: string; address: string; introduction: string; status: number; licenseData?: string; auditRemark?: string; enterpriseId?: number; shopId?: number; }
const rows=ref<Application[]>([]), total=ref(0), page=ref(1), status=ref<number>(), busy=ref(false);
const selected=ref<Application>(), opened=ref(false), remark=ref(''), existingId=ref(''), boundShopId=ref('');
const columns=[{title:'申请编号',dataIndex:'id'},{title:'会员编号',dataIndex:'memberId'},{title:'企业',dataIndex:'name'},{title:'申请身份',key:'identity'},{title:'审核状态',key:'status'},{title:'操作',key:'action'}];
async function load(){busy.value=true;try{const data=await requestClient.get<{list:Application[];total:number}>('/product/enterprise-application/page',{params:{pageNo:page.value,pageSize:10,status:status.value}});rows.value=data.list;total.value=data.total;}finally{busy.value=false;}}
async function detail(id:number){selected.value=await requestClient.get<Application>('/product/enterprise-application/get',{params:{id}});remark.value='';existingId.value='';boundShopId.value=String(selected.value.shopId||'');opened.value=true;}
async function audit(result:number){if(!selected.value || busy.value)return;if(!remark.value.trim())return void message.warning('请填写审核意见');if(existingId.value && !/^[1-9]\d*$/.test(existingId.value))return void message.warning('企业编号格式不正确');busy.value=true;try{await requestClient.put('/product/enterprise-application/audit',undefined,{params:{id:selected.value.id,status:result,remark:remark.value,existingEnterpriseId:existingId.value||undefined}});message.success(result===1?'认证通过，申请账号已关联为采购人员':'已驳回，申请人可修改重提');opened.value=false;await load();}finally{busy.value=false;}}
async function bindShop(){if(!selected.value || busy.value)return;if(!/^[1-9]\d*$/.test(boundShopId.value))return void message.warning('请填写有效店铺编号');busy.value=true;try{await requestClient.put('/product/enterprise-application/bind-shop',undefined,{params:{id:selected.value.id,shopId:boundShopId.value}});message.success('已绑定指定店铺，仅对该店铺授权');opened.value=false;await load();}finally{busy.value=false;}}
onMounted(load);
</script>
<template>
 <section class="mb-4 rounded bg-card p-4">
  <div class="mb-4 flex items-center gap-4"><h2>商城会员企业认证申请</h2><Select v-model:value="status" allow-clear placeholder="全部状态" style="width:140px" :options="[{label:'待审核',value:0},{label:'通过',value:1},{label:'驳回',value:2}]" @change="page=1;load()"/><Button @click="load">刷新</Button></div>
  <p class="mb-4">审核通过后关联企业采购人员，不创建新租户或后台账号。已有企业必须核实申请人身份后手动关联。</p>
  <Table :columns="columns" :data-source="rows" row-key="id" :loading="busy" :pagination="{current:page,pageSize:10,total}" @change="p=>{page=p.current||1;load()}">
   <template #bodyCell="{column,record}"><template v-if="column.key==='identity'">{{record.identity==='supplier'?'供应商':'采购商'}}</template><Tag v-if="column.key==='status'">{{['待审核','通过','驳回'][record.status]}}</Tag><Button v-if="column.key==='action'" type="link" @click="detail(record.id)">查看 / 审核</Button></template>
  </Table>
  <Modal v-model:open="opened" title="企业注册及资质认证审核" :footer="null" width="780px" @after-close="selected=undefined">
   <template v-if="selected"><h3>{{selected.name}}</h3><p>信用代码：{{selected.creditCode}}</p><p>联系人：{{selected.contactName}} / {{selected.contactMobile}}</p><p>地址：{{selected.address}}</p><p>{{selected.introduction}}</p><img v-if="selected.licenseData" :src="selected.licenseData" alt="私有营业执照" style="max-width:100%;max-height:400px;object-fit:contain"/>
    <p v-if="selected.auditRemark">审核意见：{{selected.auditRemark}}</p>
    <div v-if="selected.status===1 && selected.identity==='supplier'" class="my-4"><p>销售店铺授权（须具备店铺修改权限）：仅能绑定本企业已启用的店铺，企业审核不会自动授权全部店铺。</p><Input v-model:value="boundShopId" placeholder="明确授权的店铺编号"/><Button class="mt-2" :loading="busy" @click="bindShop">绑定店铺</Button></div>
    <div v-if="selected.status===0" class="mt-4"><p>如企业档案已存在，核实该会员身份后填写企业编号；不填写则新建企业档案（信用代码重复时会拒绝）。</p><Input v-model:value="existingId" placeholder="已有企业编号（选填）" class="mb-4"/><Input.TextArea v-model:value="remark" :maxlength="500" placeholder="审核意见（必填）"/><div class="mt-4 flex gap-4"><Button type="primary" :loading="busy" @click="audit(1)">审核通过</Button><Button danger :loading="busy" @click="audit(2)">驳回</Button></div></div>
   </template>
  </Modal>
 </section>
</template>
