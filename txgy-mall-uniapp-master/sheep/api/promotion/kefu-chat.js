import request from '@/sheep/request';
const call = (path, method = 'GET', data) =>
  request({
    url: `/promotion/kefu-chat/${path}`,
    method,
    data,
    custom: { auth: path !== 'capability', showLoading: false, showError: false },
  });
export default {
  capability: () => call('capability'),
  open: (data) => call('open', 'POST', data),
  thread: (sessionId) => call('thread', 'GET', { sessionId }),
  messages: (sessionId, afterId) => call('messages', 'GET', { sessionId, afterId, limit: 50 }),
  send: (data) => call('send', 'POST', data),
  human: (data) => call('human', 'POST', data),
  read: (data) => call('read', 'POST', data),
};
