/** 将旧版 WMS 接口的仓库类型编码报错转成可读的中文。 */
export function formatWmsErrorMessage(message: string): string {
  const type = message.match(/仓库类型必须为\s*(RAW_MATERIAL|FINISHED_GOODS)/)?.[1];
  if (!type) return message;
  const warehouse = type === 'RAW_MATERIAL' ? '原材料仓' : '成品仓';
  return `当前业务需要${warehouse}，请选择匹配的业务仓库`;
}
