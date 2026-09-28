/* 材料完备性：状态 / 结论的**单一文案来源**（2026-09-21 #3 抽公共件时建立）。
 *
 * 为什么单独抽出来：
 *   同一份 GET /bidding/enterprise-material/completeness 出参，两个页面各写了一份兜底文案，结果
 *   RISK 在两处叫法不同 —— 证书域写「有临近到期」、资质页写「部分缺失或临近到期」。
 *   RISK 的语义是 **有缺失 或 有临近到期**，「有临近到期」只说了后半句，使用者据此排整改会漏掉"缺失"。
 *   ⇒ 文案只留这一份，两个页面与共享表格组件全部 import；以后改文案只改这里。
 *
 * 口径（契约 §0.5 禁止硬编码枚举）：字典 material_result / material_status 优先，
 *   本文件只是**字典未注册时**的兜底，取值与后端 VO 注释逐字一致。 */

/** 完备性结论兜底：PASS 齐备 / RISK 部分缺失或临近到期 / FAIL 必备材料缺失 */
export const MATERIAL_RESULT_FALLBACK: Record<string, { color: string; label: string }> = {
  PASS: { color: 'green', label: '齐备' },
  RISK: { color: 'orange', label: '部分缺失或临近到期' },
  FAIL: { color: 'red', label: '必备材料缺失' },
};

/** 单项材料状态兜底：READY 齐备 / EXPIRING 临近到期 / MISSING 缺失 / EXPIRED 已过期 */
export const MATERIAL_STATUS_FALLBACK: Record<string, { color: string; label: string }> = {
  READY: { color: 'green', label: '齐备' },
  EXPIRING: { color: 'orange', label: '临近到期' },
  MISSING: { color: 'red', label: '缺失' },
  EXPIRED: { color: 'red', label: '已过期' },
};

/** 结论兜底（未知 code 原样显示，不编造中文） */
export function materialResultFallback(result?: string): { color: string; label: string } {
  const r = result ?? '';
  return MATERIAL_RESULT_FALLBACK[r] ?? { color: 'default', label: r };
}

/** 状态兜底（未知 code 原样显示） */
export function materialStatusFallback(status?: string): { color: string; label: string } {
  const s = status ?? '';
  return MATERIAL_STATUS_FALLBACK[s] ?? { color: 'default', label: s };
}
