import { request } from './http'
import type { Identifier } from '@/types/mall'
import type { KefuCapability, KefuHeartbeat, KefuMessages, KefuScope, KefuSend, KefuSendResult, KefuSession, KefuThread } from '@/types/kefu-chat'
const base = '/promotion/kefu-chat', headers = { 'x-silent-error': true }
const path = (agent: boolean, value: string) => `${base}/${agent ? 'agent/' : ''}${value}`
export const kefuChatApi = {
  capability: () => request<KefuCapability>({ url: `${base}/capability`, scope: 'browse', headers }),
  open: (data: KefuScope) => request<KefuThread>({ url: `${base}/open`, method: 'POST', data, headers }),
  thread: (sessionId: string, agent = false) => request<KefuThread>({ url: path(agent, 'thread'), params: { sessionId }, headers }),
  messages: (sessionId: string, afterId: Identifier, agent = false) => request<KefuMessages>({ url: path(agent, 'messages'), params: { sessionId, afterId, limit: 50 }, headers }),
  send: (data: KefuSend, agent = false) => request<KefuSendResult>({ url: path(agent, 'send'), method: 'POST', data, headers }),
  read: (sessionId: string, messageId: Identifier, agent = false) => request<KefuThread>({ url: path(agent, 'read'), method: 'POST', data: { sessionId, messageId }, headers }),
  human: (sessionId: string, version: number) => request<KefuThread>({ url: `${base}/human`, method: 'POST', data: { sessionId, version }, headers }),
  workbench: (params: KefuScope & { state: string; page: number; size: number }) => request<KefuThread[]>({ url: `${base}/agent/workbench`, params, headers }),
  heartbeat: (data: KefuScope & { online: boolean }) => request<KefuHeartbeat>({ url: `${base}/agent/heartbeat`, method: 'POST', data, headers }),
  claim: (sessionId: string, version: number) => request<KefuSession>({ url: '/promotion/kefu-scope/claim', method: 'POST', data: { sessionId, version }, headers }),
  close: (sessionId: string, version: number) => request<KefuSession>({ url: '/promotion/kefu-scope/close', method: 'POST', data: { sessionId, version }, headers }),
}
