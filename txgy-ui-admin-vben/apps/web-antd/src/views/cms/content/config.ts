export type CmsApiType = 'material' | 'policy' | 'process' | 'quota' | 'standard';

export interface CmsRecord {
  id?: number;
  status?: string;
  type?: string;
  title?: string;
  content?: string;
  updatedAt?: string;
  attachmentUrls?: string[];
  sourceUrl?: string;
  [key: string]: any;
}

export interface CmsColumn {
  dataIndex: string;
  title: string;
  width?: number;
}

export interface CmsFormField {
  dataIndex: string;
  label: string;
  multiline?: boolean;
  options?: Array<{ label: string; value: string }>;
  required?: boolean;
  date?: boolean;
}

export interface CmsContentConfig {
  apiType: CmsApiType;
  columns: CmsColumn[];
  formFields: CmsFormField[];
  moduleName: string;
  pageTitle: string;
  records: CmsRecord[];
}

export interface CmsContentConfigMap {
  material: CmsContentConfig;
  policy: CmsContentConfig;
  process: CmsContentConfig;
  quota: CmsContentConfig;
  standard: CmsContentConfig;
}

const statusOptions = [
  { label: '已发布', value: '已发布' },
  { label: '待审核', value: '待审核' },
  { label: '已驳回', value: '已驳回' },
  { label: '草稿', value: '草稿' },
];

