/**
 * 标书模板字段字典与取值解析（方案2）。
 *
 * 真实标书模板（如《答辩第一组参考-投标标书》）里的待填处是「标签 + 下划线留白」，
 * 没有 SDT 内容控件 / ${} 占位符。这里用「锚点文字」定位留白：
 *   - after（默认）：留白紧跟锚点之后，如「投标人名称：____」
 *   - before：留白在锚点之前，如「工期 ____ 日历天」锚定「日历天」
 *   - occurrence：同名锚点出现多次时取第几次（从 1 起），如「RMB￥」出现 3 次
 *
 * 字段名用「标准名」，与 parse 阶段事实卡 / 企业事实名对齐（normalizeName 后互包含即可命中）。
 */
import { diffDays, normalizeName, toChineseAmount } from './valueRules.js';

export interface FactValue {
  name: string;
  /** 取值；可能自带单位（如「1286.50 万元」），由解析函数按需换算 */
  value: string;
  unit?: string;
}

export interface TemplateFieldSpec {
  /** 标准字段名（用于取企业事实与回填多处） */
  name: string;
  anchors: Array<{ text: string; dir?: 'after' | 'before'; occurrence?: number }>;
}

/** 模板里需要回填的字段（按锚点定位，顺序即处理顺序） */
export const TEMPLATE_FIELDS: readonly TemplateFieldSpec[] = [
  // —— 封面 ——
  { name: '招标编号', anchors: [{ text: '招标编号：' }] },
  { name: '标段号', anchors: [{ text: '标段号：' }] },
  // —— 一、投标函（内联填空）——
  { name: '招标编号', anchors: [{ text: '招标编号为' }] },
  { name: '投标报价大写', anchors: [{ text: '人民币(大写)' }] },
  { name: '投标报价小写', anchors: [{ text: 'RMB￥', occurrence: 1 }] },
  { name: '措施项目费', anchors: [{ text: '措施项目费' }] },
  { name: '安全文明施工措施费', anchors: [{ text: '安全文明施工措施费' }] },
  { name: '工期天数', anchors: [{ text: '日历天', dir: 'before' }] },
  { name: '项目经理', anchors: [{ text: '委派' }] },
  // —— 二、法定代表人身份证明 ——
  { name: '投标人名称', anchors: [{ text: '投标人名称：' }, { text: '投标人：' }] },
  { name: '单位性质', anchors: [{ text: '单位性质：' }] },
  { name: '地址', anchors: [{ text: '地址：' }] },
  { name: '成立时间', anchors: [{ text: '成立时间：' }] },
  { name: '经营期限', anchors: [{ text: '经营期限：' }] },
  // 第 1 个「姓名：」是投标函附录的项目经理行（ｘｘｘ），法人证明里的姓名是第 2 个
  { name: '法定代表人', anchors: [{ text: '姓名：', occurrence: 2 }] },
  // —— 二、法定代表人身份证明（企业信息，续）——
  { name: '注册资本', anchors: [{ text: '注册资本' }] },
  { name: '经营范围', anchors: [{ text: '经营范围' }] },
  { name: '营业执照编号', anchors: [{ text: '统一社会信用代码' }, { text: '营业执照注册号' }, { text: '营业执照编号' }] },
  { name: '安全生产许可证编号', anchors: [{ text: '安全生产许可证' }] },
  // —— 五、投标报价计价表 ——
  { name: '工程名称', anchors: [{ text: '工程名称：', occurrence: 1 }] },
  { name: '投标报价小写', anchors: [{ text: '投标总价（小写）：' }] },
  { name: '投标报价大写', anchors: [{ text: '（大写）：' }] },
];

/**
 * 把事实卡 / 招标解析得到的事实，解析为模板字段的标准取值。
 * 只做确定性换算（COPY/DERIVE），不经模型：报价大写走央行大写规则、工期走开工/竣工日期推算。
 */
export interface DroppedField {
  field: string;
  value: string;
  reason: string;
}

export interface ResolvedFieldValues {
  values: Map<string, string>;
  /** 类型校验未通过、拒绝回填的取值（留白待人工复核，好过填错） */
  dropped: DroppedField[];
}

/**
 * 类型校验：取值必须满足字段自身的形态，否则拒绝回填。
 * 2026-09-22 事故：事实卡只有「法定代表人 = 张建国」，取值层把姓名灌进
 * 性别/年龄/职务/身份证号，交付件出现「性别：张建国」「身份证号码：张建国」。
 */
