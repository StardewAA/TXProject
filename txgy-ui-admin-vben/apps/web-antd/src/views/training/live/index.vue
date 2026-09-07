<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { Page } from '@vben/common-ui';
import { Button, Card, Input, Modal, Select, Switch, Table, Tabs, TabPane, Tag, message } from 'ant-design-vue';
import type { LiveContext, LiveRow, Profile } from './api';
import { exportCsv, getLive, postLive, statusText } from './api';
import LiveHall from './live-hall.vue';
import RoomPanel from './room-panel.vue';
defineOptions({ name:'TrainingLive' });
const ctx=ref<LiveContext>();const tab=ref('hall');const scope=ref('mine');const keyword=ref('');const busy=ref(false);
const rooms=ref<LiveRow[]>([]);const profiles=ref<Profile[]>([]);const stats=ref<LiveRow[]>([]);
const dates=reactive({from:'',to:''});const config=reactive({danmakuEnabled:true,micEnabled:false});
const form=reactive({id:undefined as number|undefined,title:'',description:'',coverUrl:'',planStartAt:'',planEndAt:'',visibility:'enterprise',streamUrl:'',creditHours:1});const formOpen=ref(false);
const employees=ref<{id:number;nickname:string}[]>([]);const profile=reactive<Profile>({userId:0,name:'',organization:'',expertise:'',bio:''});const profileOpen=ref(false);const own=ref(false);
const review=reactive({path:'',id:0,status:'approved',comment:''});const reviewOpen=ref(false);
const panelRoom=ref<LiveRow>();const panelType=ref('');
const columns=[{title:'直播主题',dataIndex:'title',key:'title'},{title:'讲师',dataIndex:'lecturerName'},{title:'开播时间',dataIndex:'planStartAt',key:'planStartAt'},{title:'状态',key:'status'},{title:'预约',dataIndex:'bookingCount'},{title:'操作',key:'actions',width:390}];
const toInput=(value?:string|number)=>value?new Date(value).toISOString().slice(0,16):'';
const toMillis=(value:string)=>new Date(value).getTime();
const formatDate=(value?:string|number)=>value?new Date(value).toLocaleString('zh-CN',{hour12:false}):'-';
async function run(fn:()=>Promise<unknown>){if(busy.value)return;busy.value=true;try{await fn();}finally{busy.value=false;}}
async function load(){ctx.value=await getLive('context');config.danmakuEnabled=!!ctx.value!.config.danmakuEnabled;config.micEnabled=!!ctx.value!.config.micEnabled;
 if(['rooms','enterprise','assist'].includes(tab.value))rooms.value=await getLive('rooms',{scope:tab.value==='enterprise'?'enterprise':tab.value==='assist'?'control':scope.value,keyword:keyword.value});
 if(tab.value==='profiles')profiles.value=await getLive('profiles');
 if(tab.value==='stats')stats.value=await getLive('stats',{scope:scope.value,...dates});
}
async function changeTab(){scope.value='mine';rooms.value=[];await load();}
function editRoom(r?:LiveRow){Object.assign(form,{id:r?.id,title:r?.title??'',description:r?.remark??'',coverUrl:r?.coverUrl??'',planStartAt:toInput(r?.planStartAt),planEndAt:toInput(r?.planEndAt),visibility:r?.visibility??'enterprise',streamUrl:r?.streamUrl??'',creditHours:r?.creditHours??1});formOpen.value=true;}
async function saveRoom(){await postLive('room/save',{...form,planStartAt:toMillis(form.planStartAt),planEndAt:toMillis(form.planEndAt)});formOpen.value=false;message.success('已提交预告审核');await load();}
function action(path:string,r:LiveRow,title:string){Modal.confirm({title,content:r.title,onOk:()=>run(async()=>{await postLive(path,{id:r.id});await load();})});}
function reviewItem(path:string,id:number){Object.assign(review,{path,id,status:'approved',comment:''});reviewOpen.value=true;}
async function editProfile(isOwn=false){own.value=isOwn;if(!isOwn)employees.value=await getLive('employees');Object.assign(profile,isOwn&&ctx.value?.profile?ctx.value.profile:{userId:0,name:'',organization:'',expertise:'',bio:''});profileOpen.value=true;}
function panel(type:string,r:LiveRow){panelRoom.value=r;panelType.value=type;}
function download(){exportCsv('直播运营报表',['直播','开播时间','预约人数','观看人数','平均观看秒数','测验参与率%','作业完成率%'],stats.value.map(r=>[r.title,r.planStartAt,r.bookingCount,r.viewerCount,r.averageSeconds,r.quizRate,r.homeworkRate]));}
onMounted(load);
</script>
<template>
 <Page auto-content-height>
  <div class="heading"><div><small>云培训学院 · MODULE 06</small><h1>直播与讲师体系</h1><p>从开播预约到学习反馈，让每一场培训都有记录。</p></div><Tag color="blue">{{ctx?.platform?'平台运营':ctx?.teacher?'认证讲师':ctx?.enterprise?'企业管理员':'学员'}}</Tag></div>
  <Tabs v-model:active-key="tab" @change="changeTab">
   <TabPane key="hall" tab="直播大厅"><LiveHall/></TabPane>
   <TabPane v-if="ctx?.teacher" key="rooms" tab="直播管理">
    <div class="toolbar"><Input v-model:value="keyword" placeholder="搜索主题" style="width:230px" @press-enter="load"/><Select v-if="ctx?.platform" v-model:value="scope" :options="[{value:'mine',label:'我的直播'},{value:'platform',label:'全平台直播'}]" @change="load"/><Button @click="load">查询</Button><Button type="primary" @click="editRoom()">创建直播预告</Button><Button v-if="ctx?.profile" @click="editProfile(true)">我的讲师资料</Button></div>
    <Table :columns="columns" :data-source="rooms" row-key="id" :loading="busy" :scroll="{x:1100}"><template #bodyCell="{column,record}">
     <template v-if="column.key==='title'"><b>{{record.title}}</b><div class="muted">{{record.visibility==='public'?'公开直播':'企业内部'}} · {{record.creditHours}} 学时</div></template>
     <template v-if="column.key==='planStartAt'">{{formatDate(record.planStartAt)}}</template>
     <template v-if="column.key==='status'"><Tag :color="record.status===1?'red':'blue'">{{statusText(record.status)}}</Tag><div>{{statusText(record.auditStatus)}}</div><small>{{record.auditComment}}</small></template>
     <div v-if="column.key==='actions'" class="actions"><Button v-if="record.status===0" size="small" @click="editRoom(record)">编辑</Button><Button v-if="record.status===0 && record.auditStatus==='approved'" size="small" @click="panel('push',record)">推流配置</Button><Button v-if="record.status===0 && record.auditStatus==='approved'" size="small" type="primary" @click="action('room/start',record,'确认开播并发送预约提醒？')">开播</Button><Button v-if="record.status===1" size="small" danger @click="action('room/end',record,'结束直播并生成回放任务？')">结束</Button><Button size="small" @click="panel('homework',record)">作业</Button><Button size="small" @click="panel('control',record)">场控</Button><Button v-if="record.status===2" size="small" @click="panel('replay',record)">回放管理</Button><Button v-if="ctx?.platform" size="small" @click="reviewItem('room/review',record.id)">内容审核</Button></div>
    </template></Table>
   </TabPane>
   <TabPane v-if="ctx?.enterprise" key="enterprise" tab="企业学习管理"><div class="toolbar"><Button @click="load">刷新直播</Button><Button @click="editProfile()">推荐本企业讲师</Button></div><Table :data-source="rooms" row-key="id" :columns="[{title:'直播',dataIndex:'title'},{title:'开播时间',dataIndex:'planStartAt',key:'planStartAt'},{title:'操作',key:'actions'}]"><template #bodyCell="{column,record}"><template v-if="column.key==='planStartAt'">{{formatDate(record.planStartAt)}}</template><Button v-if="column.key==='actions'" @click="panel('employees',record)">员工报名与学习记录</Button></template></Table></TabPane>
   <TabPane v-if="ctx?.enterprise" key="profiles" tab="讲师入驻"><div class="toolbar"><Button type="primary" @click="editProfile()">推荐讲师</Button><Button @click="load">刷新</Button></div><Table :data-source="profiles" row-key="id" :columns="[{title:'姓名',dataIndex:'name'},{title:'所属单位',dataIndex:'organization'},{title:'擅长方向',dataIndex:'expertise'},{title:'状态',key:'status'},{title:'审核意见',dataIndex:'reviewComment'},{title:'操作',key:'actions'}]"><template #bodyCell="{column,record}"><template v-if="column.key==='status'">{{statusText(record.status)}} <Tag v-if="!record.enabled">已停用</Tag></template><div v-if="column.key==='actions' && ctx?.platform" class="actions"><Button v-if="record.status==='pending'" @click="reviewItem('profile/review',record.id)">审核</Button><Button v-if="record.status==='approved'" @click="run(async()=>{await postLive('profile/toggle',{id:record.id,enabled:!record.enabled});await load();})">{{record.enabled?'停用':'启用'}}</Button></div></template></Table></TabPane>
   <TabPane key="assist" tab="助教与场控"><Button @click="load">刷新负责的直播</Button><Table :data-source="rooms" row-key="id" :columns="[{title:'直播',dataIndex:'title'},{title:'操作',key:'actions'}]"><template #bodyCell="{column,record}"><div v-if="column.key==='actions'" class="actions"><Button @click="panel('control',record)">场控台</Button><Button v-if="record.status===2" @click="panel('replay',record)">回放归档</Button></div></template></Table></TabPane>
   <TabPane v-if="ctx?.teacher" key="stats" tab="运营数据"><div class="toolbar"><Select v-if="ctx?.platform" v-model:value="scope" :options="[{value:'mine',label:'我的直播'},{value:'platform',label:'全平台'}]"/><label>开始 <input v-model="dates.from" type="date"/></label><label>结束（不含） <input v-model="dates.to" type="date"/></label><Button @click="load">查询</Button><Button @click="download">导出 CSV</Button></div><div class="metrics"><Card title="直播场次">{{stats.length}}</Card><Card title="预约人次">{{stats.reduce((s,r)=>s+r.bookingCount,0)}}</Card><Card title="观看人次">{{stats.reduce((s,r)=>s+(r.viewerCount??0),0)}}</Card></div><Table :data-source="stats" row-key="id" :columns="[{title:'直播',dataIndex:'title'},{title:'预约人数',dataIndex:'bookingCount'},{title:'观看人数',dataIndex:'viewerCount'},{title:'平均观看秒数',dataIndex:'averageSeconds'},{title:'测验参与率 %',dataIndex:'quizRate'},{title:'作业完成率 %',dataIndex:'homeworkRate'}]"/></TabPane>
   <TabPane v-if="ctx?.platform" key="config" tab="平台规则"><Card title="全局直播参数"><div class="toolbar"><label>弹幕总开关 <Switch v-model:checked="config.danmakuEnabled"/></label><label>允许连麦 <Switch v-model:checked="config.micEnabled"/></label><Button type="primary" :loading="busy" @click="run(async()=>{await postLive('config',config);message.success('全平台规则已更新');})">保存规则</Button></div><p class="muted">全局规则对所有直播间生效。连麦能力需媒体服务支持。</p></Card></TabPane>
  </Tabs>
  <Modal v-model:open="formOpen" :title="form.id?'编辑直播预告':'创建直播预告'" :confirm-loading="busy" @ok="run(saveRoom)"><div class="form"><label>直播标题<Input v-model:value="form.title" :maxlength="200"/></label><label>简介<Input.TextArea v-model:value="form.description" :maxlength="2000"/></label><label>封面地址<Input v-model:value="form.coverUrl"/></label><label>开播时间<input v-model="form.planStartAt" type="datetime-local"/></label><label>结束时间<input v-model="form.planEndAt" type="datetime-local"/></label><label>可见范围<Select v-model:value="form.visibility" :options="[{value:'public',label:'公开可见'},{value:'enterprise',label:'仅本企业'}]"/></label><label>学时<input v-model.number="form.creditHours" type="number" min="0.01" max="24" step="0.01"/></label><label>外部播放地址（留空使用本地推流）<Input v-model:value="form.streamUrl"/></label></div></Modal>
  <Modal v-model:open="profileOpen" :title="own?'我的讲师资料':'推荐本企业讲师'" :confirm-loading="busy" @ok="run(async()=>{await postLive(own?'profile/update':'profile/apply',profile);profileOpen=false;await load();})"><div class="form"><label v-if="!own">本企业员工<Select v-model:value="profile.userId" show-search option-filter-prop="label" :options="employees.map(e=>({value:e.id,label:e.nickname}))"/></label><label>姓名<Input v-model:value="profile.name"/></label><label>所属单位<Input v-model:value="profile.organization"/></label><label>擅长方向<Input v-model:value="profile.expertise"/></label><label>个人简介<Input.TextArea v-model:value="profile.bio" :rows="4"/></label></div></Modal>
  <Modal v-model:open="reviewOpen" title="审核" :confirm-loading="busy" @ok="run(async()=>{await postLive(review.path,{id:review.id,status:review.status,comment:review.comment});reviewOpen=false;await load();})"><div class="form"><Select v-model:value="review.status" :options="[{value:'approved',label:'通过'},{value:'rejected',label:'驳回'}]"/><Input.TextArea v-model:value="review.comment" placeholder="审核意见（驳回必填）"/></div></Modal>
  <RoomPanel v-if="panelType && panelRoom" :room="panelRoom" :type="panelType" :platform="!!ctx?.platform" @close="panelType='';load()"/>
 </Page>
</template>
<style scoped>
.heading{display:flex;align-items:center;justify-content:space-between;padding:26px 28px;background:linear-gradient(120deg,#e9f1ff,#f4f9ff);border-radius:12px;margin-bottom:18px;color:#173354}.heading h1{font-size:26px;font-weight:650;margin:7px 0}.heading p,.muted{color:#738195;font-size:12px}.heading small{letter-spacing:2px;color:#4672b4}.toolbar,.actions{display:flex;align-items:center;gap:10px;flex-wrap:wrap;margin:14px 0}.actions{gap:6px}.form{display:grid;gap:14px}.form label{display:grid;gap:6px}.form input,input[type=date]{border:1px solid #d9d9d9;border-radius:6px;padding:7px;background:transparent}.metrics{display:grid;grid-template-columns:repeat(3,1fr);gap:16px;margin-bottom:20px}.metrics :deep(.ant-card-body){font-size:28px}
</style>