export const cmsContentConfigs: CmsContentConfigMap = {
  standard: {
    columns: [
      { dataIndex: 'standardNo', title: '标准编号', width: 160 },
      { dataIndex: 'name', title: '标准名称', width: 300 },
      { dataIndex: 'standardType', title: '标准类型', width: 120 },
      { dataIndex: 'industryField', title: '适用领域', width: 160 },
      { dataIndex: 'updatedAt', title: '更新时间', width: 120 },
      { dataIndex: 'views', title: '浏览量', width: 100 },
    ],
    formFields: [
      { dataIndex: 'standardNo', label: '标准编号' },
      { dataIndex: 'name', label: '标准名称' },
      {
        dataIndex: 'standardType',
        label: '标准类型',
        options: [
          { label: '国家标准', value: '国家标准' },
          { label: '行业标准', value: '行业标准' },
          { label: '地方标准', value: '地方标准' },
          { label: '团体标准', value: '团体标准' },
        ],
      },
      { dataIndex: 'industryField', label: '适用领域' },
      {
        dataIndex: 'status',
        label: '状态',
        options: statusOptions,
      },
      { dataIndex: 'keywords', label: '关键词', multiline: true },
    ],
    moduleName: '行业标准规范库',
    pageTitle: '标准规范库管理',
    records: [
      {
        id: 1,
        standardNo: 'GB/T 4272-2008',
        name: '设备及管道绝热技术通则',
        standardType: '国家标准',
        industryField: '石化/建筑/电力',
        status: '已发布',
        updatedAt: '2026-08-20',
        views: 12860,
        keywords: '绝热,保温,保冷,通则',
      },
      {
        id: 2,
        standardNo: 'GB 50264-2013',
        name: '工业设备及管道绝热工程设计规范',
        standardType: '国家标准',
        industryField: '工业/石化',
        status: '已发布',
        updatedAt: '2026-08-16',
        views: 9362,
        keywords: '工业绝热,设计,保温',
      },
      {
        id: 3,
        standardNo: 'SH/T 3010-2013',
        name: '石油化工设备和管道涂料防腐蚀设计规范',
        standardType: '行业标准',
        industryField: '石油化工',
        status: '已发布',
        updatedAt: '2026-08-12',
        views: 7185,
        keywords: '涂料,防腐蚀,石油化工',
      },
      {
        id: 4,
        standardNo: 'GB/T 8923.1-2011',
        name: '涂覆涂料前钢材表面处理 表面清洁度的目视评定',
        standardType: '国家标准',
        industryField: '防腐/钢结构',
        status: '待审核',
        updatedAt: '2026-09-01',
        views: 0,
        keywords: '钢材表面,清洁度,涂覆',
      },
      {
        id: 5,
        standardNo: 'SY/T 0407-2012',
        name: '涂装前钢材表面处理规范',
        standardType: '行业标准',
        industryField: '石油天然气',
        status: '已驳回',
        updatedAt: '2026-08-28',
        views: 0,
        keywords: '涂装,表面处理,油气管线',
      },
      {
        id: 6,
        standardNo: 'DBJ/T 13-145-2019',
        name: '建筑外墙外保温工程质量验收规程',
        standardType: '地方标准',
        industryField: '建筑',
        status: '草稿',
        updatedAt: '2026-08-30',
        views: 0,
        keywords: '外墙外保温,验收,建筑',
      },
    ],
  },
  material: {
    columns: [
      { dataIndex: 'name', title: '材料名称', width: 220 },
      { dataIndex: 'category', title: '材料大类', width: 140 },
      { dataIndex: 'tempRange', title: '适用温度', width: 130 },
      { dataIndex: 'conductivity', title: '导热系数', width: 130 },
      { dataIndex: 'combustionGrade', title: '燃烧等级', width: 110 },
      { dataIndex: 'updatedAt', title: '更新时间', width: 120 },
    ],
    formFields: [
      { dataIndex: 'name', label: '材料名称' },
      {
        dataIndex: 'category',
        label: '材料大类',
        options: [
          { label: '无机保温', value: '无机保温' },
          { label: '有机保温', value: '有机保温' },
          { label: '防腐涂料', value: '防腐涂料' },
          { label: '衬里材料', value: '衬里材料' },
        ],
      },
      { dataIndex: 'tempRange', label: '适用温度范围' },
      { dataIndex: 'conductivity', label: '导热系数' },
      {
        dataIndex: 'combustionGrade',
        label: '燃烧等级',
        options: [
          { label: 'A级', value: 'A级' },
          { label: 'B1级', value: 'B1级' },
          { label: 'B2级', value: 'B2级' },
        ],
      },
      { dataIndex: 'industryField', label: '适用领域' },
      {
        dataIndex: 'status',
        label: '状态',
        options: statusOptions,
      },
    ],
    moduleName: '材料参数数据库',
    pageTitle: '材料参数库管理',
    records: [
      {
        id: 1,
        name: '复合硅酸盐保温材料',
        category: '无机保温',
        tempRange: '-40~650°C',
        conductivity: '0.046 W/(m·K)',
        combustionGrade: 'A级',
        industryField: '石化/电力',
        status: '已发布',
        updatedAt: '2026-08-19',
      },
      {
        id: 2,
        name: '憎水岩棉板',
        category: '无机保温',
        tempRange: '-120~650°C',
        conductivity: '0.038 W/(m·K)',
        combustionGrade: 'A级',
        industryField: '建筑/工业',
        status: '已发布',
        updatedAt: '2026-08-15',
      },
      {
        id: 3,
        name: '聚氨酯硬泡保温管壳',
        category: '有机保温',
        tempRange: '-50~120°C',
        conductivity: '0.024 W/(m·K)',
        combustionGrade: 'B1级',
        industryField: '市政热力',
        status: '已发布',
        updatedAt: '2026-08-11',
      },
      {
        id: 4,
        name: '环氧玻璃鳞片防腐涂料',
        category: '防腐涂料',
        tempRange: '-20~150°C',
        conductivity: '---',
        combustionGrade: 'B1级',
        industryField: '石油化工',
        status: '待审核',
        updatedAt: '2026-09-01',
      },
      {
        id: 5,
        name: '酚醛树脂衬里材料',
        category: '衬里材料',
        tempRange: '-20~180°C',
        conductivity: '---',
        combustionGrade: 'B1级',
        industryField: '化工',
        status: '草稿',
        updatedAt: '2026-08-27',
      },
    ],
  },
  process: {
    columns: [
      { dataIndex: 'title', title: '工艺案例名称', width: 300 },
      { dataIndex: 'resourceType', title: '资源类型', width: 130 },
      { dataIndex: 'difficulty', title: '难度等级', width: 110 },
      { dataIndex: 'industryField', title: '适用领域', width: 150 },
      { dataIndex: 'updatedAt', title: '更新时间', width: 120 },
    ],
    formFields: [
      { dataIndex: 'title', label: '工艺案例名称' },
      {
        dataIndex: 'resourceType',
        label: '资源类型',
        options: [
          { label: '标准工序', value: '标准工序' },
          { label: '施工工法', value: '施工工法' },
          { label: '技术交底', value: '技术交底' },
          { label: '通病防治', value: '通病防治' },
          { label: '优秀案例', value: '优秀案例' },
          { label: '整改方案', value: '整改方案' },
        ],
      },
      {
        dataIndex: 'difficulty',
        label: '难度等级',
        options: [
          { label: '入门', value: '入门' },
          { label: '初级', value: '初级' },
          { label: '中级', value: '中级' },
          { label: '高级', value: '高级' },
          { label: '专家级', value: '专家级' },
        ],
      },
      { dataIndex: 'industryField', label: '适用领域' },
      {
        dataIndex: 'status',
        label: '状态',
        options: statusOptions,
      },
      { dataIndex: 'summary', label: '内容摘要', multiline: true },
    ],
    moduleName: '工艺工法与案例库',
    pageTitle: '工艺案例库管理',
    records: [
      {
        id: 1,
        title: '管道聚氨酯发泡保温施工工法',
        resourceType: '施工工法',
        difficulty: '高级',
        industryField: '市政热力',
        status: '已发布',
        updatedAt: '2026-08-18',
        summary: '覆盖发泡温度、模具合模、密度控制与养护工艺。',
      },
      {
        id: 2,
        title: '储罐内壁玻璃鳞片防腐衬里工法',
        resourceType: '施工工法',
        difficulty: '高级',
        industryField: '石油化工',
        status: '已发布',
        updatedAt: '2026-08-10',
        summary: '用于原油储罐、化学品储罐内壁防腐施工。',
      },
      {
        id: 3,
        title: '岩棉外保温系统节点做法技术交底',
        resourceType: '技术交底',
        difficulty: '中级',
        industryField: '建筑',
        status: '已发布',
        updatedAt: '2026-08-05',
        summary: '包含窗口、勒脚、变形缝和阳角节点做法。',
      },
      {
        id: 4,
        title: '深冷保冷层冷桥部位通病防治',
        resourceType: '通病防治',
        difficulty: '专家级',
        industryField: '海工/冷链',
        status: '待审核',
        updatedAt: '2026-09-01',
        summary: '分析深冷工况下冷桥形成原因并提出整改措施。',
      },
      {
        id: 5,
        title: '钢结构涂装除锈等级不合格整改案例',
        resourceType: '整改方案',
        difficulty: '中级',
        industryField: '钢结构',
        status: '草稿',
        updatedAt: '2026-08-26',
        summary: '针对 Sa2.5 除锈不达标的返工处理流程。',
      },
    ],
  },
  quota: {
    columns: [
      { dataIndex: 'title', title: '定额资料名称', width: 320 },
      { dataIndex: 'quotaType', title: '定额类型', width: 130 },
      { dataIndex: 'region', title: '适用地区', width: 130 },
      { dataIndex: 'publishDate', title: '发布日期', width: 120 },
      { dataIndex: 'updatedAt', title: '更新时间', width: 120 },
    ],
    formFields: [
      { dataIndex: 'title', label: '定额资料名称' },
      {
        dataIndex: 'quotaType',
        label: '定额类型',
        options: [
          { label: '施工定额', value: '施工定额' },
          { label: '预算定额', value: '预算定额' },
          { label: '概算定额', value: '概算定额' },
          { label: '人工单价', value: '人工单价' },
          { label: '材料市场价', value: '材料市场价' },
          { label: '规费标准', value: '规费标准' },
          { label: '资料模板', value: '资料模板' },
        ],
      },
      { dataIndex: 'region', label: '适用地区' },
      { dataIndex: 'publishDate', label: '发布日期' },
      {
        dataIndex: 'status',
        label: '状态',
        options: statusOptions,
      },
    ],
    moduleName: '资质定额资料库',
    pageTitle: '定额资料库管理',
    records: [
      {
        id: 1,
        title: '石油化工绝热工程预算定额（2026版）',
        quotaType: '预算定额',
        region: '全国',
        publishDate: '2026-05-01',
        status: '已发布',
        updatedAt: '2026-08-14',
      },
      {
        id: 2,
        title: '建筑防腐工程人工单价参考（华东）',
        quotaType: '人工单价',
        region: '华东',
        publishDate: '2026-07-01',
        status: '已发布',
        updatedAt: '2026-08-09',
      },
      {
        id: 3,
        title: '保温材料市场价月度汇总',
        quotaType: '材料市场价',
        region: '全国',
        publishDate: '2026-08-01',
        status: '已发布',
        updatedAt: '2026-08-03',
      },
      {
        id: 4,
        title: '安全文明施工措施费计取规则',
        quotaType: '规费标准',
        region: '华北',
        publishDate: '2026-08-15',
        status: '待审核',
        updatedAt: '2026-09-01',
      },
      {
        id: 5,
        title: '防腐保温施工资料报验模板包',
        quotaType: '资料模板',
        region: '全国',
        publishDate: '2026-08-20',
        status: '草稿',
        updatedAt: '2026-08-29',
      },
    ],
  },
  policy: {
    columns: [
      { dataIndex: 'title', title: '政策标题', width: 340 },
      { dataIndex: 'policyLevel', title: '政策级别', width: 120 },
      { dataIndex: 'department', title: '发布机构', width: 200 },
      { dataIndex: 'publishDate', title: '发布日期', width: 120 },
      { dataIndex: 'updatedAt', title: '更新时间', width: 120 },
    ],
    formFields: [
      { dataIndex: 'title', label: '政策标题' },
      {
        dataIndex: 'policyLevel',
        label: '政策级别',
        options: [
          { label: '国家', value: '国家' },
          { label: '省级', value: '省级' },
          { label: '市级', value: '市级' },
        ],
      },
      { dataIndex: 'department', label: '发布机构' },
      { dataIndex: 'category', label: '政策分类' },
      { dataIndex: 'publishDate', label: '发布日期' },
      {
        dataIndex: 'status',
        label: '状态',
        options: statusOptions,
      },
      { dataIndex: 'summary', label: '政策摘要', multiline: true },
    ],
    moduleName: '政策资讯库',
    pageTitle: '政策资讯管理',
    records: [
      {
        id: 1,
        title: '工业设备及管道绝热节能相关标准宣贯工作安排',
        policyLevel: '国家',
        department: '住房和城乡建设部',
        category: '标准宣贯',
        publishDate: '2026-08-10',
        status: '已发布',
        updatedAt: '2026-08-18',
        summary: '推动新版绝热标准在行业的落地应用。',
      },
      {
        id: 2,
        title: '石化行业安全环保专项检查通知',
        policyLevel: '国家',
        department: '应急管理部',
        category: '安全环保',
        publishDate: '2026-08-06',
        status: '已发布',
        updatedAt: '2026-08-15',
        summary: '要求重点企业开展防腐蚀与保温施工安全自查。',
      },
      {
        id: 3,
        title: '建筑节能保温工程质量提升行动方案',
        policyLevel: '省级',
        department: '省住房和城乡建设厅',
        category: '质量监管',
        publishDate: '2026-07-28',
        status: '已发布',
        updatedAt: '2026-08-08',
        summary: '强化外墙保温工程质量追溯和现场抽检。',
      },
      {
        id: 4,
        title: '绿色低碳建材推广应用政策解读',
        policyLevel: '国家',
        department: '工业和信息化部',
        category: '产业政策',
        publishDate: '2026-08-25',
        status: '待审核',
        updatedAt: '2026-09-01',
        summary: '支持节能保温材料进入绿色建材目录。',
      },
      {
        id: 5,
        title: '城市供热管网改造项目申报指引',
        policyLevel: '市级',
        department: '市城市管理局',
        category: '项目申报',
        publishDate: '2026-08-21',
        status: '草稿',
        updatedAt: '2026-08-30',
        summary: '指导供热企业开展老旧管网和保温改造申报。',
      },
    ],
  },
};