const TYPE_CHECKS: Record<string, (value: string) => string | null> = {
  法定代表人性别: (v) => (/^[男女]$/.test(v) ? null : '性别应为「男」或「女」'),
  法定代表人年龄: (v) =>
    /^\d{1,3}$/.test(v) && Number(v) >= 18 && Number(v) <= 100 ? null : '年龄应为 18–100 的数字',
  法定代表人身份证号: (v) => (/^(\d{15}|\d{17}[\dXx])$/.test(v) ? null : '身份证号应为 15 或 18 位'),
  委托代理人身份证号: (v) => (/^(\d{15}|\d{17}[\dXx])$/.test(v) ? null : '身份证号应为 15 或 18 位'),
  电话: (v) =>
    /^[\d+\-()（）\s]{5,20}$/.test(v) && (v.match(/\d/g)?.length ?? 0) >= 5
      ? null
      : '电话/传真应为主要由数字组成的联系方式',
  传真: (v) =>
    /^[\d+\-()（）\s]{5,20}$/.test(v) && (v.match(/\d/g)?.length ?? 0) >= 5
      ? null
      : '电话/传真应为主要由数字组成的联系方式',
  邮政编码: (v) => (/^\d{6}$/.test(v) ? null : '邮编应为 6 位数字'),
  法定代表人电话: (v) =>
    /^[\d+\-()（）\s]{5,20}$/.test(v) && (v.match(/\d/g)?.length ?? 0) >= 5
      ? null
      : '电话/传真应为主要由数字组成的联系方式',
  技术负责人电话: (v) =>
    /^[\d+\-()（）\s]{5,20}$/.test(v) && (v.match(/\d/g)?.length ?? 0) >= 5
      ? null
      : '电话/传真应为主要由数字组成的联系方式',
  成立时间: (v) => (/年\s*\d{1,2}\s*月/.test(v) || /(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})/.test(v) ? null : '成立时间应为完整日期'),
  签署日期: (v) => (/年\s*\d{1,2}\s*月/.test(v) || /(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})/.test(v) ? null : '签署日期应为完整日期'),
  注册资本: (v) => (/^\d+(\.\d+)?$/.test(v) ? null : '注册资本应为数字'),
  注册资金: (v) => (/^\d+(\.\d+)?$/.test(v) ? null : '注册资金应为数字'),
  项目经理人数: (v) => (/^\d{1,4}$/.test(v) ? null : '人数应为数字'),
  技工人数: (v) => (/^\d{1,4}$/.test(v) ? null : '人数应为数字'),
  高级职称人员数: (v) => (/^\d{1,4}$/.test(v) ? null : '人数应为数字'),
  中级职称人员数: (v) => (/^\d{1,4}$/.test(v) ? null : '人数应为数字'),
  初级职称人员数: (v) => (/^\d{1,4}$/.test(v) ? null : '人数应为数字'),
};

/** 人员子字段：取值不得与人员姓名相同（防止「姓名灌进所有子字段」的模型幻觉） */
const PERSON_SUB_FIELDS = new Set([
  '法定代表人性别',
  '法定代表人年龄',
  '法定代表人职务',
  '法定代表人身份证号',
  '法定代表人技术职称',
  '法定代表人电话',
]);

