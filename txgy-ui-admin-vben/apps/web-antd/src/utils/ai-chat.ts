/**
 * AI 对话通用工具
 *
 * 目前被两处助手复用（避免同一套逻辑各写一份）：
 *   - MES 生产助手悬浮面板：views/mes/home/modules/MesAiChatPanel.vue
 *   - 「AI首页 / 门户首页」助手：components/txgy-assistant/assistant-chat.vue
 */

/**
 * 剥离回复文本中的 <think> 思考过程标签（只展示最终回答）
 *
 * 推理模型（如 DeepSeek Reasoner）会先输出思考链，前端不应把思考过程展示给用户：
 *   - 含 <think> 且已闭合：只返回闭合标签之后的正文；
 *   - 含 <think> 但未闭合（思考中）：返回空串，由调用方展示"正在思考"；
 *   - 不含 <think>：原样返回，避免正常回答被误判为思考内容。
 */
export function stripThink(content: string): string {
  if (!content) return content;
  if (content.includes('<think>')) {
    if (content.includes('</think>')) {
      return content.slice(content.indexOf('</think>') + 8).replace(/^\s+/, '');
    }
    return '';
  }
  return content;
}

/**
 * 请求错误分类提示：把底层异常翻译成面向用户的友好提示。
 *
 * 判断顺序：鉴权失效（401/403）> 网络层异常（断网/超时/CORS）> 服务不可用（5xx/服务发现失败）
 * > SSE Content-Type 校验失败 > 后端业务异常（带 msg）。
 *
 * @param e        抛出的错误对象（axios 错误 / fetch 错误 / SSE 错误帧对象 / 任意异常）
 * @param fallback 无法识别类型时的兜底文案
 */
export function describeRequestError(e: any, fallback: string): string {
  const respData = e?.response?.data ?? e?.data ?? {};
  const httpStatus = e?.response?.status;
  const bizCode = respData?.code;
  // 1. 鉴权失效：HTTP 401/403 或业务 code 401/403（后端安全过滤器直接返回 JSON 时 HTTP 可能为 200）
  if (bizCode === 401 || httpStatus === 401 || bizCode === 403 || httpStatus === 403) {
    return '登录状态已失效，请重新登录后再试。';
  }
  const rawMsg = String(respData?.msg ?? e?.message ?? e?.msg ?? '').trim();
  // 2. 网络层错误特征：axios/fetch 在无业务响应体时抛出的报错信息
  if (
    !rawMsg ||
    /network\s*error|failed\s+to\s+fetch|load\s+failed|timeout|timed\s*out|econnaborted|econnrefused|econnreset|网络错误|网络异常|请求超时/i.test(
      rawMsg,
    )
  ) {
    return '网络异常，请检查网络连接后重试。';
  }
  // 3. 服务不可用：HTTP 5xx 或通用内部错误信息
  //    含网关服务发现失败（如 "Unable to find instance for ai-server"）——AI 服务重启/维护窗口期出现
  if (
    (httpStatus && httpStatus >= 500) ||
    /系统异常|服务不可用|internal server error|暂时无法服务|unable\s+to\s+find\s+instance|no\s+instances\s+available|loadbalancer/i.test(
      rawMsg,
    )
  ) {
    return 'AI 服务暂时不可用（可能正在维护或重启），请稍后重试。';
  }
  // 4. 流式接口的 Content-Type 校验失败：
  //    fetch-event-source 在收到 application/json 时会抛英文
  //    "Expected content-type to be text/event-stream, Actual: ..."，
  //    本质是后端流链路异常被 JSON 化，按约定话术转中文，不展示原始英文。
  if (/expected\s+content-type|text\/event-stream|event-stream/i.test(rawMsg)) {
    return '数据查询接口异常，请稍后重试。';
  }
  // 5. 后端业务错误（如"聊天角色不存在""找不到默认模型"）：透出后端真实原因
  return rawMsg || fallback;
}
