import { requestClient } from '#/api/request';
export interface LiveRow {
 id:number;title:string;remark?:string;coverUrl?:string;lecturerName:string;lecturerBio?:string;lecturerExpertise?:string;
 planStartAt:string;planEndAt:string;creditHours:number;visibility:string;status:number;auditStatus:string;auditComment?:string;
 replayEnabled:number;replayAuditStatus:string;replayVideoUrl?:string;streamUrl?:string;booked:boolean;bookingCount:number;canManage:boolean;
 replayTask?:{status:string;issue?:string};tenantId:number;viewerCount?:number;averageSeconds?:number;quizRate?:number;homeworkRate?:number;
}
export interface Profile {id?:number;userId:number;name:string;organization:string;expertise:string;bio?:string;status?:string;enabled?:number;reviewComment?:string}
export interface LiveContext {userId:number;tenantId:number;teacher:boolean;enterprise:boolean;platform:boolean;profile?:Profile;config:{danmakuEnabled:boolean|number;micEnabled:boolean|number}}
export interface Assignment {id:number;title:string;content:string;dueAt:string;submissionId?:number;submissionContent?:string;score?:number;feedback?:string;reviewedAt?:string}
export interface Person {userId:number;tenantId:number;nickname:string;liveSeconds:number;replaySeconds:number;submitted:number;graded:number;assignmentCount:number;completed:boolean;requiredSeconds:number}
export interface Quiz {id:number;question:string;optionsJson:string;myAnswer?:number;myCorrect?:number;answerCount?:number;correctCount?:number}
export interface Notice {id:number;content:string;createTime:string;readAt?:string}
export const getLive=<T>(path:string,params:Record<string,unknown>={})=>requestClient.get<T>(`/training/live-center/${path}`,{params});
export const postLive=<T=boolean>(path:string,data:unknown)=>requestClient.post<T>(`/training/live-center/${path}`,data);
const labels:Record<string,string>={0:'未开播',1:'直播中',2:'已结束',pending:'待审核',approved:'已通过',rejected:'已驳回',awaiting_recording:'等待录制文件',ready:'已归档',failed:'录制异常'};
export const statusText=(value:string|number)=>labels[String(value)]??String(value);
export function exportCsv(name:string,headings:string[],rows:unknown[][]){
 const cell=(v:unknown)=>`"${String(v??'').replace(/^[=+@\-\t\r]/,"'$&").replaceAll('"','""')}"`;
 const a=document.createElement('a');a.href=URL.createObjectURL(new Blob(['\uFEFF',[headings,...rows].map(r=>r.map(cell).join(',')).join('\r\n')],{type:'text/csv;charset=utf-8'}));a.download=`${name}.csv`;a.click();setTimeout(()=>URL.revokeObjectURL(a.href),1000);
}