export function resolveFieldValues(
  facts: FactValue[],
  ctx: { projectName?: string; bidSectionCode?: string },
): ResolvedFieldValues {
  const index = new Map<string, FactValue>();
  for (const fact of facts) index.set(normalizeName(fact.name), fact);

  // 只做精确匹配（归一化后）。2026-09-22 事故根因是双向 includes 模糊匹配：
  // 事实键「法定代表人」是「法定代表人性别/年龄/职务/身份证号」的子串，姓名被灌进全部子字段。
  // 同义别名一律显式列在 put 的候选名单里，不再靠模糊兜底。
  const find = (...names: string[]): FactValue | undefined => {
    for (const name of names) {
      const key = normalizeName(name);
      const exact = index.get(key);
      if (exact) return exact;
    }
    return undefined;
  };

  const out = new Map<string, string>();
  const dropped: DroppedField[] = [];
  const legalRepName = find('法定代表人')?.value.trim();
  // 检索未命中时模型会输出「未找到/未命中」等占位值，不得作为事实回填进模板
  const put = (name: string, value: string | undefined): void => {
    if (!value || !value.trim() || out.has(name)) return;
    const trimmed = value.trim();
    if (/^(未找到|未命中|暂无|待补充|未在检索证据中)/.test(trimmed)) return;
    const check = TYPE_CHECKS[name];
    if (check) {
      const reason = check(trimmed);
      if (reason) {
        dropped.push({ field: name, value: trimmed, reason });
        return;
      }
    }
    if (PERSON_SUB_FIELDS.has(name) && legalRepName && trimmed === legalRepName) {
      dropped.push({ field: name, value: trimmed, reason: '取值与法定代表人姓名相同，疑似模型幻觉' });
      return;
    }
    out.set(name, trimmed);
  };

  put('投标人名称', find('企业名称', '公司名称', '投标人名称')?.value);
  put('工程名称', ctx.projectName);
  put('招标编号', find('招标编号')?.value);
  put('标段号', ctx.bidSectionCode);
  put('招标人', find('招标人', '招标单位', '建设单位', '业主')?.value);
  put('单位性质', find('单位性质', '企业性质')?.value);
  put('地址', find('注册地址', '地址')?.value);
  put('电话', find('联系电话', '电话')?.value);
  put('传真', find('传真')?.value);
  put('邮政编码', find('邮政编码', '邮编')?.value);
  put('成立时间', find('成立时间', '成立日期')?.value);
  put('经营期限', find('经营期限')?.value);
  put('法定代表人', find('法定代表人')?.value);
  put('法定代表人性别', find('法定代表人性别', '性别')?.value);
  put('法定代表人年龄', find('法定代表人年龄', '年龄')?.value);
  put('法定代表人职务', find('法定代表人职务', '职务')?.value);
  put('法定代表人身份证号', find('法定代表人身份证号', '身份证号')?.value);
  put('委托代理人姓名', find('委托代理人姓名', '委托代理人', '代理人')?.value);
  put('委托代理人身份证号', find('委托代理人身份证号')?.value);
  put('委托期限', find('委托期限', '授权期限')?.value);
  put('项目经理', find('项目经理姓名', '项目经理')?.value);
  put('措施项目费', find('措施项目费')?.value);
  put('安全文明施工措施费', find('安全文明施工措施费')?.value);
  put('履约担保金额', find('履约担保金额', '履约担保', '履约保证金')?.value);
  put('投标保证金金额', find('投标保证金金额', '投标保证金')?.value);
  put('投标担保类型', find('投标担保类型', '担保类型')?.value);
  put('质量标准', find('质量标准', '质量要求', '质量等级')?.value);
  put('补充说明', find('补充说明')?.value);
  put('造价编制人', find('造价编制人')?.value);
  put('签署日期', find('签署日期', '投标日期')?.value);
  put('营业执照编号', find('营业执照编号', '统一社会信用代码', '营业执照注册号')?.value);
  put('安全生产许可证编号', find('安全生产许可证编号', '安全生产许可证')?.value);
  put('经营范围', find('经营范围', '业务范围')?.value);
  const capital = find('注册资本', '注册资金');
  if (capital) {
    // 事实卡常为「5000 万元」，模板「注册资本 ____ 万元」只需数字部分（单位留在模板里）
    put('注册资本', capital.value.replace(/[^\d.]/g, ''));
    put('注册资金', capital.value.replace(/[^\d.]/g, ''));
  }

  // —— 八、资格审查资料 · 投标人基本情况表（company_*，2026-09-22 补映射）——
  put('注册地址', find('注册地址', '地址')?.value);
  put('联系人', find('联系人', '联系人姓名')?.value);
  put('网址', find('网址', '公司网址', '企业网址')?.value);
  put('组织结构', find('组织结构', '企业组织结构')?.value);
  put('法定代表人技术职称', find('法定代表人技术职称', '法定代表人职称')?.value);
  put('法定代表人电话', find('法定代表人电话', '法定代表人联系电话')?.value);
  put('技术负责人姓名', find('技术负责人姓名', '技术负责人')?.value);
  put('技术负责人技术职称', find('技术负责人技术职称', '技术负责人职称')?.value);
  put('技术负责人电话', find('技术负责人电话', '技术负责人联系电话')?.value);
  put('企业资质等级', find('企业资质等级', '资质等级')?.value);
  put('项目经理人数', find('项目经理人数', '项目经理数量')?.value);
  put('营业执照号', find('营业执照号', '营业执照注册号', '统一社会信用代码')?.value);
  put('高级职称人员数', find('高级职称人员数', '高级职称人数')?.value);
  put('中级职称人员数', find('中级职称人员数', '中级职称人数')?.value);
  put('初级职称人员数', find('初级职称人员数', '初级职称人数')?.value);
  put('技工人数', find('技工人数', '技术工人数')?.value);
  put('开户银行', find('开户银行', '开户行')?.value);
  put('账号', find('账号', '银行账号', '开户账号')?.value);
  put('经营范围备注', find('经营范围备注', '经营范围', '业务范围')?.value);
  // —— 投标函附录（误期/质量违约金限额）——
  put('误期赔偿限额', find('误期赔偿限额', '误期违约金额', '误期赔偿费')?.value);
  put('质量违约金限额', find('质量违约金限额', '质量违约金', '违约金限额')?.value);

  const price = find('投标报价', '投标总价', '报价');
  if (price) {
    const raw = price.value.replace(/[,\s]/g, '');
    const numeric = raw.replace(/[^\d.]/g, '');
    // 报价事实常以「万元」为单位，模板金额栏为「元」，做 ×10000 换算
    const toYuan = raw.includes('万') ? String(Number(numeric) * 10_000) : numeric;
    put('投标报价小写', toYuan);
    const upper = toChineseAmount(toYuan);
    if (upper) put('投标报价大写', upper);
  }

  const start = find('开工日期', '开始日期');
  const end = find('竣工日期', '结束日期', '完工日期');
  const days = start && end ? diffDays(start.value, end.value) : undefined;
  if (days !== undefined) {
    put('工期天数', String(days));
  } else {
    put('工期天数', find('工期', '工期天数', '日历天')?.value);
  }

  return { values: out, dropped };
}
