export function materialTypeLabel(type?: string | null): string {
  if (!type) return '未设置';
  if (type === 'RAW_MATERIAL') return '原材料';
  if (type === 'FINISHED_GOODS') return '成品';
  return '未识别类型';
}
