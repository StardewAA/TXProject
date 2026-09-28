<script lang="ts" setup>
import {
  computed,
  nextTick,
  onMounted,
  onUnmounted,
  reactive,
  ref,
  watch,
} from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';
import { useUserStore } from '@vben/stores';

import { Skeleton } from 'ant-design-vue';

import { getTenantSimpleList } from '#/api/core/auth';
import {
  getPortalRecruitCompanyList,
  getPortalRecruitPostPage,
  getPortalRecruitPostTypeList,
  type HrmPortalRecruitApi,
} from '#/api/hrm/portal/recruit';
import { getRecruitPostTypeList } from '#/api/hrm/recruit/post/type';
import {
  getRecruitProfileName,
  getRecruitProfileResume,
  isRecruitProfileComplete,
  parseRecruitProfileName,
} from '#/api/hrm/portal/resume';
import { getAreaTree, type SystemAreaApi } from '#/api/system/area';
import { AreaCascader } from '#/components/area';

import RecruitProfile from '../profile/recruit.vue';
import CompanyDirectory from './components/company-directory.vue';
import JobLocationMap from './components/job-location-map.vue';
import RecruitTopbar from './components/recruit-topbar.vue';
import {
  createResumeToolRequest,
  type ResumeToolRequest,
} from './components/resume-tools';
import Messages from './messages.vue';
import OnlineResume from './online-resume.vue';
import RecruitProfileSetup from './profile-setup.vue';

const loading = ref(false);
const posts = ref<HrmPortalRecruitApi.Post[]>([]);
const HOME_POSTS_CACHE_KEY = 'portal-recruit-home-posts-v1';

function readHomePostsCache() {
  try {
    const cached = JSON.parse(localStorage.getItem(HOME_POSTS_CACHE_KEY) || 'null') as null | {
      posts?: HrmPortalRecruitApi.Post[];
    };
    return Array.isArray(cached?.posts) ? cached.posts : [];
  } catch {
    return [];
  }
}

function writeHomePostsCache(value: HrmPortalRecruitApi.Post[]) {
  try {
    localStorage.setItem(HOME_POSTS_CACHE_KEY, JSON.stringify({ posts: value }));
  } catch {
    // 缓存不可用时仍使用接口返回的数据，不影响首页展示。
  }
}

// 首页推荐区使用独立的无筛选岗位快照，避免职位搜索结果污染热招职位/热门企业。
const homePosts = ref<HrmPortalRecruitApi.Post[]>(readHomePostsCache());
const postTotal = ref(0);
const keyword = ref('');
const sortBy = ref<'latest' | 'salary'>('latest');
const salaryRange = ref<string>();
const postTypes = ref<HrmPortalRecruitApi.PostType[]>([]);
const selectablePostTypes = computed(() =>
  postTypes.value.filter((type) => type.parentId != null && type.parentId > 0).length
    ? postTypes.value.filter((type) => type.parentId != null && type.parentId > 0)
    : postTypes.value,
);
const fallbackHotJobNames = ['工程管理', '采购', '资料员', '行政人事', '项目经理', '防腐保温', '防腐工', '保温工'];
const tenantNames = ref<Record<number, string>>({});
const recruitAdCompanies = ref<HrmPortalRecruitApi.Company[]>([]);
const selectedPost = ref<HrmPortalRecruitApi.Post>();
/**
 * 左栏职位列表改为滚动加载，不再分页：
 * 首屏 10 条，之后每次触底再追加 10 条，直到取完全部。
 * 这里同时是接口的分页参数 —— pageNo 会被 loadMorePosts 递增。
 */
const JOB_LIST_STEP = 10;
/** 列表滚到底前多少像素开始预加载，避免用户看到明显的等待空档。 */
const JOB_LIST_PREFETCH_PX = 220;
const jobsListRef = ref<HTMLElement>();
const jobDetailPanelRef = ref<HTMLElement>();
const jobsPageRef = ref<HTMLElement>();
const jobsSearchBandRef = ref<HTMLElement>();
/**
 * 搜索 + 筛选整块吸顶后进入紧凑态。
 * 回弹时留 JOB_BAR_UNPIN_GAP 的滞后量：吸顶线正好卡在判定值上，
 * 没有滞后的话在临界点来回滚会看着像抖动。
 */
const JOB_BAR_UNPIN_GAP = 12;
const JOB_TOPBAR_FALLBACK = 60;
const jobsBarPinned = ref(false);
/** 导航条实测高度，滚动判定与 --jobs-topbar-h 都以它为准。 */
let jobsTopbarHeight = JOB_TOPBAR_FALLBACK;
const listLoadingMore = ref(false);
const listFinished = ref(false);
const postPage = reactive<HrmPortalRecruitApi.PostPageParams>({
  pageNo: 1,
  pageSize: JOB_LIST_STEP,
  postName: '',
  postTypeId: undefined,
  jobNature: undefined,
  areaId: undefined,
  workTime: undefined,
  educationRequire: undefined,
  salaryMin: undefined,
  salaryMax: undefined,
  salaryNegotiable: undefined,
});
const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
const profileDisplayName = ref('');
const profileChecked = ref(false);
const profileCompleted = ref(true);
const profileSetupOpen = ref(false);
type ProfileAction = {
  favoritePostId?: number;
  postId?: string;
  resumeAction?: 'ai' | 'edit' | 'upload' | 'version';
  resumeRoute?: 'PortalRecruitResumeAi' | 'PortalRecruitAttachmentBuilder';
  section?: 'messages' | 'resume' | 'user';
  targetRoute?: { name: 'PortalRecruitCompany' | 'PortalRecruitDetail'; query: Record<string, string> };
};
const pendingProfileAction = ref<ProfileAction>();
/**
 * 进入在线简历页后要执行的动作（例如打开附件上传弹窗）。
 * nonce 递增，简历页消费后回传 action-handled 清空，保证一次点击只触发一次。
 */
const pendingResumeAction = ref<ResumeToolRequest>();
let profileCheckPromise: Promise<boolean> | undefined;
const navUserName = computed(() => {
  const name =
    profileDisplayName.value ||
    userStore.userInfo?.nickname ||
    userStore.userInfo?.username ||
    '用户';
  return /^1\d{10}$/.test(name) ? `${name.slice(0, 3)}****${name.slice(-4)}` : name;
});
const favoritePostIds = ref<number[]>([]);
const favoriteCompanyIds = ref<number[]>([]);
type HomeIndustryKey =
  | 'administration'
  | 'engineering'
  | 'finance'
  | 'manufacturing'
  | 'procurement'
  | 'quality'
  | 'research'
  | 'sales'
  | 'tender'
  | 'cost';

const homeIndustries: Array<{ key: HomeIndustryKey; label: string; keywords: string[] }> = [
  { key: 'manufacturing', label: '生产制造', keywords: ['生产', '操作工', '防腐', '保温', '设备维修', '焊工', '铆工', '管工', '油漆工', '喷砂工', '普工', '力工'] },
  { key: 'engineering', label: '工程项目', keywords: ['项目', '施工', '工程', '技术员'] },
  { key: 'research', label: '技术研发', keywords: ['研发', '探测', '工程师', '技术负责人'] },
  { key: 'sales', label: '销售业务', keywords: ['销售', '市场', '业务'] },
  { key: 'administration', label: '行政人事', keywords: ['行政', '人事'] },
  { key: 'finance', label: '财务会计', keywords: ['财务', '会计', '出纳'] },
  { key: 'procurement', label: '采购物流', keywords: ['采购', '材料', '库管', '物流', '仓储'] },
  { key: 'quality', label: '质量安全', keywords: ['质检', '质量', '安全'] },
  { key: 'tender', label: '招标资料', keywords: ['招标', '投标', '资料'] },
  { key: 'cost', label: '造价预算', keywords: ['预算', '造价'] },
];
const selectedHomeIndustry = ref<'all' | HomeIndustryKey>('all');
const currentYear = new Date().getFullYear();
const cityOpportunities = [
  { name: '北京', english: 'Beijing', image: '/hrm/city/beijing.jpg' },
  { name: '上海', english: 'Shanghai', image: '/hrm/city/shanghai.jpg' },
  { name: '天津', english: 'Tianjin', image: '/hrm/city/tianjin.jpg' },
  { name: '西安', english: 'Xi’an', image: '/hrm/city/xian.jpg' },
  { name: '苏州', english: 'Suzhou', image: '/hrm/city/suzhou.jpg' },
  { name: '武汉', english: 'Wuhan', image: '/hrm/city/wuhan.jpg' },
  { name: '广州', english: 'Guangzhou', image: '/hrm/city/guangzhou.jpg' },
  { name: '深圳', english: 'Shenzhen', image: '/hrm/city/shenzhen.jpg' },
  { name: '合肥', english: 'Hefei', image: '/hrm/city/hefei.jpg' },
  { name: '重庆', english: 'Chongqing', image: '/hrm/city/chongqing.jpg' },
  { name: '济南', english: 'Jinan', image: '/hrm/city/jinan.jpg' },
  { name: '佛山', english: 'Foshan', image: '/hrm/city/foshan.jpg' },
  { name: '南京', english: 'Nanjing', image: '/hrm/city/nanjing.jpg' },
  { name: '郑州', english: 'Zhengzhou', image: '/hrm/city/zhengzhou.jpg' },
  { name: '青岛', english: 'Qingdao', image: '/hrm/city/qingdao.jpg' },
  { name: '杭州', english: 'Hangzhou', image: '/hrm/city/hangzhou.jpg' },
  { name: '厦门', english: 'Xiamen', image: '/hrm/city/xiamen.jpg' },
  { name: '长沙', english: 'Changsha', image: '/hrm/city/changsha.jpg' },
  { name: '东莞', english: 'Dongguan', image: '/hrm/city/dongguan.jpg' },
  { name: '昆明', english: 'Kunming', image: '/hrm/city/kunming.jpg' },
  { name: '南昌', english: 'Nanchang', image: '/hrm/city/nanchang.jpg' },
  { name: '石家庄', english: 'Shijiazhuang', image: '/hrm/city/shijiazhuang.jpg' },
  { name: '宁波', english: 'Ningbo', image: '/hrm/city/ningbo.jpg' },
  { name: '福州', english: 'Fuzhou', image: '/hrm/city/fuzhou.jpg' },
];
const cityOpportunityPage = ref(0);
const cityOpportunityPageCount = computed(() => Math.max(1, Math.ceil(cityOpportunities.length / 12)));
const visibleCityOpportunities = computed(() =>
  cityOpportunities.slice(cityOpportunityPage.value * 12, cityOpportunityPage.value * 12 + 12),
);

/**
 * 城市名 → 地区编号。
 *
 * 编号从地区树现查，不写死表 —— 24 个城市的行政区划代码是常识，但真要有
 * 调整，写死的表不会报错、只会静默查不到东西，排查起来反而是最费劲的那种。
 *
 * 直辖市的坑：北京/上海/天津/重庆在树里省、市两级同名（北京市 → 北京市 → 区）。
 * 挂到更深的那一级更贴合「城市」语义；实测两级查出来的条数一致。
 */
type AreaNode = SystemAreaApi.Area & { children?: AreaNode[] };
const cityAreaIds = ref<Record<string, number>>({});
let cityAreaIdsPromise: Promise<void> | undefined;

function buildCityAreaIds(tree: AreaNode[]) {
  const wanted = new Set(cityOpportunities.map((city) => city.name));
  const ids: Record<string, number> = {};
  const depths: Record<string, number> = {};
  const walk = (nodes: AreaNode[], depth: number) => {
    for (const node of nodes) {
      const matched = /^(.+)市$/.exec(node.name ?? '');
      const name = matched?.[1];
      if (name && wanted.has(name) && node.id != null && depth >= (depths[name] ?? 0)) {
        ids[name] = node.id;
        depths[name] = depth;
      }
      if (node.children?.length) walk(node.children, depth + 1);
    }
  };
  walk(tree, 1);
  return ids;
}

function loadCityAreaIds() {
  cityAreaIdsPromise ??= getAreaTree()
    .then((tree) => {
      cityAreaIds.value = buildCityAreaIds((tree ?? []) as AreaNode[]);
    })
    .catch((error) => {
      // 失败不抛出：点城市时会退化成「什么都不发生」，但页面其余部分照常
      console.warn('[PortalRecruit] 地区树加载失败，城市热招暂不可点击', error);
    });
  return cityAreaIdsPromise;
}
const hoveredJobCategory = ref<string>();
const jobCategoryHovering = ref(false);
const recruitAdIndex = ref(0);
let recruitAdTimer: number | undefined;
const recruitAdImages = [
  '/hrm/ads/recruit-1.png',
  '/hrm/ads/recruit-5.png',
  '/hrm/ads/recruit-3.png',
  '/hrm/ads/recruit-4.png',
  '/hrm/ads/recruit-2.png',
];
const recruitAdCompanyNames: Record<string, string> = {
  '/hrm/ads/recruit-1.png': '天信管业科技集团有限公司',
  '/hrm/ads/recruit-2.png': '西北恒泰新材料有限公司',
  '/hrm/ads/recruit-3.png': '秦川化工产业园发展有限公司',
  '/hrm/ads/recruit-4.png': '延河热力集团有限公司',
  '/hrm/ads/recruit-5.png': '西安欧鹏互联科技有限公司',
};
type RecruitAdTheme = {
  color: string;
  deep: string;
  foreground: string;
  rgb: string;
  soft: string;
};

// 主题色按海报顶部视觉主色预配置，避免生产环境图片跨域时 canvas 取色失败。
const recruitAdThemes: Record<string, RecruitAdTheme> = {
  '/hrm/ads/recruit-1.png': { color: '#2a91d7', rgb: '42 145 215', deep: '#0b4a88', soft: '#d8efff', foreground: '#fff' },
  '/hrm/ads/recruit-5.png': { color: '#275a9c', rgb: '39 90 156', deep: '#0b2c58', soft: '#d7e8fb', foreground: '#fff' },
  '/hrm/ads/recruit-3.png': { color: '#197fd2', rgb: '25 127 210', deep: '#0a3e91', soft: '#d4edff', foreground: '#fff' },
  '/hrm/ads/recruit-4.png': { color: '#258fe4', rgb: '37 143 228', deep: '#163f9b', soft: '#d6eaff', foreground: '#fff' },
  '/hrm/ads/recruit-2.png': { color: '#2d83cc', rgb: '45 131 204', deep: '#164c87', soft: '#e0f0ff', foreground: '#fff' },
};
const currentRecruitAdImage = computed(
  () => recruitAdImages[recruitAdIndex.value] || recruitAdImages[0] || '',
);
const currentRecruitAdTheme = computed<RecruitAdTheme>(
  () => recruitAdThemes[currentRecruitAdImage.value] || recruitAdThemes[recruitAdImages[0]!]!,
);
const recruitAdThemeStyle = computed(() => ({
  '--banner-color': currentRecruitAdTheme.value.color,
  '--theme-rgb': currentRecruitAdTheme.value.rgb,
  '--banner-color-deep': currentRecruitAdTheme.value.deep,
  '--banner-color-soft': currentRecruitAdTheme.value.soft,
  '--banner-foreground': currentRecruitAdTheme.value.foreground,
}));
const jobCategoryDefinitions = [
  { name: '施工技能类（一线操作岗）', keywords: ['防腐', '喷砂', '抛丸', '油漆', '喷涂', '涂装', '衬里', '衬胶', '玻璃钢', '阴极保护', '热喷涂', '化学清洗', '保温', '保冷', '发泡', '缠绕', '管工', '铆工', '焊工', '起重工', '架子工', '电工', '仪表工', '钳工', '瓦工', '钢筋工', '木工', '混凝土', '抹灰', '防水', '生产线', '操作工', '缝纫', '裁剪', '包装', '普工', '力工', '清理', '杂工', '小工'] },
  { name: '工程技术类', keywords: ['工程师', '技术员', '技术负责人', '方案工程师', '设计师', 'CAD', 'BIM', '研发', '配方', '技术转化'] },
  { name: '项目管理类', keywords: ['项目经理', '项目副经理', '项目总工', '执行经理', '施工经理', '施工员', '施工队长', '工长', '班组长', '调度员', '测量员', '预算员', '造价员', '商务经理', '合同管理员', '结算员', '签证', '安全员', '安全经理', '安全总监', 'HSE', '消防', '监理'] },
  { name: '质量检验类', keywords: ['质检', '检测', '试验', '无损', '理化', '涂层检测', '计量', '监测'] },
  { name: '生产制造类', keywords: ['生产部', '车间主任', '生产计划', '工艺工程师', '生产质检', '设备工程师', '机械设备', '设备维修', '机修', '维修电工', '自动化', '仪表工程师', '实验员', '化验员', '调色'] },
  { name: '采购与供应链类', keywords: ['采购', '询价', '供应商', '材料员', '库管', '物流', '仓储', '叉车', '发货', '司机'] },
  { name: '市场营销类', keywords: ['市场', '销售', '客户经理', '区域经理', '大客户', '渠道', '投标', '标书', '资质', '证照', '品牌', '客服', '售后'] },
  { name: '职能管理类', keywords: ['财务', '会计', '出纳', '成本', '税务', '审计', '融资', '行政', '人事', 'HRBP', '招聘', '薪酬', '前台', '法务', '食堂', '保洁', '保安', '宿舍'] },
  { name: '国际业务类', keywords: ['海外', '驻外', '国际', '国别', '翻译', '英语', '俄语', '法语', '葡萄牙语', '阿拉伯语', '报关', '进出口'] },
  { name: '培训生/储备类', keywords: ['培训生', '储备干部', '实习生', '定向培养生', '学徒工'] },
];
const jobCategoryGroups = computed(() =>
  jobCategoryDefinitions.map((category) => ({
    ...category,
    posts: displayPosts.value
      .filter((post) =>
        category.keywords.some((keyword) =>
          [post.postName, post.postTypeName].filter(Boolean).join('').includes(keyword),
        ),
      )
      .slice(0, 18),
  })),
);
const activeJobCategoryGroup = computed(() =>
  jobCategoryGroups.value.find((category) => category.name === hoveredJobCategory.value),
);
const activeJobCategoryLeaves = computed(() => {
  const root = postTypes.value.find((type) => type.name === hoveredJobCategory.value && !type.parentId);
  const children = root
    ? postTypes.value.filter((type) => type.parentId === root.id)
    : [];
  if (children.length) return children;
  const definition = jobCategoryDefinitions.find((category) => category.name === hoveredJobCategory.value);
  return (definition?.keywords || []).map((name, index) => ({ id: -(index + 1), name }));
});
const jobCategorySections: Record<string, Array<{ name: string; jobs: string[] }>> = {
  '一线施工与生产操作': [
    { name: '防腐施工', jobs: ['防腐工', '喷砂工', '油漆工', '喷涂工', '衬里工', '衬胶工', '玻璃钢手糊工', '阴极保护工', '热喷涂工', '打磨工'] },
    { name: '保温施工', jobs: ['保温工', '铁皮工/白铁工', '外护安装工', '岩棉/玻璃棉安装工', '聚氨酯发泡工', '喷涂发泡工', '缠绕工'] },
    { name: '管道与结构安装', jobs: ['管工', '铆工', '焊工', '起重工', '架子工', '电工', '仪表工', '钳工'] },
    { name: '土建配套', jobs: ['瓦工', '钢筋工', '木工', '混凝土工', '抹灰工'] },
    { name: '生产车间', jobs: ['保温管生产线操作工', '防腐管生产线操作工', '发泡机操作工', '挤出机操作工', '缠绕机操作工', '缝纫工', '裁剪工', '包装工'] },
    { name: '辅助', jobs: ['普工/力工', '清理工'] },
  ],
  '项目技术与管理人员': [
    { name: '项目决策', jobs: ['项目经理', '项目副经理', '项目总工', '执行经理'] },
    { name: '技术', jobs: ['技术负责人', '防腐工程师', '保温工程师', '焊接工程师', '管道工程师', '结构工程师', '方案工程师', '绘图员/CAD', 'BIM工程师'] },
    { name: '施工', jobs: ['施工员', '施工队长/工长', '班组长', '调度员', '测量员'] },
    { name: '商务', jobs: ['预算员/造价员', '商务经理', '合同管理员', '结算员'] },
    { name: '安全', jobs: ['安全员', '安全总监', 'HSE工程师', '消防管理员'] },
    { name: '质量', jobs: ['质量经理', '质检员', '试验/检测员', '无损检测员'] },
    { name: '资料', jobs: ['资料员', '档案管理员'] },
  ],
  '质量、检测与物资设备': [
    { name: '质量检测', jobs: ['质检员', '试验/检测员', '化验员', '计量员', '无损检测员', '理化检验员'] },
    { name: '物资采购', jobs: ['采购员', '采购经理', '询价员', '供应商管理', '材料员'] },
    { name: '仓储物流', jobs: ['库管员', '理货员', '叉车工', '行车工', '发货员', '司机', '押运员'] },
    { name: '设备管理', jobs: ['设备维修工', '机械设备管理员', '机修工', '维修电工', '车辆管理员'] },
  ],
  '市场、商务与行政职能': [
    { name: '市场销售', jobs: ['市场业务经理', '销售工程师', '客户经理', '区域经理', '渠道专员', '商务助理'] },
    { name: '投标资质', jobs: ['投标专员', '标书制作员', '商务标/技术标编制', '资质专员', '证照管理员'] },
    { name: '财务', jobs: ['财务会计', '出纳', '成本会计', '税务会计', '审计'] },
    { name: '人力行政', jobs: ['行政人事专员', 'HRBP', '招聘专员', '薪酬绩效', '行政专员', '前台', '法务', 'IT运维'] },
    { name: '后勤', jobs: ['食堂', '保洁', '保安', '宿舍管理员'] },
  ],
  '研发、设计与生产制造': [
    { name: '研发', jobs: ['防腐材料研发工程师', '保温材料研发工程师', '涂料研发工程师', '配方工程师', '实验员'] },
    { name: '设计', jobs: ['管道设计', '保温结构设计', '防腐方案设计', 'CAD绘图员'] },
    { name: '生产工艺', jobs: ['工艺工程师', '生产计划员', '车间主任', '班组长', '生产质检员'] },
    { name: '设备', jobs: ['设备工程师', '自动化工程师'] },
  ],
  '其他/校招培养': [{ name: '校招培养', jobs: ['防腐保温管理培训生', '储备干部', '实习生', '学徒工'] }],
};
const jobCategorySectionsV2: Record<string, Array<{ name: string; jobs: string[] }>> = {
  '施工技能类（一线操作岗）': [
    { name: '防腐蚀作业', jobs: ['防腐工', '喷砂/抛丸除锈工', '油漆工', '喷涂工', '涂装工', '刮涂工', '预处理工', '衬里工', '衬胶工', '玻璃钢手糊工', '耐蚀砖板衬里工', '耐蚀塑料工', '耐蚀纤维增强塑料工', '阴极保护工', '热喷涂工', '化学清洗工', '防腐保温工', '建筑防腐工', '打磨工'] },
    { name: '保温保冷作业', jobs: ['保温工', '管道保温工', '铁皮/白铁保温工', '外护安装工', '岩棉/玻璃棉安装工', '橡塑保温工', '气凝胶保温工', '聚氨酯发泡工', '喷涂发泡工', '缠绕工', '电厂筑炉保温工', '耐火被覆工', '保冷工', '防露工'] },
    { name: '管道与结构安装', jobs: ['管工', '铆工', '焊工', '起重工', '架子工', '电工', '仪表工', '钳工', '冷作钣金工'] },
    { name: '土建配套', jobs: ['瓦工', '钢筋工', '木工', '混凝土工', '抹灰工', '防水工'] },
    { name: '生产车间操作', jobs: ['保温管生产线操作工', '防腐管生产线操作工', '发泡机操作工', '挤出机操作工', '缠绕机操作工', '缝纫工', '裁剪工', '包装工'] },
    { name: '辅助工种', jobs: ['普工/力工', '清理工', '学徒工', '杂工', '小工/中工'] },
  ],
  '工程技术类': [
    { name: '防腐技术', jobs: ['防腐工程师', '防腐保温工程师', '涂层工程师', '阴极保护工程师', '材料工程师'] },
    { name: '保温技术', jobs: ['保温工程师', '热绝缘工程师', '保冷工程师'] },
    { name: '焊接与结构', jobs: ['焊接工程师', '管道工程师', '结构工程师'] },
    { name: '设计研发', jobs: ['防腐方案设计师', '保温结构设计师', 'CAD绘图员', 'BIM工程师', '涂料研发工程师', '保温材料研发工程师', '配方工程师'] },
    { name: '技术支持', jobs: ['技术员', '技术负责人', '方案工程师', '技术转化推广人员'] },
  ],
  '项目管理类': [
    { name: '项目决策', jobs: ['项目经理', '项目副经理', '项目总工', '执行经理', '施工经理'] },
    { name: '现场管理', jobs: ['施工员', '施工队长/工长', '班组长', '调度员', '测量员', '脚手架主管'] },
    { name: '商务管理', jobs: ['预算员/造价员', '商务经理', '合同管理员', '结算员', '签证变更专员'] },
    { name: '安全管理', jobs: ['安全员', '安全经理', '安全总监', 'HSE工程师', '消防管理员'] },
    { name: '监理咨询', jobs: ['防腐保温监理工程师', '防腐保温专业监理工程师', '安装监理工程师', '监理员'] },
  ],
  '质量检验类': [{ name: '质量检验', jobs: ['质检员', '防腐质检工程师', '试验/检测员', '无损检测员（RT/UT/MT/PT）', '理化检验员', '涂层检测员', '计量员', '防腐保温监测工', '防腐检测工'] }],
  '生产制造类': [
    { name: '生产管理', jobs: ['生产部经理', '车间主任', '生产计划员', '调度员', '工艺工程师', '生产质检员'] },
    { name: '设备管理', jobs: ['设备工程师', '机械设备管理员', '设备维修工', '机修工', '维修电工', '自动化工程师', '仪表工程师'] },
    { name: '研发', jobs: ['研发工程师', '实验员', '化验员', '涂料调色工程师'] },
  ],
  '采购与供应链类': [{ name: '采购与供应链', jobs: ['采购员', '采购经理', '询价员', '供应商管理工程师', '材料员', '库管员', '物流经理', '仓储主管', '叉车工', '发货员', '司机'] }],
  '市场营销类': [{ name: '销售', jobs: ['市场业务经理', '销售工程师', '客户经理', '区域经理', '大客户经理', '渠道专员', '防水建材销售经理', '涂料业务经理'] }, { name: '投标与资质', jobs: ['投标专员', '标书制作员', '资质专员', '证照管理员', '合同管理专员'] }, { name: '市场支持', jobs: ['市场调研员', '品牌推广专员', '销售内勤', '客服经理', '售后技术支持'] }],
  '职能管理类': [{ name: '财务', jobs: ['财务会计', '出纳', '成本会计', '税务会计', '审计专员', '融资专员'] }, { name: '人力行政', jobs: ['行政人事专员', 'HRBP', '招聘专员', '薪酬绩效专员', '行政专员', '前台', '法务专员'] }, { name: '后勤', jobs: ['食堂管理', '保洁', '保安', '宿舍管理员'] }],
  '国际业务类': [{ name: '国际业务', jobs: ['海外项目经理', '驻外防腐保温工程师', '国际业务经理', '国别代表', '翻译（英语/俄语/法语/葡萄牙语/阿拉伯语）', '进出口报关员', '国际物流专员'] }],
  '培训生/储备类': [{ name: '培训生与储备', jobs: ['防腐保温管理培训生', '储备干部', '实习生', '定向培养生'] }],
};
const activeJobCategorySections = computed(() => {
  const sections = jobCategorySectionsV2[hoveredJobCategory.value || ''];
  if (sections) return sections;
  return [{ name: '职位', jobs: activeJobCategoryLeaves.value.map((type) => type.name) }];
});
const showJobCategoryDetail = computed(
  () => Boolean(jobCategoryHovering.value && hoveredJobCategory.value),
);

/**
 * 筛选行的下拉改为悬停即展开，只允许一个同时打开。
 *
 * 地址（AreaCascader）不在其列：它是三级级联、面板大得多，扫过去就弹会误触，
 * 留着点击更稳。
 *
 * 开合都留了延迟 —— 没有延迟的话，光标横扫过一排筛选会一路闪开闪关。
 * 开延迟让「只是路过」不触发，关延迟让光标从触发器移向弹出层的那段空隙不算离开。
 */
type FilterDropdownKey =
  | 'education'
  | 'jobNature'
  | 'jobType'
  | 'salary'
  | 'sort'
  | 'workTime';
const HOVER_OPEN_DELAY = 90;
const HOVER_CLOSE_DELAY = 180;
const activeFilterKey = ref<FilterDropdownKey>();
let hoverOpenTimer: number | undefined;
let hoverCloseTimer: number | undefined;

function openFilterDropdown(key: FilterDropdownKey) {
  window.clearTimeout(hoverCloseTimer);
  if (activeFilterKey.value === key) return;
  window.clearTimeout(hoverOpenTimer);
  hoverOpenTimer = window.setTimeout(() => {
    activeFilterKey.value = key;
  }, HOVER_OPEN_DELAY);
}

function closeFilterDropdownSoon() {
  window.clearTimeout(hoverOpenTimer);
  window.clearTimeout(hoverCloseTimer);
  hoverCloseTimer = window.setTimeout(() => {
    activeFilterKey.value = undefined;
  }, HOVER_CLOSE_DELAY);
}

function closeFilterDropdownNow() {
  window.clearTimeout(hoverOpenTimer);
  window.clearTimeout(hoverCloseTimer);
  activeFilterKey.value = undefined;
}

/** 受控开合：antd 自己请求关（选中、点空白）时也要把状态收回来。 */
function handleFilterDropdownVisible(key: FilterDropdownKey, visible: boolean) {
  if (visible) {
    window.clearTimeout(hoverOpenTimer);
    window.clearTimeout(hoverCloseTimer);
    activeFilterKey.value = key;
  } else if (activeFilterKey.value === key) {
    activeFilterKey.value = undefined;
  }
}

/**
 * 光标落在触发器或弹出层上都算「还在这一项里」。
 * 弹出层挂在 body 下、不在工具栏子树里，光靠触发器的 mouseleave 会在
 * 光标下移到选项的瞬间把它关掉，所以这里在 document 上统一判。
 */
function handleFilterPointerMove(event: MouseEvent) {
  if (!activeFilterKey.value) return;
  const target = event.target as HTMLElement | null;
  if (!target?.closest) return;
  if (target.closest('.filter-control')) {
    // 停在别的筛选上时，交给那一项的 mouseenter 去接管
    window.clearTimeout(hoverCloseTimer);
    return;
  }
  if (target.closest('.jobs-filter-dropdown')) {
    window.clearTimeout(hoverCloseTimer);
    return;
  }
  closeFilterDropdownSoon();
}

// 职位类型两级下拉：分类结构复用首页的 jobCategorySectionsV2，但每个可点项
// 仍绑定后端真实的 postTypeId，确保筛选参数与接口契约不变。
const jobTypePanelOpen = computed({
  get: () => activeFilterKey.value === 'jobType',
  set: (open: boolean) => {
    if (open) {
      window.clearTimeout(hoverOpenTimer);
      window.clearTimeout(hoverCloseTimer);
      activeFilterKey.value = 'jobType';
    } else if (activeFilterKey.value === 'jobType') {
      activeFilterKey.value = undefined;
    }
  },
});
const activeJobTypeGroup = ref<string>();
// 后端个别子类型名与首页分类定义不同名，做一层别名归一，避免落到「其它」组。
const JOB_TYPE_NAME_ALIASES: Record<string, string> = {
  培训生与储备: '培训生/储备',
};
const jobTypeGroups = computed(() => {
  const childTypes = postTypes.value.filter(
    (type) => type.parentId != null && type.parentId > 0,
  );
  const byName = new Map(childTypes.map((type) => [type.name, type]));
  const groups = Object.entries(jobCategorySectionsV2)
    .map(([name, sections]) => ({
      name,
      items: sections
        .map(
          (section) =>
            byName.get(section.name) ??
            byName.get(JOB_TYPE_NAME_ALIASES[section.name] ?? ''),
        )
        .filter((type): type is HrmPortalRecruitApi.PostType => Boolean(type)),
    }))
    .filter((group) => group.items.length > 0);

  // 后端若存在首页分类未覆盖的子类型，单独归组，避免选项丢失。
  const covered = new Set(
    groups.flatMap((group) => group.items.map((item) => item.id)),
  );
  const rest = childTypes.filter((type) => !covered.has(type.id));
  if (rest.length > 0) {
    groups.push({ name: '其它', items: rest });
  }
  return groups;
});
const activeJobTypeItems = computed(
  () =>
    jobTypeGroups.value.find(
      (group) => group.name === activeJobTypeGroup.value,
    )?.items ??
    jobTypeGroups.value[0]?.items ??
    [],
);

/** 打开面板时把左侧定位到当前已选类型所属的大类。 */
watch(jobTypePanelOpen, (open) => {
  if (!open) return;
  const current = jobTypeGroups.value.find((group) =>
    group.items.some((item) => item.id === postPage.postTypeId),
  );
  activeJobTypeGroup.value = current?.name ?? jobTypeGroups.value[0]?.name;
});

function pickJobType(id: number) {
  postPage.postTypeId = id;
  closeFilterDropdownNow();
  refreshPosts();
}

const hotCompanies = computed(() => {
  const companyMap = new Map<
    number,
    { financingStage?: HrmPortalRecruitApi.Company['financingStage']; id: number; logo?: string; name: string; posts: HrmPortalRecruitApi.Post[]; scale?: string }
  >();
  for (const post of homePosts.value) {
    if (post.tenantId == null) continue;
    const current = companyMap.get(post.tenantId) || {
      id: post.tenantId,
      logo: post.companyLogo,
      name:
        post.companyShortName ||
        post.companyName ||
        tenantNames.value[post.tenantId] ||
        '行业企业',
      scale: post.companyScale,
      financingStage: post.companyFinancingStage,
      posts: [],
    };
    if (
      current.name === '行业企业' &&
      (post.companyShortName || post.companyName || tenantNames.value[post.tenantId])
    ) {
      current.name =
        post.companyShortName ||
        post.companyName ||
        tenantNames.value[post.tenantId] ||
        current.name;
    }
    current.posts.push(post);
    if (!current.logo && post.companyLogo) current.logo = post.companyLogo;
    if (!current.scale && post.companyScale) current.scale = post.companyScale;
    if (!current.financingStage && post.companyFinancingStage) current.financingStage = post.companyFinancingStage;
    if (current.name === '行业企业' && (post.companyShortName || post.companyName)) {
      current.name = post.companyShortName || post.companyName || current.name;
    }
    companyMap.set(post.tenantId, current);
  }
  return [...companyMap.values()]
    .sort((left, right) => right.posts.length - left.posts.length || left.name.localeCompare(right.name, 'zh-CN'))
});

const visibleHotCompanies = computed(() => hotCompanies.value.slice(0, 9));

const hotJobItems = computed(() => {
  const items: Array<{ name: string; id?: number }> = selectablePostTypes.value
    .slice(0, 8)
    .map((type) => ({ name: type.name, id: type.id }));
  for (const name of fallbackHotJobNames) {
    if (items.length >= 8) break;
    if (!items.some((item) => item.name === name)) items.push({ name, id: undefined });
  }
  return items.slice(0, 8);
});

function normalizePortalRecruitPostTypes(
  types: HrmPortalRecruitApi.PostType[],
) {
  const uniqueTypes = new Map<string, HrmPortalRecruitApi.PostType>();
  for (const type of types) {
    if (type.id != null && type.name && !uniqueTypes.has(type.name)) {
      uniqueTypes.set(type.name, type);
    }
  }
  return [...uniqueTypes.values()];
}

const showAllPosts = computed(() => route.query.view === 'list');
const requestedSection = computed<'companies' | 'home' | 'jobs' | 'messages' | 'resume' | 'user'>(() => {
  const section = Array.isArray(route.query.section) ? route.query.section[0] : route.query.section;
  if (section === 'companies' || section === 'jobs' || section === 'messages' || section === 'resume' || section === 'user') return section;
  return 'home';
});
const activeSection = computed(() => {
  if (!profileChecked.value) return 'home';
  if (!profileCompleted.value && isProfileActionSection(requestedSection.value)) return 'home';
  return requestedSection.value;
});

/**
 * 首页、职位、公司三个板块共用同一套富页脚。
 * 公共框架只在这三页之外才渲染它自己的页脚，这里给出同口径的开关：
 * 富页脚显示时，用 CSS 把公共页脚隐藏掉，避免两个页脚上下叠加。
 */
const showRecruitFooter = computed(
  () =>
    activeSection.value === 'home' ||
    activeSection.value === 'companies' ||
    activeSection.value === 'resume' ||
    activeSection.value === 'user',
);
const previewPosts = computed(() => displayPosts.value.slice(0, 9));
const homePreviewPosts = computed(() => {
  const result = homePosts.value.filter((post) => {
    if (selectedHomeIndustry.value === 'all') return true;
    return getHomeIndustry(post) === selectedHomeIndustry.value;
  });
  return result.slice(0, 9);
});

const jobNatureOptions = [
  { label: '不限', value: 'all' as const },
  { label: '全职', value: 1 as const },
  { label: '实习', value: 2 as const },
  { label: '兼职', value: 3 as const },
];

const sortOptions = [
  { label: '最新发布', value: 'latest' as const },
  { label: '薪资优先', value: 'salary' as const },
];

const workTimeOptions = [
  { label: '经验不限', value: 1 },
  { label: '1 年以下', value: 2 },
  { label: '1-3 年', value: 3 },
  { label: '3-5 年', value: 4 },
  { label: '5-10 年', value: 5 },
  { label: '10 年以上', value: 6 },
];

const educationOptions = [
  { label: '学历不限', value: 1 },
  { label: '高中及以上', value: 2 },
  { label: '大专及以上', value: 3 },
  { label: '本科及以上', value: 4 },
  { label: '硕士及以上', value: 5 },
  { label: '博士', value: 6 },
];

const salaryOptions = [
  { label: '薪资不限', value: 'all' },
  { label: '5K 以下', value: '0-5000' },
  { label: '5K-10K', value: '5000-10000' },
  { label: '10K-20K', value: '10000-20000' },
  { label: '20K 以上', value: '20000-' },
  { label: '面议', value: 'negotiable' },
];

const displayPosts = computed(() => {
  const result = [...posts.value];
  if (sortBy.value === 'salary') {
    result.sort((left, right) => {
      const leftSalary = left.maxSalary ?? left.minSalary ?? -1;
      const rightSalary = right.maxSalary ?? right.minSalary ?? -1;
      return rightSalary - leftSalary;
    });
  }
  return result;
});

const isSearchMode = computed(
  () => Boolean(
    keyword.value.trim() ||
      postPage.postTypeId ||
      postPage.jobNature ||
      postPage.areaId ||
      postPage.workTime ||
      postPage.educationRequire ||
      postPage.salaryMin ||
      postPage.salaryMax ||
      postPage.salaryNegotiable,
  ),
);

function formatLocation(post: HrmPortalRecruitApi.Post) {
  return (
    post.areaName || (post.areaId ? `地区 ${post.areaId}` : '工作地点待确认')
  );
}

/**
 * 详情条件串专用的紧凑地点。那一条要同时挤下薪资、经验、学历、人数、性质和
 * 最晚入职，「陕西省 西安市 雁塔区」这种省市区全称会把整串顶到第二行，
 * 换成主流招聘站的「西安·雁塔区」两段式正好收进一行。
 * 直辖市在 areaName 里会重复一段（北京市 北京市 东城区），先去重再截。
 */
function formatLocationCompact(post?: HrmPortalRecruitApi.Post) {
  if (!post) return '';
  const parts = (post.areaName || '')
    .split(/\s+/)
    .filter(Boolean)
    .filter((part, index, list) => part !== list[index - 1]);
  if (!parts.length) {
    return post.areaId ? `地区 ${post.areaId}` : '工作地点待确认';
  }
  if (parts.length === 1) return parts[0]!.replace(/[省市]$/, '');
  const district = parts[parts.length - 1]!;
  const city = parts[parts.length - 2]!.replace(/[省市]$/, '');
  return `${city}·${district}`;
}

function formatProvinceLocation(post?: HrmPortalRecruitApi.Post) {
  if (!post) return '工作地点待确认';
  const location = post.areaName?.replace(/\s+/g, '').trim();
  if (!location) return post.areaId ? `地区 ${post.areaId}` : '工作地点待确认';
  const municipality = location.match(/^(北京市|上海市|天津市|重庆市)/);
  if (municipality) return municipality[1];
  const autonomousRegion = location.match(/^(内蒙古自治区|广西壮族自治区|西藏自治区|宁夏回族自治区|新疆维吾尔自治区)/);
  if (autonomousRegion) return autonomousRegion[1];
  const province = location.match(/^.+?省/);
  return province?.[0] || location;
}

function formatHotCompanyPostLocation(post: HrmPortalRecruitApi.Post) {
  const area = post.areaName?.replace(/[\s,，]+/g, '').trim() || '';
  const address = post.workAddress?.replace(/[\s,，]+/g, '').trim() || '';
  const source = `${area}${address}`;
  const cityMatch = source.match(/([\u4e00-\u9fa5]{2,8}市)/);
  const city = cityMatch?.[1] || '';
  const afterCity = city ? source.slice(source.indexOf(city) + city.length) : source;
  const district = afterCity.match(/([\u4e00-\u9fa5]{2,8}(?:区|县|旗))/)?.[1] || '';
  if (city && district) return `${city}-${district}`;
  if (city) return city;
  return formatProvinceLocation(post) || '工作地点待确认';
}

function formatSalary(post: HrmPortalRecruitApi.Post) {
  if (post.salaryUnit === -1 || (post.minSalary ?? 0) < 0) return '面议';
  const min = post.minSalary;
  const max = post.maxSalary;
  if (min == null && max == null) return '薪资面议';
  const format = (value?: number) => (value == null ? '' : `${value / 1000}K`);
  if (min != null && max != null) return `${format(min)}-${format(max)}`;
  return min != null ? `${format(min)}起` : `${format(max)}以内`;
}

function formatWorkTime(value?: number) {
  return workTimeOptions.find((option) => option.value === value)?.label || '经验不限';
}

function formatJobNature(value?: number) {
  return jobNatureOptions.find((option) => option.value === value)?.label || '';
}

function formatEducation(value?: number) {
  return educationOptions.find((option) => option.value === value)?.label || '学历不限';
}

async function selectPostType(id?: number) {
  await router.push({
    name: 'PortalRecruit',
    query: {
      section: 'jobs',
      ...(id == null ? {} : { postTypeId: String(id) }),
    },
  });
  postPage.postTypeId = id;
  postPage.pageNo = 1;
  await loadPosts();
}

function selectHotJob(name: string, id?: number) {
  if (id != null) {
    void selectPostType(id);
    return;
  }
  keyword.value = name;
  handleSearch();
}

function syncPostTypeFromRoute() {
  const rawValue = Array.isArray(route.query.postTypeId)
    ? route.query.postTypeId[0]
    : route.query.postTypeId;
  const postTypeId = Number(rawValue);
  postPage.postTypeId = Number.isInteger(postTypeId) && postTypeId > 0
    ? postTypeId
    : undefined;
}

function getHomeIndustry(post: HrmPortalRecruitApi.Post): HomeIndustryKey | undefined {
  const searchableText = [post.postName, post.postTypeName]
    .filter(Boolean)
    .join('');
  return homeIndustries.find((industry) =>
    industry.keywords.some((keyword) => searchableText.includes(keyword)),
  )?.key;
}

/** 收 'all'：默认值就是 'all'，但它不在 homeIndustries 里，模板原本照这个数组
    铺胶囊 —— 于是首屏十颗全是未选态，而选过一次之后又没有回头路。 */
function selectHomeIndustry(industry: 'all' | HomeIndustryKey) {
  selectedHomeIndustry.value = industry;
}

/**
 * 点城市跳职位页。
 *
 * 原来是把城市名塞进 postPage.postName —— 那是**按职位名称搜索**，而城市
 * 存在 areaName 里。「北京」于是变成「搜索名称含北京的职位」，实测 24 个
 * 城市全部落空（点完搜索框里留着「北京」，结果 0 条）。
 *
 * 改走地区筛选：写 areaId，由后端按地区树向下聚合 —— 实测 陕西省 196 =
 * 榆林 100 + 西安 11 + …，市级、省级编号都能用。
 */
async function selectCityOpportunity(city: string) {
  // 树还没到就等它，而不是静默不做声 —— 点一下没反应比跳错更让人困惑
  await loadCityAreaIds();
  const areaId = cityAreaIds.value[city];
  if (!areaId) return;
  clearPostFilters();
  postPage.areaId = areaId;
  void router.push({ name: 'PortalRecruit', query: { section: 'jobs' } });
  void loadPosts();
}

function shiftCityOpportunities(direction: number) {
  const pageCount = cityOpportunityPageCount.value;
  cityOpportunityPage.value =
    (cityOpportunityPage.value + direction + pageCount) % pageCount;
}

function setJobCategory(category?: string) {
  hoveredJobCategory.value = category;
}

function selectCategoryLeaf(type: { id: number; name: string }) {
  if (type.id > 0) {
    void selectPostType(type.id);
    return;
  }
  keyword.value = type.name;
  handleSearch();
}

/**
 * 首页行业卡片里的职位，同样新开标签页。
 * 这里也去掉了资料门：和 openPostDetailPage / openHomeCompany 一个道理 ——
 * 那道门是 await 出来的，之后 window.open 会被浏览器当非用户手势拦掉；
 * 而资料是否完善详情页自己在 onMounted 里就会判定。
 */
function openCategoryPost(post: HrmPortalRecruitApi.Post) {
  openPostDetailPage(post);
}

/**
 * 去独立的职位详情页（recruit/detail?id=…），新开一个浏览器标签页。
 *
 * 开新页之后详情页不必再自带返回入口：关掉标签页就行，原来的列表页还停在
 * 原位，滚动位置和选中的职位都不丢。
 *
 * 必须在这个点击处理函数里同步调用 window.open —— 一旦先 await（比如走
 * requestProfileAction 那道资料门），浏览器就当成非用户手势，把弹窗拦掉。
 * 资料是否完善由详情页自己在 onMounted 里判定（它的 ensureRecruitProfile）。
 */
function openPostDetailPage(post: HrmPortalRecruitApi.Post) {
  const { href } = router.resolve({
    name: 'PortalRecruitDetail',
    query: { id: String(post.id) },
  });
  window.open(href, '_blank', 'noopener,noreferrer');
}

function openHomePostCompany(post: HrmPortalRecruitApi.Post) {
  if (post.tenantId == null) return;
  openHomeCompany({ id: post.tenantId });
}

/**
 * 企业主页同样新开标签页，理由和 openPostDetailPage 一样。
 *
 * 这里去掉了原来的 requestProfileAction 资料门：那道门是 await 出来的，
 * 之后调 window.open 会被浏览器当成非用户手势拦掉；而 company.vue 自己在
 * onMounted 里就会调 ensureRecruitProfile 判定，资料不全时由它弹自己的弹窗，
 * 入口再拦一道本来就重复。
 */
function openHomeCompany(company: { id: number }) {
  const { href } = router.resolve({
    name: 'PortalRecruitCompany',
    query: { tenantId: String(company.id) },
  });
  window.open(href, '_blank', 'noopener,noreferrer');
}

function normalizeRecruitCompanyName(name?: string) {
  return (name || '').replace(/[\s\u3000]/g, '').trim();
}

async function openRecruitAdCompany(imagePath: string) {
  const companyName = recruitAdCompanyNames[imagePath];
  if (!companyName) return;

  const normalizedTarget = normalizeRecruitCompanyName(companyName);
  if (!normalizedTarget) return;

  if (!recruitAdCompanies.value.length) {
    try {
      recruitAdCompanies.value = await getPortalRecruitCompanyList();
    } catch (error) {
      console.warn('[PortalRecruit] 加载轮播企业失败', error);
    }
  }

  const company = recruitAdCompanies.value.find((item) => {
    const names = [item.name, item.shortName].map(normalizeRecruitCompanyName).filter(Boolean);
    return names.some(
      (name) => name === normalizedTarget || name.includes(normalizedTarget) || normalizedTarget.includes(name),
    );
  });
  if (company) {
    openHomeCompany({ id: company.tenantId ?? company.id });
    return;
  }

  const hotCompany = hotCompanies.value.find((item) => {
    const name = normalizeRecruitCompanyName(item.name);
    return Boolean(name) && (name === normalizedTarget || name.includes(normalizedTarget) || normalizedTarget.includes(name));
  });
  if (hotCompany) {
    openHomeCompany(hotCompany);
    return;
  }

  const post = posts.value.find((item) => {
    const name = normalizeRecruitCompanyName(item.companyShortName || item.companyName);
    return Boolean(name) && (name === normalizedTarget || name.includes(normalizedTarget) || normalizedTarget.includes(name));
  });
  if (post?.tenantId != null) openHomeCompany({ id: post.tenantId });
}

function isProfileActionSection(section: string): section is 'messages' | 'resume' | 'user' {
  return ['messages', 'resume', 'user'].includes(section);
}

async function checkRecruitProfile() {
  if (profileChecked.value) return profileCompleted.value;
  if (!profileCheckPromise) {
    profileCheckPromise = getRecruitProfileResume()
      .then((resume) => {
        profileCompleted.value = isRecruitProfileComplete(resume);
        const name = parseRecruitProfileName(resume?.content);
        if (name) profileDisplayName.value = name;
        return profileCompleted.value;
      })
      .catch((error) => {
        console.warn('[PortalRecruit] 检查求职资料失败', error);
        profileCompleted.value = true;
        return true;
      })
      .finally(() => {
        profileChecked.value = true;
      });
  }
  return profileCheckPromise;
}

function applyFavorite(postId: number) {
  favoritePostIds.value = favoritePostIds.value.includes(postId)
    ? favoritePostIds.value.filter((id) => id !== postId)
    : [...favoritePostIds.value, postId];
  localStorage.setItem('portal-recruit-favorites', JSON.stringify(favoritePostIds.value));
}

function applyCompanyFavorite(companyId: number) {
  favoriteCompanyIds.value = favoriteCompanyIds.value.includes(companyId)
    ? favoriteCompanyIds.value.filter((id) => id !== companyId)
    : [...favoriteCompanyIds.value, companyId];
  localStorage.setItem(
    'portal-recruit-company-favorites',
    JSON.stringify(favoriteCompanyIds.value),
  );
}

function runPendingProfileAction() {
  const action = pendingProfileAction.value;
  pendingProfileAction.value = undefined;
  if (!action) return;
  if (action.favoritePostId != null) {
    applyFavorite(action.favoritePostId);
    return;
  }
  if (action.resumeRoute) {
    void router.push({ name: action.resumeRoute });
    return;
  }
  if (action.targetRoute) {
    void router.push(action.targetRoute);
    return;
  }
  if (action.section) {
    if (action.resumeAction) {
      pendingResumeAction.value = createResumeToolRequest(action.resumeAction);
    }
    void router.push({
      name: 'PortalRecruit',
      query: {
        section: action.section,
        ...(action.postId ? { postId: action.postId } : {}),
      },
    });
  }
}

async function requestProfileAction(action: ProfileAction) {
  pendingProfileAction.value = action;
  if (await checkRecruitProfile()) {
    runPendingProfileAction();
    return;
  }
  profileSetupOpen.value = true;
}

function handleProfileSetupCompleted() {
  profileCompleted.value = true;
  profileChecked.value = true;
  profileSetupOpen.value = false;
  void getRecruitProfileName().then((name) => {
    if (name) profileDisplayName.value = name;
  });
  runPendingProfileAction();
}

function cancelProfileSetup() {
  profileSetupOpen.value = false;
  pendingProfileAction.value = undefined;
}

function openGatedSection(section: 'messages' | 'resume' | 'user', postId?: string) {
  void requestProfileAction({ section, postId });
}

function openResumeEditor() {
  void requestProfileAction({ section: 'resume', resumeAction: 'edit' });
}

function openResumeUpload() {
  void requestProfileAction({ section: 'resume', resumeAction: 'upload' });
}

function openResumeVersions() {
  void requestProfileAction({ section: 'resume', resumeAction: 'version' });
}

function openResumeAi() {
  void requestProfileAction({ resumeRoute: 'PortalRecruitResumeAi' });
}

function openResumeGenerator() {
  void requestProfileAction({ resumeRoute: 'PortalRecruitAttachmentBuilder' });
}

function handleResumeActionHandled() {
  pendingResumeAction.value = undefined;
}

/** 离开在线简历页时清掉未消费的动作，避免下次进入简历页时意外弹出上传弹窗 */
watch(activeSection, (section, previous) => {
  if (section !== 'resume') pendingResumeAction.value = undefined;
  if (section !== previous && section === 'home') {
    clearPostFilters();
    void loadHomePosts();
  } else if (section !== previous && section === 'jobs') {
    void loadPosts();
  }
});

function openChat(post: HrmPortalRecruitApi.Post) {
  openGatedSection('messages', String(post.id));
}

function selectPost(post: HrmPortalRecruitApi.Post) {
  selectedPost.value = post;
  // 新岗位从头看起，否则会停在上一份职位的阅读位置
  void nextTick(() => jobDetailPanelRef.value?.scrollTo({ top: 0 }));
}

/* ── 右栏职位详情 ─────────────────────────────────────────────
   卡片分两层：上半是企业行 / 标题行 / 条款带，构成固定的「岗位速览」；
   下半 .job-detail-panel 是唯一滚动区，描述、职位信息、工作地址一页排完。
   分页签会把内容切断，还让短描述显得空；常驻速览 + 单一滚动区
   既保证了关键信息随时可见，滚动时也不会丢失上下文。 */

/** 薪资之外的可比较字段，与左栏列表保持同一套口径。 */
/**
 * 企业行的副标题。行业 / 规模 / 融资阶段都属于「企业」这一维度，
 * 归在企业行里；三者全空才落到占位文案。
 */
const detailCompanyMeta = computed(() => {
  const post = selectedPost.value;
  if (!post) return '';
  return (
    [post.companyIndustry, post.companyScale, post.companyFinancingStage]
      .filter(Boolean)
      .join(' · ') || '企业信息待完善'
  );
});

/**
 * 标题行下方的条件串 —— 求职者扫读这一页的主路径。
 *
 * 只放填充率高、且彼此不重复的字段。按全量 261 条真实岗位实测：
 * 经验 / 学历 / 工作性质 各 261，地区 256，招聘人数 249，最晚入职 228。
 * 最晚入职仍然「有值才追加」—— 它缺席时留白比摆一个「不限」更干净。
 * 与之相对，上一版把「融资阶段」摆在常驻条款带上，而它 261 条里 0 条有值。
 */
/**
 * 条件串里的入职截止日只给「月-日」。整串长度是卡着算的，带上年份会多出
 * 五十来像素、正好把最后一项顶到第二行；同年内不加年份不会有歧义，
 * 跨年了再退回完整年月日。
 */
function formatEntryDeadline(value?: number | string) {
  const full = formatLatestEntryTime(value);
  if (!full) return '';
  const currentYear = String(new Date().getFullYear());
  return full.startsWith(currentYear)
    ? `${full.slice(5)} 前入职`
    : `${full} 前入职`;
}

const detailMetaItems = computed(() => {
  const post = selectedPost.value;
  if (!post) return [];
  const deadline = formatEntryDeadline(post.latestEntryTime);
  return [
    formatLocationCompact(post),
    formatWorkTime(post.workTime),
    formatEducation(post.educationRequire),
    post.recruitNum ? `招 ${post.recruitNum} 人` : '',
    formatJobNature(post.jobNature),
    deadline,
  ].filter(Boolean);
});

/**
 * 加载失败的图片地址。
 *
 * 企业 logo 走的是本站接口（相对路径），基本都能出来；但招聘方头像是外链，
 * 实测全站 9 条有头像的岗位用的是同一个 `http://test.txgy.iocoder.cn/...`，
 * 指向测试域名、当前环境取不到。img 加载失败时浏览器会留一个破图图标加
 * alt 文字，比干脆不显示还难看，所以记下失败过的地址，退回文字方块。
 */
const brokenImages = ref<Set<string>>(new Set());

function markImageBroken(src?: string) {
  if (!src || brokenImages.value.has(src)) return;
  brokenImages.value = new Set(brokenImages.value).add(src);
}

/** 有地址、且没失败过，才交给 <img> 渲染 */
function canShowImage(src?: string) {
  return Boolean(src) && !brokenImages.value.has(src!);
}

/**
 * 招聘方 —— 这一页的签名元素。
 *
 * ownerUserName 是全站唯一 50/50 全量填充的「人」字段，而这一页最终要促成的
 * 就是一次人和人的对话；把「对面是谁」摆出来，上方那个「立即沟通」才落得到实处。
 * 头像只有 1/50，退回姓氏方块（与沟通页同一套做法）。
 */
const detailRecruiter = computed(() => {
  const post = selectedPost.value;
  if (!post?.ownerUserName) return undefined;
  return { name: post.ownerUserName, avatar: post.ownerUserAvatar || '' };
});

/** 描述里的 -、•、1. 开头的行按列表项渲染，中文 JD 大多是这个写法。 */
const detailDescriptionLines = computed(() => {
  const raw = selectedPost.value?.description?.trim();
  if (!raw) return [];
  return raw
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((text) => ({
      bullet: /^[-•·*]\s*/.test(text) || /^\d+\s*[.、)]\s*/.test(text),
      text: text.replace(/^[-•·*]\s*/, ''),
    }));
});

/** 地图要的是完整地址，左栏展示用的是地区名，两者不能共用。 */
function formatWorkAddress(post?: HrmPortalRecruitApi.Post) {
  if (!post) return '';
  return post.workAddress?.trim() || post.areaName?.trim() || '';
}

/** 后端 LocalDateTime 序列化为毫秒时间戳（数字），不能直接当字符串切。 */
function formatLatestEntryTime(value?: number | string) {
  if (value === null || value === undefined || value === '') return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value).slice(0, 10);
  const pad = (part: number) => String(part).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function isFavorite(post: HrmPortalRecruitApi.Post) {
  return favoritePostIds.value.includes(post.id);
}

function isFavoriteCompany(company: { id: number }) {
  return favoriteCompanyIds.value.includes(company.id);
}

function toggleFavoriteCompany(company: { id: number }) {
  applyCompanyFavorite(company.id);
}

function toggleFavorite(post: HrmPortalRecruitApi.Post) {
  if (profileChecked.value && profileCompleted.value) {
    applyFavorite(post.id);
    return;
  }
  void requestProfileAction({ favoritePostId: post.id });
}

function handleSearch() {
  postPage.postName = keyword.value.trim();
  postPage.pageNo = 1;
  void router.push({ name: 'PortalRecruit', query: { section: 'jobs' } });
  void loadPosts();
}

function selectSection(section: 'companies' | 'home' | 'jobs') {
  if (section === 'home') {
    clearPostFilters();
    selectedPost.value = undefined;
  }
  void router.push({
    name: 'PortalRecruit',
    query: section === 'home' ? {} : { section },
  });
}

function handleJobNatureChange(value?: 'all' | number) {
  postPage.jobNature = value === 'all' ? undefined : value;
  refreshPosts();
}

function handleFilterChange() {
  refreshPosts();
}

function handleSalaryChange(value?: string) {
  postPage.salaryMin = undefined;
  postPage.salaryMax = undefined;
  postPage.salaryNegotiable = undefined;
  if (value === 'negotiable') {
    postPage.salaryNegotiable = true;
  } else if (value && value !== 'all') {
    const [min, max] = value.split('-');
    postPage.salaryMin = min ? Number(min) : undefined;
    postPage.salaryMax = max ? Number(max) : undefined;
  }
  refreshPosts();
}

function refreshPosts() {
  postPage.pageNo = 1;
  void loadPosts();
}

function clearPostFilters() {
  keyword.value = '';
  postPage.postName = '';
  postPage.postTypeId = undefined;
  postPage.jobNature = undefined;
  postPage.areaId = undefined;
  postPage.workTime = undefined;
  postPage.educationRequire = undefined;
  postPage.salaryMin = undefined;
  postPage.salaryMax = undefined;
  postPage.salaryNegotiable = undefined;
  salaryRange.value = undefined;
  postPage.pageNo = 1;
  sortBy.value = 'latest';
}

function resetFilters() {
  clearPostFilters();
  void loadPosts();
}

/** 重新拉取第一屏。筛选、搜索、切换职位类型都走这里，会重置滚动加载状态。 */
async function loadPosts() {
  loading.value = activeSection.value !== 'home' || !homePosts.value.length;
  postPage.pageNo = 1;
  listLoadingMore.value = false;
  listFinished.value = false;
  try {
    const result = await getPortalRecruitPostPage({
      ...postPage,
      pageSize: activeSection.value === 'home' ? 100 : postPage.pageSize,
    });
    posts.value = result.list || [];
    postTotal.value = result.total || 0;
    selectedPost.value = posts.value[0];
    listFinished.value = posts.value.length >= postTotal.value;
    await nextTick();
    // 整页滚动下不会自动触发 window scroll，这里补一次判定，
    // 免得首屏没填满视口却一直不再加载。
    // 这里刻意不动滚动位置：整页滚动时主动 scrollIntoView 会引发一串
    // scroll 事件，反过来又触发加载，形成自激的连续加载。
    handleJobsListScroll();
  } catch (error) {
    posts.value = [];
    postTotal.value = 0;
    listFinished.value = true;
    console.warn('[PortalRecruit] 加载岗位失败', error);
  } finally {
    loading.value = false;
  }
}

/** 触底追加一屏。只作用于左栏列表，首页推荐区走 homePosts，不经过这里。 */
async function loadMorePosts() {
  if (listLoadingMore.value || listFinished.value || loading.value) return;
  if (posts.value.length >= postTotal.value) {
    listFinished.value = true;
    return;
  }
  listLoadingMore.value = true;
  try {
    const result = await getPortalRecruitPostPage({
      ...postPage,
      pageNo: postPage.pageNo + 1,
      pageSize: postPage.pageSize,
    });
    const next = result.list || [];
    if (next.length === 0) {
      listFinished.value = true;
      return;
    }
    postPage.pageNo += 1;
    posts.value = [...posts.value, ...next];
    postTotal.value = result.total || postTotal.value;
    listFinished.value = posts.value.length >= postTotal.value;
  } catch (error) {
    // 失败就停在当前这批，底部会转到「已显示全部」而不是无限重试
    listFinished.value = true;
    console.warn('[PortalRecruit] 加载更多岗位失败', error);
  } finally {
    listLoadingMore.value = false;
  }
}

/**
 * 触底判定挂在 window 上：左右两栏都不再自建滚动区，岗位列表跟着整页一起滚。
 * 列表底边进入视口下方 JOB_LIST_PREFETCH_PX 范围内就预取下一屏。
 */
function handleJobsListScroll() {
  const element = jobsListRef.value;
  if (!element) return;
  const remaining = element.getBoundingClientRect().bottom - window.innerHeight;
  if (remaining <= JOB_LIST_PREFETCH_PX) void loadMorePosts();
}

/**
 * 搜索 + 筛选整块吸顶的判定。
 *
 * 直接用 getBoundingClientRect().top 判断，不另记元素在文档里的原始位置：
 * sticky 一旦吸住，top 会被钳在偏移值上，所以「top 是否已贴到导航条下沿」
 * 就等于「有没有吸住」，回弹时这个值同样准确，不会因为类名切换而自激。
 */
function syncJobsStickyBar() {
  const band = jobsSearchBandRef.value;
  if (!band) return;
  const slack = jobsBarPinned.value ? JOB_BAR_UNPIN_GAP : 0;
  jobsBarPinned.value =
    band.getBoundingClientRect().top <= jobsTopbarHeight + slack;
}

/**
 * 导航条是 sticky top:0、高度写死 60px（见 components/recruit-topbar.vue），
 * 这里仍实测一次写进 --jobs-topbar-h：那边一旦改高度，吸顶块会立刻跟着错位，
 * 而这类错位在本地很难被发现。
 */
function syncJobsTopbarHeight() {
  const element = jobsPageRef.value?.querySelector<HTMLElement>(
    ':scope > .recruit-topbar',
  );
  const height = element?.offsetHeight || JOB_TOPBAR_FALLBACK;
  if (height === jobsTopbarHeight) return;
  jobsTopbarHeight = height;
  jobsPageRef.value?.style.setProperty('--jobs-topbar-h', `${height}px`);
}

/** 整页滚动同时驱动「触底预取」与「工作台吸顶」，合成一个监听省一次 layout 读取。 */
function handleJobsPageScroll() {
  handleJobsListScroll();
  syncJobsStickyBar();
}

/** 断点变化会改导航条高度，吸顶偏移得跟着重算。 */
function handleJobsResize() {
  syncJobsTopbarHeight();
  syncJobsStickyBar();
}

async function loadHomePosts() {
  try {
    const result = await getPortalRecruitPostPage({
      ...postPage,
      postName: '',
      postTypeId: undefined,
      jobNature: undefined,
      areaId: undefined,
      workTime: undefined,
      educationRequire: undefined,
      salaryMin: undefined,
      salaryMax: undefined,
      salaryNegotiable: undefined,
      pageNo: 1,
      pageSize: 100,
    });
    homePosts.value = result.list || [];
    writeHomePostsCache(homePosts.value);
  } catch (error) {
    console.warn('[PortalRecruit] 加载首页推荐岗位失败', error);
  }
}

async function loadTenantNames() {
  try {
    const tenants = await getTenantSimpleList();
    tenantNames.value = Object.fromEntries(
      (tenants as Array<{ id?: number; name?: string }>)
        .filter((tenant) => tenant.id && tenant.name)
        .map((tenant) => [tenant.id, tenant.name]),
    );
  } catch (error) {
    console.warn('[PortalRecruit] 加载企业名称失败', error);
  }
}

function formatPostTotal(total: number) {
  return `共 ${total} 个岗位`;
}

function getPostCompanyName(post: HrmPortalRecruitApi.Post) {
  return post.companyShortName || post.companyName || (post.tenantId != null ? tenantNames.value[post.tenantId] : undefined) || '招聘企业待确认';
}

function showMorePosts() {
  router.push({ name: 'PortalRecruit', query: { section: 'jobs', view: 'list' } });
}

onMounted(async () => {
  syncPostTypeFromRoute();
  // 用捕获阶段监听：scroll 不冒泡，但捕获能收到任意元素发出的 scroll 事件。
  // 职位页的滚动容器是 .recruit-page--jobs 自己（见该类的注释），
  // 挂 window 冒泡会收不到。
  document.addEventListener('scroll', handleJobsPageScroll, {
    capture: true,
    passive: true,
  });
  syncJobsTopbarHeight();
  syncJobsStickyBar();
  window.addEventListener('resize', handleJobsResize, { passive: true });
  // mouseover 会冒泡，挂在 document 上就能同时覆盖触发器和挂在 body 下的弹出层
  document.addEventListener('mouseover', handleFilterPointerMove, {
    passive: true,
  });
  recruitAdTimer = window.setInterval(() => {
    if (!jobCategoryHovering.value) {
      recruitAdIndex.value = (recruitAdIndex.value + 1) % recruitAdImages.length;
    }
  }, 5000);
  try {
    const saved = JSON.parse(localStorage.getItem('portal-recruit-favorites') || '[]');
    if (Array.isArray(saved)) favoritePostIds.value = saved.filter((id): id is number => typeof id === 'number');
    const savedCompanies = JSON.parse(localStorage.getItem('portal-recruit-company-favorites') || '[]');
    if (Array.isArray(savedCompanies)) {
      favoriteCompanyIds.value = savedCompanies.filter((id): id is number => typeof id === 'number');
    }
  } catch {}
  // 地区树只为「城市热招」服务 —— 职位页的地址筛选由 AreaCascader 自己拉一份，
  // 所以只在首页预取，省掉其它分区一次无谓请求。点城市时还会再兜一次底。
  if (activeSection.value === 'home') void loadCityAreaIds();
  await Promise.all([
    loadHomePosts(),
    loadTenantNames(),
    getPortalRecruitCompanyList()
      .then((result) => {
        recruitAdCompanies.value = result || [];
      })
      .catch((error) => {
        console.warn('[PortalRecruit] 加载轮播企业失败', error);
      }),
    getPortalRecruitPostTypeList()
      .then(async (result) => {
        const portalTypes = normalizePortalRecruitPostTypes(result || []);
        if (portalTypes.length) {
          postTypes.value = portalTypes;
          return;
        }
        const adminTypes = await getRecruitPostTypeList();
        postTypes.value = normalizePortalRecruitPostTypes(adminTypes || []);
      })
      .catch(async () => {
        try {
          const adminTypes = await getRecruitPostTypeList();
          postTypes.value = normalizePortalRecruitPostTypes(adminTypes || []);
        } catch {
          postTypes.value = [];
        }
      }),
    checkRecruitProfile(),
  ]);
  // 岗位列表必须等资料检查完成后再取：activeSection 在 profileChecked 之前会回落到
  // home，此时取数会按首页的 100 条去要，职位页就会一次渲染过多岗位。
  await loadPosts();
  const requestedProfileSection = requestedSection.value;
  if (!profileCompleted.value && isProfileActionSection(requestedProfileSection) && !pendingProfileAction.value) {
    const postId = Array.isArray(route.query.postId) ? route.query.postId[0] : route.query.postId;
    pendingProfileAction.value = { section: requestedProfileSection, postId: postId || undefined };
    profileSetupOpen.value = true;
  }
});

onUnmounted(() => {
  document.removeEventListener('scroll', handleJobsPageScroll, { capture: true });
  window.removeEventListener('resize', handleJobsResize);
  document.removeEventListener('mouseover', handleFilterPointerMove);
  window.clearTimeout(hoverOpenTimer);
  window.clearTimeout(hoverCloseTimer);
  if (recruitAdTimer) window.clearInterval(recruitAdTimer);
});
</script>

<template>
  <div
    ref="jobsPageRef"
    class="portal-page recruit-page"
    :class="{ 'recruit-page--home': activeSection === 'home', 'recruit-page--jobs': activeSection === 'jobs', 'recruit-page--messages': activeSection === 'messages', 'recruit-page--rich-footer': showRecruitFooter }"
    :style="recruitAdThemeStyle"
  >
    <RecruitTopbar
      :active="activeSection"
      :display-name="navUserName"
      :guard-profile-actions="true"
      :show-resume-tools="true"
      :themed="true"
      @resume-ai="openResumeAi"
      @resume-edit="openResumeEditor"
      @resume-generate="openResumeGenerator"
      @resume-upload="openResumeUpload"
      @resume-version="openResumeVersions"
      @section="openGatedSection"
    />

    <a-modal
      v-model:open="profileSetupOpen"
      :footer="null"
      :mask-closable="false"
      width="1180px"
      wrap-class-name="profile-setup-modal"
      @cancel="cancelProfileSetup"
    >
      <RecruitProfileSetup :embedded="true" @completed="handleProfileSetupCompleted" />
    </a-modal>

    <Messages v-if="activeSection === 'messages'" embedded />
    <OnlineResume
      v-else-if="activeSection === 'resume'"
      :pending-action="pendingResumeAction"
      @action-handled="handleResumeActionHandled"
    />
    <RecruitProfile v-else-if="activeSection === 'user'" section="user" embedded />

    <section v-if="activeSection === 'home'" class="recruit-hero">
      <div class="hero-topline">
        <button class="hero-brand-wordmark" type="button" @click="selectSection('home')" aria-label="天信招聘首页">
          <span class="hero-brand-star">✦</span><span>天信招聘</span>
        </button>
        <div class="hero-tools" aria-label="招聘服务入口">
          <span class="hero-tool-icon wechat-icon"><IconifyIcon icon="lucide:message-circle" /></span>
          <span class="hero-tool-icon app-icon"><IconifyIcon icon="lucide:smartphone" /></span>
        </div>
      </div>
      <div class="hero-copy">
        <p class="eyebrow">人才服务 · 职业机会</p>
        <h1>发现适合你的下一份工作</h1>
      </div>
      <div class="hero-visual" aria-hidden="true">
        <div class="hero-orbit hero-orbit-one"></div>
        <div class="hero-orbit hero-orbit-two"></div>
        <div class="hero-profile-card">
          <span class="hero-avatar">职</span>
          <span><b>专业人才</b><small>正在寻找新机会</small></span>
          <i>↗</i>
        </div>
        <div class="hero-role-card"><span>匹配职位</span><b>项目管理 · </b></div>
      </div>
      <div class="hero-search">
        <a-input
          v-model:value="keyword"
          size="large"
          allow-clear
          placeholder="搜索职位名称、关键字..."
          @press-enter="handleSearch"
        >
          <template #prefix>
            <IconifyIcon icon="lucide:search" />
          </template>
        </a-input>
        <a-button type="primary" size="large" @click="handleSearch">
          搜索职位
        </a-button>
      </div>
      <div class="quick-searches">
        <span>热门职位</span>
        <button
          v-for="item in hotJobItems"
          :key="item.name"
          type="button"
          @click="selectHotJob(item.name, item.id)"
        >
          {{ item.name }}
        </button>
      </div>
    </section>

    <section
      v-if="activeSection === 'home'"
      class="recruit-category-showcase"
      aria-label="职位分类与招聘广告"
      @mouseenter="jobCategoryHovering = true"
      @mouseleave="jobCategoryHovering = false; hoveredJobCategory = undefined"
    >
      <aside
        class="recruit-category-sidebar"
      >
        <button
          v-for="category in jobCategoryGroups"
          :key="category.name"
          type="button"
          :class="{ active: hoveredJobCategory === category.name }"
          @mouseenter="setJobCategory(category.name)"
          @click="setJobCategory(category.name)"
        >
          <span>{{ category.name }}</span><IconifyIcon icon="lucide:chevron-right" />
        </button>
      </aside>
      <div class="recruit-category-content">
        <template v-if="showJobCategoryDetail && activeJobCategoryGroup">
          <div class="category-detail-head"><strong>{{ activeJobCategoryGroup.name }}</strong></div>
          <div class="category-post-groups">
            <section v-for="section in activeJobCategorySections" :key="section.name" class="category-post-group">
              <h3>{{ section.name }}</h3>
              <div class="category-post-links">
                <button v-for="jobName in section.jobs" :key="jobName" type="button" @click="selectCategoryLeaf({ id: postTypes.find((type) => type.name === jobName)?.id || -1, name: jobName })">
                  {{ jobName }}
                </button>
              </div>
            </section>
            <p v-if="!activeJobCategorySections.length" class="category-empty">暂无该分类职位，试试其他分类。</p>
          </div>
        </template>
        <div v-else class="recruit-ad-carousel">
          <button
            type="button"
            class="recruit-ad-image-button"
            :aria-label="`查看${recruitAdCompanyNames[currentRecruitAdImage] || ''}企业主页`"
            @click="openRecruitAdCompany(currentRecruitAdImage)"
          >
            <img :src="currentRecruitAdImage" alt="天信招聘行业招聘广告" />
          </button>
          <div class="recruit-ad-dots">
            <button
              v-for="(_, index) in recruitAdImages"
              :key="index"
              type="button"
              :class="{ active: recruitAdIndex === index }"
              :aria-label="'切换到第 ' + (index + 1) + ' 张招聘广告'"
              @click="recruitAdIndex = index"
            />
          </div>
        </div>
      </div>
    </section>

    <section
      v-if="activeSection === 'jobs'"
      ref="jobsSearchBandRef"
      class="jobs-search-band"
      :class="{ 'is-pinned': jobsBarPinned }"
      aria-label="职位搜索与筛选"
    >
      <div class="jobs-search-inner">
        <div class="hero-search jobs-search-form">
          <a-input
            v-model:value="keyword"
            size="large"
            allow-clear
            placeholder="搜索职位名称、关键字..."
            @press-enter="handleSearch"
          >
            <template #prefix>
              <IconifyIcon icon="lucide:search" />
            </template>
          </a-input>
          <a-button type="primary" size="large" @click="handleSearch">搜索职位</a-button>
        </div>
      </div>
      <section class="filter-panel">
        <div class="filter-heading">
          <div>
            <span class="section-kicker">岗位机会</span>
            <strong>筛选你感兴趣的职位</strong>
          </div>
          <span class="result-count">{{ formatPostTotal(postTotal) }}</span>
        </div>
        <div class="filter-toolbar">
          <AreaCascader
            v-model="postPage.areaId"
            allow-clear
            class="filter-control area-control"
            :change-on-select="true"
            placeholder="地址"
            popup-class-name="recruit-filter-dropdown jobs-filter-dropdown"
            :selectable-levels="[1, 2, 3]"
            show-search
            @mouseenter="closeFilterDropdownNow()"
            @update:model-value="handleFilterChange"
          />
          <a-select
            v-model:value="postPage.postTypeId"
            :open="jobTypePanelOpen"
            allow-clear
            class="filter-control job-type-control"
            placeholder="职位类型"
            popup-class-name="recruit-filter-dropdown jobs-filter-dropdown job-type-dropdown"
            @change="handleFilterChange"
            @dropdown-visible-change="jobTypePanelOpen = $event"
            @mouseenter="openFilterDropdown('jobType')"
          >
            <!-- 保留 options 仅用于回显选中项名称，下拉内容由 dropdownRender 接管 -->
            <a-select-option
              v-for="type in selectablePostTypes"
              :key="type.id"
              :value="type.id"
            >
              {{ type.name }}
            </a-select-option>
            <template #dropdownRender>
              <div class="job-type-panel">
                <nav class="job-type-groups" aria-label="职位大类">
                  <button
                    v-for="group in jobTypeGroups"
                    :key="group.name"
                    type="button"
                    class="job-type-group"
                    :class="{ 'is-active': group.name === activeJobTypeGroup }"
                    @mouseenter="activeJobTypeGroup = group.name"
                    @focus="activeJobTypeGroup = group.name"
                  >
                    {{ group.name }}
                  </button>
                </nav>
                <div class="job-type-items" role="listbox">
                  <button
                    v-for="item in activeJobTypeItems"
                    :key="item.id"
                    type="button"
                    role="option"
                    class="job-type-item"
                    :class="{ 'is-selected': item.id === postPage.postTypeId }"
                    :aria-selected="item.id === postPage.postTypeId"
                    @click="pickJobType(item.id)"
                  >
                    {{ item.name }}
                  </button>
                </div>
              </div>
            </template>
          </a-select>
          <a-select
            :value="postPage.jobNature"
            :open="activeFilterKey === 'jobNature'"
            allow-clear
            class="filter-control"
            placeholder="求职类型"
            popup-class-name="recruit-filter-dropdown jobs-filter-dropdown"
            @change="handleJobNatureChange"
            @dropdown-visible-change="handleFilterDropdownVisible('jobNature', $event)"
            @mouseenter="openFilterDropdown('jobNature')"
          >
            <a-select-option
              v-for="option in jobNatureOptions"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </a-select-option>
          </a-select>
          <a-select
            v-model:value="salaryRange"
            :open="activeFilterKey === 'salary'"
            allow-clear
            class="filter-control"
            placeholder="薪资待遇"
            popup-class-name="recruit-filter-dropdown jobs-filter-dropdown"
            @change="handleSalaryChange"
            @dropdown-visible-change="handleFilterDropdownVisible('salary', $event)"
            @mouseenter="openFilterDropdown('salary')"
          >
            <a-select-option
              v-for="option in salaryOptions"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </a-select-option>
          </a-select>
          <a-select
            v-model:value="postPage.workTime"
            :open="activeFilterKey === 'workTime'"
            allow-clear
            class="filter-control"
            placeholder="工作经验"
            :options="workTimeOptions"
            popup-class-name="recruit-filter-dropdown jobs-filter-dropdown"
            @change="handleFilterChange"
            @dropdown-visible-change="handleFilterDropdownVisible('workTime', $event)"
            @mouseenter="openFilterDropdown('workTime')"
          />
          <a-select
            v-model:value="postPage.educationRequire"
            :open="activeFilterKey === 'education'"
            allow-clear
            class="filter-control"
            placeholder="学历要求"
            :options="educationOptions"
            popup-class-name="recruit-filter-dropdown jobs-filter-dropdown"
            @change="handleFilterChange"
            @dropdown-visible-change="handleFilterDropdownVisible('education', $event)"
            @mouseenter="openFilterDropdown('education')"
          />
          <a-select
            v-model:value="sortBy"
            :open="activeFilterKey === 'sort'"
            class="filter-control sort-control"
            popup-class-name="recruit-filter-dropdown jobs-filter-dropdown"
            placeholder="最新发布"
            @dropdown-visible-change="handleFilterDropdownVisible('sort', $event)"
            @mouseenter="openFilterDropdown('sort')"
          >
            <a-select-option
              v-for="option in sortOptions"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </a-select-option>
          </a-select>
        </div>
        <div class="filter-actions">
          <a-button type="link" class="reset-button" @click="resetFilters">
            <IconifyIcon icon="lucide:rotate-ccw" />
            重置筛选
          </a-button>
        </div>
      </section>
    </section>

    <a-tabs
      v-if="activeSection === 'home' || activeSection === 'jobs'"
      :class="{ 'home-recruit-tabs': activeSection === 'home' }"
      class="recruit-tabs"
    >
      <a-tab-pane key="posts" tab="岗位机会">
        <div v-if="activeSection === 'home'" class="home-jobs-heading">
          <h2>热招职位</h2>
          <div class="home-job-categories">
            <!--「全部」得显式给一颗：默认就是它，不给的话首屏十颗全是未选态，
                而且选过一次行业之后再也退不回全部。 -->
            <button
              type="button"
              :class="{ active: selectedHomeIndustry === 'all' }"
              @click="selectHomeIndustry('all')"
            >
              全部
            </button>
            <button
              v-for="industry in homeIndustries"
              :key="industry.key"
              :class="{ active: selectedHomeIndustry === industry.key }"
              type="button"
              @click="selectHomeIndustry(industry.key)"
            >
              {{ industry.label }}
            </button>
          </div>
        </div>
        <div v-if="loading" class="post-list post-list-loading">
          <div v-for="item in 4" :key="item" class="post-card">
            <Skeleton active :paragraph="{ rows: 3 }" />
          </div>
        </div>
        <div v-else-if="(activeSection !== 'home' && !displayPosts.length) || (activeSection === 'home' && !isSearchMode && !showAllPosts && !homePreviewPosts.length)" class="portal-empty">
          <span class="portal-empty-icon">
            <IconifyIcon icon="lucide:search-x" />
          </span>
          <strong>暂时没有符合条件的职位</strong>
          <p>试试调整工作性质、职位类型或搜索关键词</p>
        </div>
        <div v-else-if="activeSection === 'home' && !isSearchMode && !showAllPosts" class="home-job-grid">
          <article
            v-for="post in homePreviewPosts"
            :key="post.id"
            class="post-card home-job-card"
            role="link"
            tabindex="0"
            @click="openCategoryPost(post)"
            @keydown.enter="openCategoryPost(post)"
          >
            <div class="post-card-main">
              <div class="post-title-row">
                <div class="home-job-title-actions">
                  <button class="home-job-title-group" type="button" @click.stop="openCategoryPost(post)">
                    <strong class="post-title">{{ post.postName }}</strong>
                  </button>
                  <button class="home-job-chat-button" type="button" aria-label="进入招聘沟通" @click.stop="openChat(post)">
                    <IconifyIcon class="home-job-chat-icon" icon="lucide:message-square" />
                  </button>
                </div>
                <strong class="post-salary post-salary-top">{{ formatSalary(post) }}</strong>
              </div>
              <!-- 地点 / 经验 / 学历是「元信息」不是「标签」，配上图标和职位页右栏的
                   元信息行同一套读法；原来做成三个灰底胶囊，对比度低又糊成一片。 -->
              <div class="post-tags">
                <span class="post-tag-location"><IconifyIcon icon="lucide:map-pin" />{{ formatProvinceLocation(post) }}</span>
                <span class="post-tag-experience"><IconifyIcon icon="lucide:briefcase" />{{ formatWorkTime(post.workTime) }}</span>
                <span class="post-tag-education"><IconifyIcon icon="lucide:graduation-cap" />{{ formatEducation(post.educationRequire) }}</span>
              </div>
            </div>
            <div class="post-card-actions">
              <button class="post-card-company" type="button" @click.stop="openHomePostCompany(post)">
                <img v-if="post.companyLogo" class="home-job-company-logo" :src="post.companyLogo" :alt="`${getPostCompanyName(post)}企业 Logo`" />
                <span v-else class="home-job-company-logo home-job-company-logo-fallback">{{ getPostCompanyName(post).slice(0, 1) }}</span>
                <span>{{ getPostCompanyName(post) }}</span>
              </button>
              <!-- 退到企业行业。原来空值兜底写「企业规模待确认」，实测首页九张卡
                   全部命中兜底，同一句话印九遍，既是噪音也在说「这页没做好」。 -->
              <span v-if="post.companyScale || post.companyIndustry" class="company-meta-detail">{{ post.companyScale || post.companyIndustry }}</span>
            </div>
          </article>
        </div>
        <div v-else class="jobs-results-shell">
            <div class="jobs-split">
              <div
                ref="jobsListRef"
                class="jobs-card-list"
                @scroll.passive="handleJobsListScroll"
              >
                <article
                  v-for="(post, index) in displayPosts"
                  :key="post.id"
                  class="jobs-post-card"
                  :class="{ selected: selectedPost?.id === post.id }"
                  :style="{ '--enter-index': index % JOB_LIST_STEP }"
                  @click="selectPost(post)"
                >
                  <div class="jobs-post-company">
                    <img
                      v-if="canShowImage(post.companyLogo)"
                      :src="post.companyLogo"
                      :alt="`${getPostCompanyName(post)}企业 Logo`"
                      @error="markImageBroken(post.companyLogo)"
                    />
                    <span v-else>{{ getPostCompanyName(post).slice(0, 1) }}</span>
                    <div>
                      <strong>{{ getPostCompanyName(post) }}</strong>
                      <small>{{ post.companyScale || '企业规模待确认' }}</small>
                    </div>
                  </div>
                  <div class="jobs-post-content">
                    <div class="jobs-post-title-row">
                      <h3>{{ post.postName }}</h3>
                      <b>{{ formatSalary(post) }}</b>
                    </div>
                    <div class="jobs-post-meta">
                      <span><IconifyIcon icon="lucide:map-pin" />{{ formatLocation(post) }}</span>
                      <span><IconifyIcon icon="lucide:briefcase-business" />{{ formatWorkTime(post.workTime) }}</span>
                      <span><IconifyIcon icon="lucide:graduation-cap" />{{ formatEducation(post.educationRequire) }}</span>
                    </div>
                    <div class="jobs-post-tags">
                      <span v-if="post.postTypeName">{{ post.postTypeName }}</span>
                      <span v-if="formatJobNature(post.jobNature)">{{ formatJobNature(post.jobNature) }}</span>
                      <span v-if="post.recruitNum">招聘 {{ post.recruitNum }} 人</span>
                    </div>
                  </div>
                  <a-tooltip :title="isFavorite(post) ? '取消收藏' : '收藏职位'">
                    <button class="jobs-post-favorite" :class="{ active: isFavorite(post) }" type="button" :aria-label="isFavorite(post) ? '取消收藏' : '收藏职位'" @click.stop="toggleFavorite(post)">
                      <IconifyIcon icon="lucide:star" />
                    </button>
                  </a-tooltip>
                </article>
                <div class="jobs-list-footer" :class="{ 'is-loading': listLoadingMore }" aria-live="polite">
                  <template v-if="listLoadingMore">
                    <span class="jobs-list-spinner" aria-hidden="true" />
                    <span>正在加载更多岗位…</span>
                  </template>
                  <template v-else-if="listFinished">
                    <IconifyIcon icon="lucide:circle-check" />
                    <span>{{ postTotal ? `已显示全部 ${postTotal} 个岗位` : '暂无更多岗位' }}</span>
                  </template>
                  <template v-else>
                    <IconifyIcon icon="lucide:chevron-down" class="jobs-list-hint-icon" />
                    <span>继续向下滑动，加载更多</span>
                  </template>
                </div>
            </div>
          <article v-if="selectedPost" class="jobs-detail-card">
            <!-- ① 企业行：可点的企业身份，右侧收藏 -->
            <header class="job-detail-company">
              <button class="job-detail-company-main" type="button" @click="openHomePostCompany(selectedPost)">
                <img
                  v-if="canShowImage(selectedPost.companyLogo)"
                  :src="selectedPost.companyLogo"
                  :alt="`${getPostCompanyName(selectedPost)}企业 Logo`"
                  @error="markImageBroken(selectedPost?.companyLogo)"
                />
                <span v-else class="job-detail-company-logo-fallback">{{ getPostCompanyName(selectedPost).slice(0, 1) }}</span>
                <span class="job-detail-company-text">
                  <strong>{{ getPostCompanyName(selectedPost) }}</strong>
                  <small>{{ detailCompanyMeta }}</small>
                </span>
                <IconifyIcon icon="lucide:arrow-up-right" class="job-detail-company-link" />
              </button>
              <a-tooltip :title="isFavorite(selectedPost) ? '取消收藏' : '收藏职位'">
                <button
                  class="job-detail-favorite"
                  :class="{ active: isFavorite(selectedPost) }"
                  type="button"
                  :aria-label="isFavorite(selectedPost) ? '取消收藏该职位' : '收藏该职位'"
                  @click="toggleFavorite(selectedPost)"
                >
                  <IconifyIcon icon="lucide:star" />
                </button>
              </a-tooltip>
            </header>

            <!-- ② 标题行：薪资是决策信息，与职位名同一行；绿色只给主 CTA -->
            <div class="job-detail-title-row">
              <div class="job-detail-heading">
                <h2>{{ selectedPost.postName }}</h2>
                <p class="job-detail-salary-line">
                  <b>{{ formatSalary(selectedPost) }}</b>
                  <span v-for="item in detailMetaItems" :key="item">{{ item }}</span>
                </p>
              </div>
              <div class="job-detail-action">
                <a-button type="primary" class="job-detail-chat" @click="openChat(selectedPost)">
                  <IconifyIcon icon="lucide:message-circle" /> 立即沟通
                </a-button>
              </div>
            </div>

            <!-- ①② 是常驻的「岗位速览」：它们在 .job-detail-card 里、滚动区之外，
                 所以右侧无论滚到哪都不动。下面 .job-detail-panel 是唯一的滚动区。
                 条件（地区/经验/学历/人数/性质）已全部并进②的条件串，
                 原先独立的「条款带」和「职位信息」表因此撤掉 —— 它们列的字段
                 与①②逐项重复，而且一半格子摆的是常年为空的企业字段。 -->
            <div ref="jobDetailPanelRef" class="job-detail-panel">
              <section class="job-detail-block">
                <h3>职位描述</h3>
                <div v-if="detailDescriptionLines.length" class="job-detail-copy">
                  <p v-for="(line, index) in detailDescriptionLines" :key="index" :class="{ 'is-bullet': line.bullet }">
                    {{ line.text }}
                  </p>
                </div>
                <!-- 全量 261 条岗位里有 43 条没填描述，不算常态但也谈不上罕见，
                     不该只吊一行灰字了事。把「怎么问」讲清楚，这一块才有得读。 -->
                <div v-else class="job-detail-blank">
                  <IconifyIcon icon="lucide:file-text" />
                  <p>
                    <strong>招聘方未填写职位描述</strong>
                    <span>岗位职责、班组安排、食宿与待遇等细节，可在沟通中直接向招聘方了解。</span>
                  </p>
                </div>
              </section>

              <!-- ③ 招聘方：读完全部信息后回答「对面是谁」，
                     正好接住顶部那个常驻的「立即沟通」。 -->
              <section v-if="detailRecruiter" class="job-detail-block">
                <h3>招聘方</h3>
                <div class="job-detail-recruiter">
                  <span class="job-detail-recruiter-avatar">
                    <!-- @error 是异步触发的：图片开始加载之后 selectedPost 可能
                         已经被换掉，事件回来时 detailRecruiter 已经是 undefined，
                         处理器里直接取 .avatar 会抛 TypeError（实测能复现：
                         点城市进职位页，列表重载把 selectedPost 重置，
                         上一张还在加载的头像随后失败）。 -->
                    <img
                      v-if="canShowImage(detailRecruiter.avatar)"
                      :src="detailRecruiter.avatar"
                      :alt="detailRecruiter.name"
                      @error="markImageBroken(detailRecruiter?.avatar)"
                    />
                    <template v-else>{{ detailRecruiter.name.slice(0, 1) }}</template>
                  </span>
                  <span class="job-detail-recruiter-text">
                    <strong>{{ detailRecruiter.name }}</strong>
                    <small>{{ getPostCompanyName(selectedPost) }}</small>
                  </span>
                </div>
              </section>

              <!-- ④ 工作地址压轴：读完岗位、也知道了找谁聊，最后才核对通勤。
                   地图块高度固定（160px），夹在中间会把下方内容切成两段。 -->
              <section class="job-detail-block">
                <h3>工作地址</h3>
                <JobLocationMap
                  :address="formatWorkAddress(selectedPost)"
                  :longitude="selectedPost.longitude"
                  :latitude="selectedPost.latitude"
                />
                <!-- 右栏是一屏速览，想要完整版（企业档案、完整描述等）从这里出去 -->
                <button
                  class="job-detail-more"
                  type="button"
                  @click="openPostDetailPage(selectedPost)"
                >
                  查看更多职位详情
                  <IconifyIcon icon="lucide:arrow-right" />
                </button>
              </section>
            </div>
          </article>
            </div>
        </div>
        <a-button
          v-if="activeSection === 'home' && !isSearchMode && !showAllPosts && postTotal > previewPosts.length"
          class="show-more-posts"
          type="default"
          @click="showMorePosts"
        >
          查看更多
        </a-button>
      </a-tab-pane>
</a-tabs>

    <section v-if="activeSection === 'home' && hotCompanies.length" class="home-companies-section">
      <div class="home-jobs-heading">
        <h2>热门企业</h2>
      </div>
      <div class="home-job-grid home-company-grid">
        <article
          v-for="company in visibleHotCompanies"
          :key="company.id"
          class="post-card home-company-card"
          role="link"
          tabindex="0"
          @click="openHomeCompany(company)"
          @keydown.enter="openHomeCompany(company)"
        >
          <header class="hot-company-header" role="link" tabindex="0" @click.stop="openHomeCompany(company)" @keydown.enter.stop="openHomeCompany(company)">
            <img v-if="company.logo" class="hot-company-logo" :src="company.logo" :alt="company.name" />
            <span v-else class="hot-company-logo hot-company-logo-fallback">{{ company.name.slice(0, 1) }}</span>
            <div class="hot-company-heading">
              <strong class="hot-company-name">{{ company.name }}</strong>
              <!-- 分隔符从全角竖线换成中点：竖线本身占一个字宽，加上「100-500 人」
                   这类长文本，整行溢出被 ellipsis 切成「100-5...」。 -->
              <span class="hot-company-meta">
                <span>{{ company.posts.length }} 个公开岗位</span>
                <i v-if="company.financingStage || company.scale">·</i>
                <span v-if="company.financingStage">{{ company.financingStage }}</span>
                <i v-if="company.financingStage && company.scale">·</i>
                <span v-if="company.scale">{{ company.scale }}</span>
              </span>
            </div>
            <a-tooltip :title="isFavoriteCompany(company) ? '取消收藏' : '收藏企业'">
              <button
                type="button"
                class="company-favorite-button"
                :class="{ active: isFavoriteCompany(company) }"
                :aria-label="isFavoriteCompany(company) ? `取消收藏${company.name}` : `收藏${company.name}`"
                @click.stop="toggleFavoriteCompany(company)"
              >
                <IconifyIcon icon="lucide:star" />
              </button>
            </a-tooltip>
          </header>
          <div class="hot-company-posts">
            <button
              v-for="post in company.posts.slice(0, 2)"
              :key="post.id"
              type="button"
              class="hot-company-post"
              @click.stop="openCategoryPost(post)"
            >
              <span class="hot-company-post-heading">
                <strong>{{ post.postName || '行业招聘岗位' }}</strong>
                <b>{{ formatSalary(post) }}</b>
              </span>
              <span class="hot-company-tags">
                <span class="hot-company-tag-location"><IconifyIcon icon="lucide:map-pin" />{{ formatHotCompanyPostLocation(post) }}</span>
                <span class="hot-company-tag-experience"><IconifyIcon icon="lucide:briefcase" />{{ formatWorkTime(post.workTime) }}</span>
                <span class="hot-company-tag-education"><IconifyIcon icon="lucide:graduation-cap" />{{ formatEducation(post.educationRequire) }}</span>
              </span>
            </button>
            <p v-if="!company.posts.length" class="hot-company-empty">暂无公开职位</p>
          </div>
          <button type="button" class="hot-company-more" @click.stop="openHomeCompany(company)">
            查看更多职位
          </button>
        </article>
      </div>
      <a-button
        v-if="hotCompanies.length"
        class="show-more-posts home-company-more"
        type="default"
        @click="selectSection('companies')"
      >
        查看更多
      </a-button>
    </section>

    <CompanyDirectory v-else-if="activeSection === 'companies'" />

    <section v-if="activeSection === 'home'" class="city-opportunities-section" aria-labelledby="city-opportunities-title">
      <div class="city-opportunities-heading">
        <h2 id="city-opportunities-title">城市热招</h2>
      </div>
      <div class="city-opportunities-viewport">
        <div class="city-opportunities-grid">
          <button
            v-for="city in visibleCityOpportunities"
            :key="city.name"
            class="city-opportunity-card"
            type="button"
            :style="{ backgroundImage: 'linear-gradient(180deg, rgb(7 30 49 / 4%), rgb(7 25 42 / 76%)), url(' + city.image + ')' }"
            @click="selectCityOpportunity(city.name)"
          >
            <span class="city-opportunity-arrow" aria-hidden="true">↗</span>
            <span class="city-opportunity-copy">
              <strong>{{ city.name }}</strong>
              <small>{{ city.english }}</small>
            </span>
          </button>
        </div>
        <button
          v-if="cityOpportunityPageCount > 1"
          class="city-opportunity-nav city-opportunity-prev"
          type="button"
          aria-label="上一组城市"
          @click="shiftCityOpportunities(-1)"
        >
          <IconifyIcon icon="lucide:chevron-left" />
        </button>
        <button
          v-if="cityOpportunityPageCount > 1"
          class="city-opportunity-nav city-opportunity-next"
          type="button"
          aria-label="下一组城市"
          @click="shiftCityOpportunities(1)"
        >
          <IconifyIcon icon="lucide:chevron-right" />
        </button>
      </div>
    </section>

    <footer v-if="showRecruitFooter" class="recruit-home-footer">
      <div class="recruit-footer-inner">
        <section class="recruit-footer-intro">
          <p class="recruit-footer-kicker">天信人才服务</p>
          <h2>让专业的人，遇见合适的机会</h2>
          <p>连接防腐保温产业链企业与人才，让每一次寻找都有回应。</p>
          <span class="recruit-footer-badge"><IconifyIcon icon="lucide:shield-check" /> 专注行业招聘服务</span>
        </section>

        <nav class="recruit-footer-column" aria-label="企业招聘服务">
          <h3>企业招聘</h3>
          <button type="button" @click="selectSection('companies')">查看合作企业</button>
          <button type="button" @click="selectSection('companies')">发布招聘需求</button>
          <button type="button" @click="openGatedSection('messages')">联系招聘顾问</button>
        </nav>

        <nav class="recruit-footer-column" aria-label="求职帮助">
          <h3>求职帮助</h3>
          <button type="button" @click="selectSection('jobs')">职位搜索</button>
          <button type="button" @click="openGatedSection('resume')">完善求职资料</button>
          <button type="button" @click="openGatedSection('user')">查看应聘记录</button>
        </nav>

        <section class="recruit-footer-contact">
          <h3>招聘服务中心</h3>
          <strong>在线沟通，快速回应</strong>
          <p>遇到岗位、投递或简历问题，进入招聘沟通与 HR 直接交流。</p>
          <button type="button" @click="openGatedSection('messages')">
            <IconifyIcon icon="lucide:messages-square" /> 进入招聘沟通
          </button>
        </section>
      </div>

      <div class="recruit-footer-bottom">
        <span>© {{ currentYear }} 防腐保温智慧平台 · 天信人才服务</span>
        <span>职位浏览 · 简历投递 · 招聘沟通</span>
        <span class="recruit-footer-mark">TXGY <small>HRM RECRUITMENT</small></span>
      </div>
    </footer>
</div>
</template>

<style scoped>
.portal-page {
  display: grid;
  gap: 10px;
}

.recruit-page {
  display: grid;
  width: 100%;
  box-sizing: border-box;
  grid-template-columns: minmax(16px, 1fr) minmax(0, 1180px) minmax(16px, 1fr);
  min-width: 1280px;
  margin-top: -32px;
  --recruit-ink: #4a2916;
  --recruit-muted: #876f5e;
  --recruit-line: #f0dac1;
  --recruit-accent: #e87522;
  --recruit-accent-soft: #fff0df;
}

/*
 * 沟通工作台独占一屏，不跟随上面那条 -32px 上提。
 * -32px 是给首页/职位页的 hero 塞进导航条下面用的，那两页顶上压着的是
 * 装饰性色块，被吃掉 32px 看不出来；沟通页顶上压的是搜索框，会直接少一截。
 * 而且 .portal-main 是 overflow-y: auto，落在滚动原点之上的内容 Chrome 不
 * 计入可滚动区 —— 那 32px 永远滚不到，只能从布局上还回去。
 * 行高用 auto + 1fr，让面板自己吃掉导航条和页脚留白之间的全部高度，
 * 这样页面刚好一屏，既不会被压住也不会在下面留一大块空白。
 */
.recruit-page--messages {
  /*
   * 必须是 height 而不是 min-height。min-height 下容器高度对轨道计算是
   * 不确定的，`1fr` 会退回按内容计算 —— 会话列表十条就能把这一行撑到
   * 962px，视口只有 760 时页面立刻出现滚动条。定死高度，1fr 才拿得到
   * 剩余空间。视口太矮时面板的 min-height 兜底，多出来的交给外层滚动。
   */
  height: 100dvh;
  margin-top: 0;
  grid-template-rows: auto minmax(0, 1fr);
}

.recruit-page > * { grid-column: 2; min-width: 0; }
.recruit-page > .recruit-topbar { grid-column: 1 / -1; width: 100%; margin-left: 0; }
.recruit-page > .recruit-subnav { grid-column: 1 / -1; width: 100%; margin-left: 0; padding-left: max(56px, calc((100% - 1180px) / 2 + 32px)); padding-right: max(56px, calc((100% - 1180px) / 2 + 32px)); }

:global(.portal-main--recruit-home) {
  min-width: 1280px;
  overflow-x: auto;
}

:global(.portal-main--recruit) {
  min-width: 1280px;
  overflow-x: auto;
}

:global(.portal-shell--recruit) {
  width: 100%;
  min-width: 1280px;
  overflow-x: auto;
}

:global(.portal-shell--recruit .portal-header) {
  min-width: 1280px;
  flex-wrap: nowrap;
  gap: 32px;
  padding: 0 36px;
}

:global(.portal-shell--recruit .portal-user) {
  padding-bottom: 0;
}

:global(.portal-shell--recruit .portal-nav) {
  order: initial;
  width: auto;
  min-height: 0;
}

:global(.portal-shell--recruit .portal-nav button) {
  min-width: 88px;
  padding: 0 14px;
}

:global(.portal-shell--recruit .portal-main) {
  width: 100%;
  min-width: 1280px;
  max-width: none;
  padding: 0;
}

/* 首页导航的 sticky 需要绑定浏览器滚动，不被招聘页的横向滚动容器截断。 */
:global(.portal-shell--recruit:has(.recruit-page--home)),
:global(.portal-shell--recruit:has(.recruit-page--home) .portal-main--recruit-home) {
  overflow-x: visible;
}

.recruit-page--home > .recruit-topbar,
.recruit-page--home > .recruit-subnav {
  width: 100%;
  margin-left: 0;
}

.service-section { padding:18px 0 30px; }
.service-heading { display:flex; justify-content:space-between; align-items:end; margin-bottom:16px; }
.service-heading h2 { margin:7px 0 0; color:var(--recruit-ink); font-size:25px; }
.service-heading > span { color:var(--recruit-muted); font-size:15px; }
.service-grid { display:grid; grid-template-columns:repeat(3,1fr); gap:14px; }
.service-grid article { padding:20px; border:1px solid var(--recruit-line); border-radius:14px; background:#fff; transition:transform .2s,box-shadow .2s; }
.service-grid article:hover { transform:translateY(-3px); box-shadow:0 12px 25px rgb(183 92 24 / 12%); }
.service-icon { display:grid; width:38px; height:38px; place-items:center; border-radius:11px; color:#c85e18; background:#fff0df; font-size:22px; }
.service-grid h3 { margin:14px 0 7px; font-size:16px; color:var(--recruit-ink); }.service-grid p { min-height:42px; margin:0 0 13px; color:var(--recruit-muted); font-size:14px; line-height:1.7; }.service-grid a { color:#c85e18; font-size:15px; cursor:pointer; }

.recruit-subnav {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 26px;
  min-height: 54px;
  width: 100%;
  margin-left: 0;
  padding: 0 24px;
  border-block: 1px solid rgb(238 225 211 / 90%);
  border-radius: 0;
  background: rgb(255 255 255 / 82%);
  box-shadow: 0 8px 20px rgb(183 92 24 / 7%);
  backdrop-filter: blur(16px) saturate(115%);
  -webkit-backdrop-filter: blur(16px) saturate(115%);
  transition: background-color 220ms ease, box-shadow 220ms ease, backdrop-filter 220ms ease;
}

.recruit-subnav.is-scrolled {
  background: rgb(255 255 255 / 92%);
  box-shadow: 0 10px 28px rgb(183 92 24 / 10%);
  backdrop-filter: blur(18px) saturate(145%);
  -webkit-backdrop-filter: blur(18px) saturate(145%);
}

.recruit-nav-links, .recruit-nav-account { display: flex; align-items: center; }
.recruit-nav-links { gap: 24px; }
.recruit-nav-account { margin-left: auto; gap: 22px; }
.recruit-nav-account button { display: inline-flex; align-items: center; gap: 7px; }
.recruit-account-menu { position: relative; }
.recruit-account-dropdown { position: absolute; top: calc(100% - 2px); right: 0; z-index: 30; min-width: 132px; padding: 6px; border: 1px solid #e8edf0; border-radius: 8px; background: #fff; box-shadow: 0 8px 24px rgb(26 55 72 / 14%); opacity: 0; pointer-events: none; transform: translateY(4px); transition: opacity 140ms ease, transform 140ms ease, visibility 140ms ease; visibility: hidden; }
.recruit-account-menu:hover .recruit-account-dropdown,
.recruit-account-menu:focus-within .recruit-account-dropdown { opacity: 1; pointer-events: auto; transform: translateY(0); visibility: visible; }
.recruit-account-dropdown button { width: 100%; height: 36px; padding: 0 10px; border-radius: 5px; color: #435a63; text-align: left; white-space: nowrap; }
.recruit-account-dropdown button:hover { background: #f1f7f8; color: #0f777d; }
.recruit-account-name { max-width: 120px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.recruit-subnav button {
  position: relative;
  height: 52px;
  padding: 0 4px;
  border: 0;
  background: transparent;
  color: #875222;
  cursor: pointer;
  font-size: 15px;
  transition: color 180ms ease, background-color 180ms ease;
}

.recruit-subnav button:focus-visible { outline: 2px solid #4de0d2; outline-offset: 3px; border-radius: 4px; }
@media (min-width: 1280px) and (max-width: 640px) {
  .home-jobs-heading { align-items: flex-start; flex-direction: column; gap: 14px; }
  .recruit-page { margin-top: -24px; }
  .recruit-subnav { flex-wrap: wrap; gap: 0 16px; padding: 0 14px; }
  .recruit-nav-links { gap: 20px; }
  .recruit-nav-account { width: 100%; justify-content: flex-end; gap: 20px; border-top: 1px solid #ffffff18; }
  .recruit-nav-account button { height: 44px; }
}
@media (prefers-reduced-motion: reduce) { .recruit-subnav, .recruit-subnav button { transition: none; } }

.recruit-subnav button:hover,
.recruit-subnav button.active {
  color: #d86616;
}

.recruit-subnav button.active::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 3px;
  border-radius: 3px 3px 0 0;
  background: #ef8b35;
  content: '';
}

.recruit-empty-section {
  display: grid;
  min-height: 360px;
  place-items: center;
  align-content: center;
  gap: 10px;
  border: 1px solid var(--recruit-line);
  border-radius: 16px;
  background: #fff;
  color: var(--recruit-muted);
  text-align: center;
}

.recruit-empty-section h2,
.recruit-empty-section p { margin: 0; }
.recruit-empty-section h2 { color: var(--recruit-ink); font-size: 20px; }
.recruit-empty-icon {
  display: grid;
  width: 52px;
  height: 52px;
  place-items: center;
  border-radius: 16px;
  color: #fff;
  background: var(--recruit-accent);
  font-size: 22px;
  font-weight: 700;
}

.recruit-hero {
  position: relative;
  display: grid;
  width: min(100%, 1280px);
  box-sizing: border-box;
  margin-inline: auto;
  gap: 24px;
  padding: 42px 52px 28px;
  border: 1px solid #f2d4ad;
  border-radius: 16px;
  background: linear-gradient(135deg, #fffaf3 0%, #fff6ea 62%, #ffe7c5 100%);
  box-shadow: 0 18px 46px rgb(183 92 24 / 10%);
}

.hero-copy {
  max-width: 760px;
}

.hero-visual {
  position: absolute;
  top: 22px;
  right: 34px;
  width: 250px;
  height: 142px;
  pointer-events: none;
}
.hero-orbit { position: absolute; border: 1px solid rgb(232 117 34 / 28%); border-radius: 50%; }
.hero-orbit-one { inset: 12px 22px 2px 32px; transform: rotate(22deg); }
.hero-orbit-two { inset: 0 4px 25px 60px; transform: rotate(-26deg); }
.hero-profile-card,
.hero-role-card {
  position: absolute;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid rgb(255 255 255 / 90%);
  border-radius: 12px;
  background: rgb(255 255 255 / 92%);
  box-shadow: 0 12px 28px rgb(183 92 24 / 12%);
}
.hero-profile-card { top: 34px; left: 22px; }
.hero-profile-card > span:not(.hero-avatar) { display: grid; gap: 3px; }
.hero-profile-card b { color: #5d341d; font-size: 13px; }
.hero-profile-card small { color: #a48268; font-size: 11px; }
.hero-profile-card i { color: var(--recruit-accent); font-size: 18px; font-style: normal; }
.hero-avatar { display: grid; width: 28px; height: 28px; place-items: center; border-radius: 9px; color: #fff; background: var(--recruit-accent); font-size: 13px; font-weight: 700; }
.hero-role-card { right: 2px; bottom: 4px; display: grid; gap: 3px; padding: 9px 12px; }
.hero-role-card span { color: #a48268; font-size: 10px; }
.hero-role-card b { color: #80502d; font-size: 12px; }

.hero-copy h1,
.hero-copy p {
  margin: 0;
}

.hero-copy h1 {
  margin-bottom: 8px;
  color: #4a2916;
  font-size: clamp(32px, 4vw, 46px);
  line-height: 1.18;
}

.hero-note {
  margin: 0;
  color: #475569;
  font-size: 17px;
  line-height: 1.7;
}

.eyebrow {
  margin: 0 0 8px;
  color: var(--recruit-accent);
  font-size: 22px;
  font-weight: 600;
}

.hero-search {
  display: flex;
  width: min(100%, 1040px);
  gap: 14px;
}

.hero-search .ant-input-affix-wrapper {
  flex: 1;
  min-height: 54px;
  padding-inline: 16px;
  background: #fff !important;
  border-color: #efc38e;
}

.hero-search :deep(.ant-input) {
  color: #334155;
  background: #fff !important;
}

.hero-search :deep(.ant-input::placeholder) {
  color: #94a3b8;
}

.hero-search .ant-btn {
  min-width: 132px;
  height: 54px;
  padding-inline: 24px;
  border-color: var(--recruit-accent);
  background: var(--recruit-accent);
  font-size: 16px;
}

.quick-searches {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 12px;
  color: #64748b;
  font-size: 13px;
}

.quick-searches button {
  padding: 5px 12px;
  border: 0;
  border-radius: 999px;
  color: #c85e18;
  background: #fff;
  cursor: pointer;
  font-size: 13px;
}

.quick-searches button:hover,
.quick-searches button:focus-visible {
  color: #fff;
  background: var(--recruit-accent);
  outline: none;
}

.quick-searches-muted { color: #64748b; }

.recruit-tabs {
  min-height: 420px;
}

.filter-panel {
  display: grid;
  width: min(100%, 1280px);
  box-sizing: border-box;
  gap: 14px;
  margin-bottom: 16px;
  margin-inline: auto;
  padding: 20px 22px;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  background: #fff;
}

.filter-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #eef2f7;
}

.filter-heading > div { display: grid; gap: 4px; }
.section-kicker { color: #2563eb; font-size: 12px; font-weight: 700; letter-spacing: .08em; }
.filter-heading strong { color: #0f172a; font-size: 18px; }
.result-count { color: #94a3b8; font-size: 13px; }

.filter-toolbar {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 12px;
}

.filter-control {
  width: 100%;
  min-width: 0;
}

.area-control :deep(.ant-select),
.area-control :deep(.ant-cascader-picker) {
  width: 100%;
  border-color: #dbe3ee;
  background: #fff !important;
}

.filter-panel :deep(.ant-select-selector) {
  border-color: #dbe3ee !important;
  background: #fff !important;
}

.filter-panel :deep(.ant-select-selection-item),
.filter-panel :deep(.ant-select-selection-placeholder),
.filter-panel :deep(.ant-cascader-picker-label) {
  color: #475569;
}

.filter-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.filter-label {
  min-width: 60px;
  color: #64748b;
  font-size: 13px;
}

.filter-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 14px;
  border-top: 1px solid #f1f5f9;
}

.reset-button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

:global(.recruit-filter-dropdown),
:global(.recruit-filter-dropdown .ant-select-dropdown),
:global(.recruit-filter-dropdown .ant-cascader-menus),
:global(.recruit-filter-dropdown .ant-cascader-menu) {
  color: #334155 !important;
  background: #fff !important;
}

:global(.recruit-filter-dropdown .ant-select-item),
:global(.recruit-filter-dropdown .ant-cascader-menu-item) {
  color: #334155 !important;
  background: #fff !important;
}

:global(.recruit-filter-dropdown .ant-select-item-option-active),
:global(.recruit-filter-dropdown .ant-select-item-option-selected),
:global(.recruit-filter-dropdown .ant-cascader-menu-item-active) {
  color: #1d4ed8 !important;
  background: #eff6ff !important;
}

.home-job-grid {
  display: grid;
  width: min(100%, 1280px);
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
  margin-inline: auto;
}

.home-jobs-heading { display: flex; align-items: end; justify-content: space-between; width: min(100%, 1180px); margin: 8px auto 18px; }
.home-jobs-heading h2 { margin: 0; color: #4a2916; font-size: 24px; }
.home-jobs-heading p { margin: 6px 0 0; color: #a48268; font-size: 13px; }
.home-job-categories { display: flex; gap: 8px; flex-wrap: wrap; }
.home-job-categories button { padding: 7px 14px; border: 0; border-radius: 999px; background: #fff; color: #8c7564; cursor: pointer; font-size: 13px; }
.home-job-categories button:hover, .home-job-categories button.active { background: #fff0df; color: #d86616; }

.home-job-grid .post-card {
  grid-template-columns: 1fr;
  gap: 18px;
  min-height: 228px;
  padding: 22px 22px 0;
  overflow: hidden;
  cursor: pointer;
}

.home-job-grid .post-card-main { min-width: 0; }
.home-job-grid .post-card-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-inline: -22px;
  padding: 13px 22px;
  background: #fffaf4;
}
.home-job-grid .post-apply { display: none; }

.show-more-posts {
  display: block;
  width: 276px;
  height: 60px;
  margin: 16px auto 4px;
  border: 0;
  border-radius: 12px;
  background: #d8f3f5;
  color: #08a6b3;
  font-size: 20px;
}

.show-more-posts:hover,
.show-more-posts:focus-visible {
  border: 0;
  background: #c6edf0;
  color: #078d99;
}

.search-results-layout {
  display: grid;
  width: min(100%, 1280px);
  grid-template-columns: minmax(320px, 410px) minmax(0, 1fr);
  gap: 18px;
  margin-inline: auto;
}

/* ── 职位页双栏骨架 ─────────────────────────────────────────────
   左栏是职位列表，右栏是所选职位的粘性详情。
   结果区本身不铺底色，融入门户自身背景；蓝色由卡片描边、薪资徽章
   与标题承担，绿色只留给动作。
   宽度不设自己的上限，直接吃满页面 1180px 内容列
   （见 .recruit-page 的 grid-template-columns），
   左右 padding 归零，好让 .jobs-split 与上方的筛选面板同宽对齐。
   margin-top 只留 8px：原先的 -60px 是为了把跟着「职位列表 / 找到适合你的工作」
   标题一起上提，标题已移除，那个负值会直接把列表压到筛选面板上。 */
.jobs-results-shell {
  display: grid;
  gap: 14px;
  margin-top: 8px;
  padding: 18px 0;
  background: transparent;
}

.jobs-split {
  display: grid;
  grid-template-columns: 384px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

/* 列表不自建滚动区：不设 max-height、不设 overflow，
   高度随卡片自然增长，滚动交给整页的浏览器滚动条。
   这样它和右栏同处一个文档流，两栏一起上下滑，不会各自困在固定高度的框里。
   触底加载改由 window 的 scroll 事件判定（见 handleJobsListScroll）。 */
.jobs-card-list {
  display: grid;
  gap: 8px;
}

/* 卡片只在"被创建"时播一次入场动画。
   v-for 以 post.id 为 key，追加新一屏时旧卡片是复用而非重建，
   所以动画天然只作用于新来的那 10 张，不会整列表重播。
   --enter-index 由模板按批内序号（0–9）注入，形成逐张错开的节奏。 */
.jobs-post-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  grid-template-rows: auto auto;
  column-gap: 10px;
  padding: 13px 15px;
  border: 1px solid var(--job-line);
  border-radius: 12px;
  background: #fff;
  cursor: pointer;
  animation: jobs-card-settle 460ms cubic-bezier(0.22, 0.61, 0.36, 1) both;
  animation-delay: calc(var(--enter-index, 0) * 42ms);
  transform-origin: center;
  transition: border-color 140ms ease, box-shadow 140ms ease;
}

/* 入场不是匀速升起，而是「被抽出来 → 冲过落点 → 回弹一次 → 收住」。
   过冲交给关键帧而不是缓动函数：单条 cubic-bezier 只能过冲一次，
   做不出「冲过头再荡回来」这股阻尼感。缓动仍用全页那条 ease-out，
   保证和吸顶工作台、卡片悬停是同一套动效语汇。 */
@keyframes jobs-card-settle {
  0% {
    opacity: 0;
    transform: translateY(24px) scale(0.97);
  }

  52% {
    opacity: 1;
    transform: translateY(-6px) scale(1.008);
  }

  76% {
    opacity: 1;
    transform: translateY(2px) scale(0.999);
  }

  100% {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.jobs-post-card:hover {
  border-color: #9dc4e8;
  box-shadow: 0 2px 8px rgb(47 111 179 / 7%);
}

.jobs-post-card.selected {
  border-color: var(--job-blue);
  /* 左缘蓝色竖条，与右栏详情构成一条视觉连线 */
  box-shadow: inset 3px 0 0 var(--job-blue), 0 3px 10px rgb(47 111 179 / 14%);
}

.jobs-post-company { grid-area: 1 / 1; display: flex; min-width: 0; align-items: center; gap: 8px; }
.jobs-post-company > img,
.jobs-post-company > span {
  display: grid;
  width: 26px;
  height: 26px;
  flex: 0 0 26px;
  place-items: center;
  border-radius: 7px;
  /* 同详情页：横版 logo 用 cover 会被裁掉两头，用 contain 完整缩进方块 */
  object-fit: contain;
  padding: 2px;
  color: var(--job-blue);
  background: var(--job-blue-soft);
  font-size: 12px;
  font-weight: 700;
}

.jobs-post-company div { min-width: 0; }
.jobs-post-company strong {
  display: block;
  overflow: hidden;
  color: var(--job-muted);
  font-size: 12px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 左栏只留可横向比较的字段；企业规模移入右栏详情，避免列表噪声 */
.jobs-post-company small { display: none; }

.jobs-post-content { grid-area: 2 / 1; min-width: 0; }

.jobs-post-title-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-top: 7px;
}
.jobs-post-title-row h3 {
  margin: 0;
  overflow: hidden;
  color: var(--job-ink);
  font-size: 15px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 薪资是第一决策信息，给独立徽章而非普通文字 */
.jobs-post-title-row b {
  flex: 0 0 auto;
  padding: 4px 8px;
  border-radius: 999px;
  color: var(--job-salary);
  background: #ffffff;
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
}

.jobs-post-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin-top: 7px;
  color: var(--job-muted);
  font-size: 12px;
}
.jobs-post-meta span { display: inline-flex; align-items: center; gap: 4px; }
.jobs-post-meta :deep(svg) { color: #7ba7d4; }

.jobs-post-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 9px; }
.jobs-post-tags span {
  padding: 3px 8px;
  border: 1px solid var(--job-green-line);
  border-radius: 999px;
  color: var(--job-green);
  background: var(--job-green-soft);
  font-size: 11px;
}

/* 收藏键：原来的书签图标换成星标，规格和首页「热门企业」那颗一致 ——
   裸星、无底板无描边，未收藏是灰色空心轮廓，收藏后填实变绿。
   填色只有 .active 才给：未收藏不写 fill，<path> 上那条 fill="none"
   表现属性就恢复作用，星形保持空心。 */
.jobs-post-favorite {
  grid-area: 1 / 2 / span 2 / span 1;
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  align-self: start;
  padding: 0;
  border: 0;
  background: transparent;
  color: #a3aebd;
  cursor: pointer;
  font-size: 18px;
  transition: color 160ms ease, transform 140ms ease;
}
.jobs-post-favorite.active :deep(svg *) { fill: currentColor; }
/* 底板和描边显式归零：这两条是 (0,2,0)，比上面基础规则的 (0,1,0) 重，
   光靠位置压不住，悬停时那块淡蓝底会自己冒回来。 */
.jobs-post-favorite:hover {
  border-color: transparent;
  background: transparent;
  color: var(--job-green);
  transform: scale(1.14);
}
.jobs-post-favorite:active { transform: scale(0.92); }
.jobs-post-favorite.active {
  border-color: transparent;
  background: transparent;
  color: var(--job-green);
}

/* ── 列表底部：加载状态 ────────────────────────────────────────
   三种状态共用一行，高度固定，切换时不引起列表跳动。
   它同时是"下滑还有内容"的提示，因此默认态也占位而非隐藏。 */
.jobs-list-footer {
  position: relative;
  display: flex;
  min-height: 42px;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 6px 0 2px;
  color: var(--job-muted);
  font-size: 12px;
}
.jobs-list-footer :deep(svg) { color: #9dc4e8; font-size: 15px; }

/* 加载态在列表下沿抻开一条线：两端淡出、由中段向两侧拉满，
   像列表底边被拽开一道缝。比单纯转圈更贴「正在往后取内容」这件事，
   也把这一屏的「粘」带进来。用 scaleX 而不是 width —— 前者走合成层，
   不会每帧触发重排。 */
.jobs-list-footer.is-loading::after {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 2px;
  border-radius: 999px;
  background: linear-gradient(90deg, transparent, var(--job-blue), transparent);
  content: '';
  transform-origin: center;
  animation: jobs-list-pull 1150ms cubic-bezier(0.5, 0, 0.5, 1) infinite;
}

@keyframes jobs-list-pull {
  0% {
    transform: scaleX(0.16);
    opacity: 0;
  }

  32% {
    opacity: 1;
  }

  72% {
    transform: scaleX(1);
    opacity: 1;
  }

  100% {
    transform: scaleX(1);
    opacity: 0;
  }
}

/* 提示态的下箭头缓慢上下浮动，暗示"继续滑" */
.jobs-list-hint-icon { animation: jobs-hint-bob 1.8s ease-in-out infinite; }

@keyframes jobs-hint-bob {
  0%,
  100% {
    transform: translateY(-2px);
    opacity: 0.55;
  }

  50% {
    transform: translateY(2px);
    opacity: 1;
  }
}

.jobs-list-footer.is-loading { color: var(--job-blue); }

.jobs-list-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid var(--job-blue-soft);
  border-top-color: var(--job-blue);
  border-radius: 50%;
  animation: jobs-spin 720ms linear infinite;
}

@keyframes jobs-spin {
  to {
    transform: rotate(360deg);
  }
}

/* 尊重系统的「减少动态效果」设置：去掉入场错峰与浮动，保留状态切换 */
@media (prefers-reduced-motion: reduce) {
  .jobs-post-card {
    animation: none;
  }

  .jobs-list-hint-icon {
    animation: none;
  }

  /* 抻开的线也停掉 —— 加载态本来就还有转圈，不缺指示 */
  .jobs-list-footer.is-loading::after {
    animation: none;
    opacity: 0;
  }
}

/* ── 右栏：所选职位详情 ───────────────────────────────────────
   四段式：企业行 → 标题行 → 条款带 → 正文面板。
   前三段常驻在滚动区之外，构成不用滚动就能读完的「岗位速览」；
   正文面板是唯一滚动区，描述 / 职位信息 / 工作地址顺序排完。
   卡片高度跟随视口（见下方 max-height），sticky 时右栏始终留在屏幕内。 */
.jobs-detail-card {
  position: sticky;
  /* 让开吸顶工作台：导航条高度 + 收拢后的工作台高度 + 间距。
     工作台只有收拢后才会和它同屏，所以按收拢值算一个数就够。 */
  top: calc(var(--jobs-topbar-h, 60px) + var(--jobs-bar-h) + 14px);
  z-index: 1; /* 低于工作台的 30：收拢期间两者若短暂重叠，工作台要压在上面 */
  display: grid;
  /* 常驻前两行是固定高度，正文行用 minmax(0, 1fr) 才能被压到 0 ——
     否则卡片顶到 max-height 时溢出的是整块内容，而不是正文自己滚动。 */
  grid-template-rows: auto auto minmax(0, 1fr);
  /* 卡片高度锁死在「导航条 + 吸顶工作台之下、视口之内」这一段：
     min 和 max 取同一个式子，于是它永远刚好占满右栏可视高度。
     少了这一条，左栏职位列表把整列撑到一千六百多像素，卡片却只有八百多，
     下面七八百像素是纯白 —— 那才是这一页看着「空」的真正来源。
     末尾扣除的是两个位置上更紧的那个：卡片吸顶后距顶 14px，静止时却排在
     工作区自己的标题之后、比吸顶位置低 80px。按 80 算两处都收得进视口，
     按 14 算静止那一刻下沿会把卡片切掉五十多像素。
     矮屏下内容顶到上限就转成正文内部滚动，压底的「招聘方」仍够得着。 */
  min-height: calc(
    100vh - var(--jobs-topbar-h, 60px) - var(--jobs-bar-h) - 80px
  );
  max-height: calc(
    100vh - var(--jobs-topbar-h, 60px) - var(--jobs-bar-h) - 80px
  );
  overflow: hidden;
  border: 1px solid var(--job-line);
  border-radius: 16px;
  background: #fff;
}

/* ① 企业行 */
.job-detail-company {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 26px;
  border-bottom: 1px solid var(--job-line);
  background: linear-gradient(180deg, #f3f9ff 0%, #fff 100%);
}

.job-detail-company-main {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.job-detail-company-main > img,
.job-detail-company-logo-fallback {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  place-items: center;
  border: 1px solid var(--job-line);
  border-radius: 10px;
  background: #fff;
  /* contain 而不是 cover：企业 logo 多是横版带字（实测 2362×945，比例 2.5:1），
     cover 会把左右两头裁掉，只剩中间一截认不出是谁家。内缩一点让图形不贴边。 */
  object-fit: contain;
  padding: 3px;
  color: var(--job-blue);
  font-size: 15px;
  font-weight: 700;
}

.job-detail-company-text { display: grid; min-width: 0; gap: 2px; }
.job-detail-company-text strong {
  overflow: hidden;
  color: var(--job-ink);
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.job-detail-company-text small {
  overflow: hidden;
  color: var(--job-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.job-detail-company-link {
  flex: 0 0 auto;
  color: #a8bccf;
  font-size: 14px;
  transition: color 140ms ease;
}
.job-detail-company-main:hover .job-detail-company-link { color: var(--job-blue); }
.job-detail-company-main:focus-visible {
  outline: 2px solid var(--job-blue);
  outline-offset: 3px;
  border-radius: 8px;
}

/* 与列表卡片、首页企业卡同一颗星：裸星，空心灰 → 实心绿 */
.job-detail-favorite {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  place-items: center;
  padding: 0;
  border: 0;
  background: transparent;
  color: #a3aebd;
  cursor: pointer;
  font-size: 19px;
  transition: color 160ms ease, transform 140ms ease;
}
.job-detail-favorite.active :deep(svg *) { fill: currentColor; }
.job-detail-favorite:hover {
  border-color: transparent;
  background: transparent;
  color: var(--job-green);
  transform: scale(1.14);
}
.job-detail-favorite:active { transform: scale(0.92); }
.job-detail-favorite.active {
  border-color: transparent;
  background: transparent;
  color: var(--job-green);
}

/* ② 标题行 */
.job-detail-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  padding: 18px 26px 16px;
}

.job-detail-heading { min-width: 0; }
.job-detail-heading h2 {
  margin: 0;
  color: var(--job-ink);
  font-size: 26px;
  font-weight: 600;
  line-height: 1.3;
}

/* 薪资是决策信息，与职位名同排；用竖线分隔而非徽章，避免和左栏列表撞款 */
.job-detail-salary-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 14px;
  margin: 12px 0 0;
  color: var(--job-muted);
  font-size: 13px;
}
.job-detail-salary-line b {
  color: var(--job-salary);
  font-size: 19px;
  font-weight: 700;
}
.job-detail-salary-line span { position: relative; padding-left: 15px; }
.job-detail-salary-line span::before {
  position: absolute;
  top: 50%;
  left: 0;
  width: 1px;
  height: 11px;
  background: var(--job-line);
  content: '';
  transform: translateY(-50%);
}

.job-detail-action { flex: 0 0 auto; }
/* 绿只留给动作：主 CTA 用实心绿。
   本文件有多条 !important 的 .ant-btn-primary 共用规则（如
     .recruit-page .search-detail-actions .ant-btn-primary  (0,3,0)），
   同为 !important 时比特异性，故此处用更高特异性接管，
   不改动那些共用规则 —— 它们仍服务于页面其它分支。 */
.recruit-page--jobs .jobs-detail-card .job-detail-action :deep(.ant-btn-primary) {
  height: 40px;
  padding: 0 22px;
  border-color: var(--job-green) !important;
  border-radius: 999px !important;
  background: var(--job-green) !important;
  box-shadow: 0 6px 14px rgb(12 150 83 / 18%) !important;
  color: #fff !important;
  font-weight: 600;
}
.recruit-page--jobs .jobs-detail-card .job-detail-action :deep(.ant-btn-primary:hover),
.recruit-page--jobs .jobs-detail-card .job-detail-action :deep(.ant-btn-primary:focus-visible) {
  border-color: #0a7f46 !important;
  background: #0a7f46 !important;
}

/* 正文区：不自建滚动，高度随内容长，跟着整页一起滑。
   描述、地址、信息三段一页连排，滚到哪都能同时看到上面的岗位速览。
   这里的间距比常规略紧：卡片整张要用 position:sticky 钉在视口里，
   高度直接决定它在多矮的屏幕上会被裁掉，紧凑一点能多覆盖若干分辨率。 */
/* 只有卡片被 max-height 压住时这里才真的滚得动；高屏下内容比可视区矮，
   滚动条不出现，视觉上仍是「整页一起滚」。 */
.job-detail-panel {
  padding: 18px 26px 20px;
  overflow-y: auto;
  /* 滚到正文两端时不要把手势传给整页，否则会连带把工作台滚回展开态 */
  overscroll-behavior: contain;
  scrollbar-width: thin;
  scrollbar-color: #c4d8ee transparent;
}
.job-detail-panel::-webkit-scrollbar {
  width: 6px;
}
.job-detail-panel::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: #c4d8ee;
}
.job-detail-panel::-webkit-scrollbar-track {
  background: transparent;
}

/* 段落之间用细线分段，替代原先的页签切换 —— 一页里靠留白和分隔线区分层级 */
.job-detail-block + .job-detail-block {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--job-line);
}

/* 章节标题带一道左侧色条。这一块要在一屏多长的滚动里反复定位，
   只把字改成蓝色是不够的 —— 色条是结构不是动作，所以走蓝不走绿。 */
.job-detail-block h3 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 12px;
  color: var(--job-ink);
  font-size: 15px;
  font-weight: 600;
}
.job-detail-block h3::before {
  flex: 0 0 auto;
  width: 3px;
  height: 14px;
  border-radius: 999px;
  background: var(--job-blue);
  content: '';
}

.job-detail-copy p {
  margin: 0 0 8px;
  color: var(--job-muted);
  font-size: 14px;
  line-height: 1.85;
}
.job-detail-copy p:last-child { margin-bottom: 0; }
/* 中文 JD 多数用 -、•、1. 分行，按列表项渲染比一坨段落好扫 */
.job-detail-copy p.is-bullet { position: relative; padding-left: 15px; }
.job-detail-copy p.is-bullet::before {
  position: absolute;
  top: 0;
  left: 2px;
  color: #9dc4e8;
  content: '•';
}

/* 描述空态。它不是「没内容所以留白」，而是这页的常态，
   所以给一块有边界的底，让这一节仍然读得出结构。 */
.job-detail-blank {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 15px 18px;
  border: 1px dashed var(--job-line);
  border-radius: 12px;
  background: var(--job-blue-soft);
}
.job-detail-blank :deep(svg) {
  flex: 0 0 auto;
  margin-top: 2px;
  color: var(--job-blue);
  font-size: 18px;
}
.job-detail-blank p { display: grid; gap: 5px; margin: 0; }
.job-detail-blank strong { color: var(--job-ink); font-size: 14px; }
.job-detail-blank span { color: var(--job-muted); font-size: 13px; line-height: 1.7; }

/* 招聘方跟在地址之后顺排，不钉底。试过用 margin-top: auto 把它压到卡片下沿，
   结果两百多像素的空白被夹在地图和分隔线之间，看着像「缺了一块」；
   顺排时同样的空白落在卡片末尾，而卡片底边正好齐视口下沿，
   那一段是连着屏幕外的，读起来是内边距而不是空档。 */
/* 地图下方的「去完整详情页」出口。用描边而不是实心绿 ——
   绿在本页只留给「立即沟通」那一个主 CTA，这里只是次级跳转。
   通栏是因为它落在正文最后一块的收尾位置，通栏才收得住。 */
.job-detail-more {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  margin-top: 14px;
  padding: 11px 16px;
  border: 1px solid var(--job-line);
  border-radius: 10px;
  background: #fff;
  color: var(--job-blue);
  cursor: pointer;
  font-size: 13.5px;
  font-weight: 600;
  transition: border-color 140ms ease, background-color 140ms ease;
}
.job-detail-more:hover {
  border-color: var(--job-blue);
  background: var(--job-blue-soft);
}
.job-detail-more:focus-visible {
  outline: 2px solid var(--job-blue);
  outline-offset: 2px;
}

.job-detail-recruiter {
  display: flex;
  align-items: center;
  gap: 12px;
}
.job-detail-recruiter-avatar {
  display: grid;
  width: 42px;
  height: 42px;
  flex: 0 0 42px;
  place-items: center;
  border-radius: 12px;
  background: var(--job-blue-soft);
  overflow: hidden;
  color: var(--job-blue);
  font-size: 16px;
  font-weight: 600;
}
.job-detail-recruiter-avatar img { width: 100%; height: 100%; object-fit: cover; }
.job-detail-recruiter-text { display: grid; min-width: 0; gap: 3px; }
.job-detail-recruiter-text strong { color: var(--job-ink); font-size: 14px; font-weight: 600; }
.job-detail-recruiter-text small {
  overflow: hidden;
  color: var(--job-muted);
  font-size: 12.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-result-list,
.search-detail-card {
  min-width: 0;
  border: 1px solid var(--recruit-line);
  border-radius: 12px;
  background: #fff;
}

.search-result-list { overflow: hidden; }
.search-result-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px;
  border-bottom: 1px solid var(--recruit-line);
}
.search-result-heading strong { color: var(--recruit-ink); font-size: 17px; }
.search-result-heading span { color: var(--recruit-muted); font-size: 13px; }
.search-post-card {
  display: grid;
  gap: 10px;
  padding: 18px 20px;
  border-bottom: 1px solid #f0f3f2;
  cursor: pointer;
  transition: background 160ms ease;
}
.search-post-card:last-child { border-bottom: 0; }
.search-post-card:hover,
.search-post-card.selected { background: var(--recruit-accent-soft); }
.search-post-topline,
.search-detail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.search-post-topline strong { color: var(--recruit-ink); font-size: 16px; }
.search-post-topline b,
.search-detail-actions strong { color: #ef665c; font-size: 17px; white-space: nowrap; }
.search-post-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  color: var(--recruit-muted);
  font-size: 13px;
}
.search-post-company { color: #52615c; font-size: 13px; }
.search-detail-card { padding: 30px 34px; }
.search-detail-header { padding-bottom: 26px; border-bottom: 1px solid var(--recruit-line); }
.search-detail-header h2 { margin: 0 0 12px; color: var(--recruit-ink); font-size: 28px; }
.search-detail-actions { display: grid; justify-items: end; gap: 16px; }
.search-detail-actions .ant-btn { min-width: 112px; background: var(--recruit-accent); border-color: var(--recruit-accent); }
.detail-action-row { display: flex; gap: 10px; }
.detail-action-row .ant-btn { height: 40px; }
.detail-favorite { color: var(--recruit-accent) !important; background: #fff !important; border-color: var(--recruit-accent) !important; }
.favorite-button,
.search-favorite { display: inline-flex; align-items: center; gap: 5px; border: 0; color: #81908b; background: transparent; cursor: pointer; font-size: 13px; }
.favorite-button:hover,
.favorite-button.active,
.search-favorite:hover,
.search-favorite.active { color: #f2a93b; }
.search-favorite { justify-self: start; padding: 0; }
.search-detail-section { padding: 28px 0 12px; color: #596862; font-size: 15px; line-height: 1.9; }
.search-detail-section h3 { margin: 0 0 14px; color: var(--recruit-ink); font-size: 18px; }
.search-detail-section p { margin: 0 0 7px; }
.detail-summary { padding-top: 10px; border-top: 1px solid var(--recruit-line); }

.post-list {
  display: grid;
  width: min(100%, 1280px);
  margin-inline: auto;
  gap: 12px;
}

.post-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  width: min(100%, 1280px);
  align-items: center;
  justify-self: center;
  gap: 28px;
  padding: 22px 26px;
  border-radius: 12px;
  border: 1px solid #dbe3ee;
  background: #fff;
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease,
    transform 160ms ease;
}

.post-card:hover,
.post-card:has(.post-title:hover) {
  border-color: #93c5fd;
  box-shadow: 0 8px 24px rgb(30 64 175 / 9%);
  transform: translateY(-1px);
}

.post-title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 16px;
}

.post-title {
  display: inline-flex;
  max-width: 100%;
  padding: 0;
  border: 0;
  color: #0f172a;
  background: transparent;
  cursor: pointer;
  font: inherit;
  font-size: 20px;
  font-weight: 700;
  line-height: 1.35;
  text-align: left;
  transition: color 160ms ease;
}

.post-company-row,
.post-tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 12px;
  color: #64748b;
  font-size: 13px;
}

.post-company-row { margin-top: 9px; color: #475569; font-weight: 600; }
.post-type-tag,
.post-tags span { padding: 3px 8px; border-radius: 4px; background: #f1f5f9; font-weight: 400; }
.post-excerpt { margin-top: 9px; }
.post-tags { margin-top: 12px; }

.post-title:hover,
.post-title:focus-visible {
  color: #2563eb;
  outline: none;
  text-decoration: underline;
  text-underline-offset: 4px;
}

.post-location {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #64748b;
  font-size: 14px;
}

.post-location :deep(svg) {
  color: #2563eb;
  font-size: 16px;
}

.post-excerpt {
  margin: 10px 0 0;
  max-width: 900px;
  overflow: hidden;
  color: #64748b;
  font-size: 14px;
  line-height: 1.65;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.post-card-actions {
  display: grid;
  min-width: 0;
  align-items: center;
  justify-content: flex-end;
}

.post-salary {
  color: #f05b52;
  font-size: 20px;
  white-space: nowrap;
}

.post-apply {
  min-width: 112px;
  height: 42px;
  padding-inline: 24px;
  border-color: #2563eb;
  border-radius: 6px;
  background: #2563eb;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
}

.post-apply:hover,
.post-apply:focus-visible {
  border-color: #1d4ed8;
  background: #1d4ed8;
  color: #fff;
}

.portal-empty {
  display: grid;
  width: 100%;
  box-sizing: border-box;
  grid-template-columns: minmax(0, 1fr);
  min-height: 220px;
  place-items: center;
  align-content: center;
  justify-items: center;
  gap: 8px;
  padding: 32px 20px;
  border: 1px dashed #d1d5db;
  background: #fff;
  color: #64748b;
  text-align: center;
}

.portal-empty-icon {
  display: grid;
  width: 52px;
  min-width: 52px;
  height: 52px;
  min-height: 52px;
  flex: 0 0 52px;
  place-items: center;
  border-radius: 50%;
  color: #2563eb;
  background: #eff6ff;
  font-size: 24px;
}

.portal-empty-icon :deep(svg) {
  width: 24px;
  height: 24px;
}

.portal-empty strong {
  color: #334155;
  font-size: 15px;
  font-weight: 600;
}

.portal-empty p {
  margin: 0;
  color: #94a3b8;
  font-size: 13px;
}

.post-list-loading .post-card {
  cursor: default;
}

@media (min-width: 1280px) and (max-width: 900px) {
  .service-grid { grid-template-columns:repeat(2,1fr); }
  .service-heading { display:block; }.service-heading > span { display:block; margin-top:8px; }
}
@media (min-width: 1280px) and (max-width: 560px) {
  .service-grid { grid-template-columns:1fr; }
}
@media (min-width: 1280px) and (min-width: 641px) and (max-width: 900px) {
  .hero-visual { opacity: .38; right: 14px; }

  .home-job-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .search-results-layout { grid-template-columns: 1fr; }

  .filter-toolbar {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .post-card {
    gap: 20px;
    padding: 20px;
  }
}

@media (min-width: 1280px) and (max-width: 640px) {
  .recruit-hero {
    padding: 28px 20px 22px;
  }

  .hero-copy h1 {
    font-size: 28px;
  }

  .hero-visual { display: none; }

  .hero-note {
    font-size: 15px;
  }

  .hero-search {
    width: 100%;
    flex-direction: column;
  }

  .hero-search .ant-btn {
    width: 100%;
    height: 50px;
  }

  .filter-toolbar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .home-job-grid { grid-template-columns: 1fr; }

  .filter-heading { align-items: flex-start; flex-direction: column; gap: 6px; }

  .search-detail-card { padding: 22px 20px; }
  .search-detail-header { flex-direction: column; }
  .search-detail-actions { width: 100%; justify-items: stretch; }
  .search-detail-actions .ant-btn { width: 100%; }
  .detail-action-row { width: 100%; }
  .detail-action-row .ant-btn { flex: 1; }

  .post-card {
    grid-template-columns: 1fr;
    gap: 12px;
    padding: 15px;
  }

  .post-title {
    font-size: 18px;
  }

  .post-title-row {
    align-items: flex-start;
    gap: 8px 12px;
  }

  .post-card-actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .post-apply {
    width: 100%;
  }

}
.recruit-page { color: #5d4636; }
.recruit-page .filter-panel, .recruit-page .post-card, .recruit-page .search-detail-card, .recruit-page .recruit-tabs :deep(.ant-tabs-content-holder) { border-color: #f4e5d7; background: #fff; box-shadow: 0 8px 22px rgb(183 92 24 / 4%); }
.recruit-page .section-kicker, .recruit-page .search-detail-section h3 { color: #d86616; }
.recruit-page .post-apply, .recruit-page .search-detail-actions .ant-btn-primary, .recruit-page .show-more-posts { border-color: #efc89f; background: linear-gradient(135deg, #f8d8b2, #efb77d); color: #8a4a21; box-shadow: 0 5px 12px rgb(183 92 24 / 8%); }
.recruit-page .post-apply:hover, .recruit-page .search-detail-actions .ant-btn-primary:hover, .recruit-page .show-more-posts:hover { border-color: #e5ae77; background: linear-gradient(135deg, #efb77d, #e99d5b); }
.recruit-page .post-tags span, .recruit-page .search-post-card.selected { background: #fff7ed; }
.recruit-page .recruit-tabs :deep(.ant-tabs-nav) { display: none; }
.home-jobs-heading { display: grid; width: 100%; justify-items: center; padding: 10px 75px 10px; border-bottom: 1px solid #f2e7dc; }
.home-jobs-heading h2 { width: 100%; justify-self: stretch; margin-bottom: 22px; text-align: center; font-size: 30px; font-weight: 800; letter-spacing: -.02em; }
.home-job-categories { display: flex; justify-content: center; gap: 34px; width: 100%; }
.recruit-page .home-recruit-tabs :deep(.ant-tabs-content-holder) { background: transparent; box-shadow: none; }
.recruit-page--jobs .recruit-tabs :deep(.ant-tabs-content-holder) { border-color: transparent; background: transparent; box-shadow: none; }
.home-job-grid { gap: 16px; }
.home-job-grid .post-card { min-height: 232px; border: 1px solid #eee4da; border-radius: 10px; background: #f4f6f8; box-shadow: 0 4px 14px rgb(65 43 26 / 4%); transition: transform 160ms ease, box-shadow 160ms ease, border-color 160ms ease; }
.home-job-grid .post-card:hover { border-color: #efcba8; box-shadow: 0 12px 24px rgb(183 92 24 / 10%); transform: translateY(-2px); }
.home-job-grid .post-title { color: #2f241e; font-size: 17px; font-weight: 700; }
.home-job-grid .post-title-row { justify-content: space-between; }
.home-job-grid .post-salary-top { display: block; color: #f05a4f; font-size: 16px; }
.home-job-grid .post-card-main { display: grid; align-content: start; gap: 14px; }
.home-job-grid .post-tags { display: flex; gap: 8px; margin-top: 0; }
.home-job-grid .post-tags span { padding: 4px 9px; border-radius: 3px; background: #fff; color: #806956; font-size: 12px; }
.home-job-grid .post-title:hover { color: #d86616; }
.home-job-grid .post-location { color: #8d7a6c; font-size: 12px; }
.home-job-grid .post-company-row { color: #6b5748; font-size: 13px; }
.home-job-grid .post-type-tag, .home-job-grid .post-tags span { background: #faf5f0; color: #806956; }
.home-job-grid .post-excerpt { color: #9a8879; font-size: 12px; }
.home-job-grid .post-card-actions { margin-inline: -22px; border-top: 1px solid #eee4da; background: #f4f6f8; }
.home-job-grid .post-salary { color: #e45f45; font-size: 17px; }
.home-job-grid .favorite-button { color: #a18e7d; }
.post-company-meta, .company-meta-detail { display: inline-flex; align-items: center; color: #6f6258; font-size: 12px; }
.post-company-meta { gap: 8px; }
.company-logo { display: grid; width: 24px; height: 24px; place-items: center; overflow: hidden; border-radius: 3px; background: #fff; color: #d86616; font-weight: 700; }
.company-logo img { width: 100%; height: 100%; object-fit: contain; }
.company-meta-detail { margin-left: auto; }
.company-meta-detail i { margin: 0 7px; color: #d7c8bb; font-style: normal; }
.show-more-posts { width: 210px; height: 46px; border: 1px solid #f0c9a2; border-radius: 8px; background: linear-gradient(135deg, #fff7ed, #ffe9cf); color: #cc6a2a; font-size: 15px; }
.show-more-posts:hover, .show-more-posts:focus-visible { border-color: #eeb37b; background: linear-gradient(135deg, #ffedda, #ffdfb9); color: #b9581d; }
:global(.profile-setup-modal .ant-modal-content), :global(.profile-setup-modal .ant-modal-body) { background: #fff; }
:global(.profile-setup-modal .ant-modal-content) { padding: 0; }
:global(.profile-setup-modal .ant-modal-header) { background: #fff; }
:global(.profile-setup-modal .ant-modal-close), :global(.profile-setup-modal .ant-modal-close .anticon) { color: #000 !important; }
:global(.profile-setup-modal .ant-modal-close:hover), :global(.profile-setup-modal .ant-modal-close:focus-visible) { color: #000 !important; }

.recruit-page {
  --recruit-ink: #17343d;
  --recruit-muted: #6a7d82;
  --recruit-line: #dce8e7;
  --recruit-accent: #c96632;
  --recruit-accent-soft: #edf7f5;
  /* 招聘页共用配色（70% 淡蓝 / 30% 淡绿）：
     淡蓝承载结构与信息 —— 边框、标题、薪资、选中态；
     淡绿只承载动作 —— 立即沟通、收藏、职位标签。
     定义在基类上，职位页与公司页共用同一份，避免两页各抄一套值后走样。 */
  --job-ink: #1b2a3d;
  --job-muted: #5f7288;
  --job-blue: #2f6fb3;
  --job-blue-soft: #e8f1fb;
  --job-line: #d7e5f4;
  --job-green: #0c9653;
  --job-green-soft: #e6fcf1;
  --job-green-line: #bcebd2;
  /* 薪资用红：用户指定的例外，破了上面「蓝承载信息、绿承载动作」的分工。
     职位页两处薪资都取它 —— 左栏卡片的 .jobs-post-title-row b
     和右栏详情的 .job-detail-salary-line b，两栏并排，必须同色。
     首页热门职位卡的薪资不走这个 token（那条被 .home-job-grid 的 #dc3545 接管）。
     要还原成蓝色：删掉这行，并把上述两处规则的 color 改回 var(--job-blue)。 */
  --job-salary: #ff0000;
  gap: 18px;
  padding-bottom: 24px;
  background: radial-gradient(circle at 80% 3%, rgb(211 239 232 / 62%), transparent 26rem);
}

.recruit-subnav {
  min-height: 60px;
  border-color: rgb(220 232 231 / 90%);
  background: rgb(255 255 255 / 90%);
  box-shadow: 0 8px 24px rgb(23 52 61 / 8%);
}

.recruit-subnav button { height: 58px; color: #587178; font-weight: 600; }
.recruit-subnav button:hover,
.recruit-subnav button.active { color: var(--recruit-ink); }
.recruit-subnav button.active::after { background: linear-gradient(90deg, #c96632, #e6a05b); }
.recruit-nav-account button { color: #587178; }
.recruit-nav-account button:hover { color: var(--recruit-accent); }

.recruit-hero {
  min-height: 290px;
  padding: 48px 56px 34px;
  border: 0;
  border-radius: 24px;
  overflow: hidden;
  background-color: #163a45;
  background-image:
    linear-gradient(120deg, rgb(19 55 65 / 98%), rgb(26 78 83 / 94%) 62%, rgb(201 102 50 / 88%)),
    repeating-linear-gradient(90deg, rgb(255 255 255 / 5%) 0 1px, transparent 1px 42px),
    repeating-linear-gradient(0deg, rgb(255 255 255 / 4%) 0 1px, transparent 1px 42px);
  box-shadow: 0 22px 44px rgb(23 52 61 / 18%);
}

.hero-copy h1 { max-width: 680px; color: #fff; font-size: clamp(34px, 4vw, 52px); letter-spacing: -.045em; }
.eyebrow { color: #8de0d1; font-size: 17px; font-weight: 700; letter-spacing: .16em; }
.hero-note { color: rgb(255 255 255 / 72%); }
.hero-visual { opacity: .88; }
.hero-orbit { border-color: rgb(141 224 209 / 42%); }
.hero-profile-card,
.hero-role-card { border-color: rgb(255 255 255 / 20%); background: rgb(255 255 255 / 14%); box-shadow: 0 16px 30px rgb(0 0 0 / 18%); backdrop-filter: blur(10px); }
.hero-profile-card b, .hero-role-card b { color: #fff; }
.hero-profile-card small, .hero-role-card span { color: rgb(255 255 255 / 68%); }
.hero-avatar { background: #c96632; }
.hero-profile-card i { color: #8de0d1; }
.hero-search .ant-input-affix-wrapper { border: 0; box-shadow: 0 8px 20px rgb(0 0 0 / 12%); }
.hero-search .ant-btn { border-color: #c96632; background: #c96632; box-shadow: 0 8px 18px rgb(0 0 0 / 16%); }
.hero-search .ant-btn:hover { border-color: #df7c43; background: #df7c43; }
.quick-searches { color: rgb(255 255 255 / 68%); }
.quick-searches button { color: #dff8f3; background: rgb(255 255 255 / 12%); }
.quick-searches button:hover, .quick-searches button:focus-visible { color: #17343d; background: #8de0d1; }

.home-jobs-heading { padding: 18px 0 20px; border-bottom-color: var(--recruit-line); }
.home-jobs-heading {
  grid-template-columns: minmax(0, 1fr);
  justify-items: stretch;
}
.home-jobs-heading h2 {
  width: 100%;
  color: var(--recruit-ink);
  font-size: 28px;
}
.home-job-categories {
  width: 100%;
  gap: 10px;
}
.home-job-categories button {
  padding: 5px 25px;
  border: 1px solid #fff;
  border-radius: 999px;
  background: #fff;
  color: #806956;
  cursor: pointer;
  font-size: 13px;
}
.home-job-categories button:hover, .home-job-categories button.active { border-color: #b9e2da; color: #16756f; background: #edf7f5; }

.home-job-grid { gap: 18px; }
.home-job-grid .post-card { min-height: 160px; border: 1px solid var(--recruit-line); border-radius: 16px; background: #fff; box-shadow: 0 8px 20px rgb(23 52 61 / 6%); }
.home-job-grid .post-card:hover { border-color: #9ed4cc; box-shadow: 0 16px 30px rgb(23 52 61 / 11%); transform: translateY(-4px); }
.home-job-grid .post-title { color: var(--recruit-ink); font-size: 18px; }
.home-job-grid .post-salary-top { color: var(--recruit-accent); font-weight: 800; }
.home-job-grid .post-tags span { border-radius: 999px; color: #5c777b; background: #f1f7f5; }
.home-job-grid .post-card-actions { border-top-color: var(--recruit-line); background: #f7fbfa; }
.company-meta-detail { color: #587178; }

.home-job-card {
  grid-template-rows: minmax(0, 1fr) auto;
  min-height: 220px !important;
  gap: 14px !important;
  padding: 24px 22px 0 !important;
}

.home-job-card .post-card-main {
  display: grid;
  align-content: start;
  gap: 18px;
}

.home-job-card .post-title-row {
  align-items: baseline;
}

.home-job-card .post-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-job-card .post-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 0;
}

.home-job-card .post-tags span {
  padding: 5px 10px;
  border: 1px solid #d7e9ed;
  border-radius: 6px;
  color: #587178;
  background: #f1f7f5;
  font-size: 13px;
  line-height: 1.2;
}

.home-job-card .post-card-actions {
  min-height: 54px;
  margin-inline: -22px;
  padding: 13px 22px;
}

.home-job-card .post-card-company {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
  overflow: hidden;
  color: #425d64;
  font-size: 15px;
  white-space: nowrap;
}

.home-job-card .post-card-company :deep(svg) {
  flex: 0 0 auto;
  color: var(--recruit-accent);
  font-size: 17px;
}

.home-job-card .post-card-company > span {
  overflow: hidden;
  text-overflow: ellipsis;
}

.home-job-card .company-meta-detail {
  flex: 0 0 auto;
  color: #425d64;
  font-size: 15px;
}

.service-section { padding-top: 24px; }
.service-heading .section-kicker { font-size: 15px; }
.service-heading h2 { color: var(--recruit-ink); }
.service-grid article { border-color: var(--recruit-line); border-radius: 16px; box-shadow: 0 6px 18px rgb(23 52 61 / 5%); }
.service-grid article:hover { border-color: #9ed4cc; box-shadow: 0 14px 28px rgb(23 52 61 / 10%); }
.service-icon { color: #16756f; background: #edf7f5; }
.service-grid h3 { color: var(--recruit-ink); }
.service-grid a { color: var(--recruit-accent); font-weight: 650; }

.filter-panel,
.search-result-list,
.search-detail-card { border-color: var(--recruit-line); border-radius: 16px; box-shadow: 0 8px 22px rgb(23 52 61 / 6%); }
.filter-heading { border-bottom-color: var(--recruit-line); }
.section-kicker { color: #16756f; }
.filter-heading strong, .search-result-heading strong, .search-detail-header h2 { color: var(--recruit-ink); }
.search-post-card:hover, .search-post-card.selected { background: #edf7f5; }
.search-post-topline b, .search-detail-actions strong { color: var(--recruit-accent); }
.search-detail-actions .ant-btn-primary { border-color: var(--recruit-accent); background: var(--recruit-accent); }
.search-detail-actions .ant-btn-primary:hover { border-color: #df7c43; background: #df7c43; }

.recruit-page > .recruit-hero,
.recruit-page > .recruit-tabs {
  grid-column: 1 / -1;
  width: 100%;
  margin-left: 0;
  box-sizing: border-box;
}

/* 公司页与首页、职位页共用同一条 1180px 内容基线。 */
.recruit-page > .company-directory {
  grid-column: 2;
  width: min(1180px, 100%);
  margin: 0;
  box-sizing: border-box;
}
 .recruit-page > .recruit-tabs {
  padding-right: max(16px, calc((100% - 1180px) / 2));
  padding-left: max(16px, calc((100% - 1180px) / 2));
}
.recruit-page > .recruit-tabs {
  width: min(1180px, 100%);
  margin-right: auto;
  margin-left: auto;
  padding-right: 0;
  padding-left: 0;
}
.recruit-page > .recruit-hero {
  width: min(1180px, 100%);
  margin-right: auto;
  margin-left: auto;
}

.recruit-page > .jobs-search-band {
  grid-column: 1 / -1;
  width: 100%;
  box-sizing: border-box;
  padding: 30px 24px 28px;
  /* 原为跟随轮播广告主题色的渐变底，按需求改为透明，融入门户自身背景 */
  background: transparent;
}
.jobs-search-inner { width: min(1180px, 100%); margin: 0 auto; }
.jobs-search-form { width: min(900px, 100%); margin: 0 auto; }
.jobs-search-form { gap: 0; }
/* margin-top 由下面的「吸顶工作台」一段接管 —— 它不只是留白，还决定吸顶时机。 */
.recruit-page--jobs > .jobs-search-band { margin-top: 56px; }
.jobs-search-form .ant-input-affix-wrapper { min-height: 54px; border: 2px solid var(--banner-color, #2a91d7); border-radius: 12px 0 0 12px; box-shadow: none; }
.jobs-search-form .ant-btn { min-width: 118px; height: 54px; border: 0; border-radius: 0 12px 12px 0; background: color-mix(in srgb, var(--banner-color-deep, #0b4a88) 64%, var(--banner-color, #2a91d7) 36%); font-size: 17px; font-weight: 700; }
.jobs-search-form .ant-btn:hover, .jobs-search-form .ant-btn:focus-visible { background: var(--banner-color-deep, #0b4a88); }

.recruit-hero {
  background-color: #fff9ef;
  background-image: linear-gradient(120deg, #fffaf3, #f0d4b3);
  box-shadow: 0 22px 44px rgb(183 92 24 / 12%);
}
.hero-copy h1 { color: #5d341d; }
.eyebrow { color: #d86616; }
.hero-note { color: #806956; }
.hero-orbit { border-color: rgb(216 134 54 / 38%); }
.hero-profile-card,
.hero-role-card {
  border-color: rgb(216 134 54 / 24%);
  background: rgb(255 255 255 / 72%);
  box-shadow: 0 16px 30px rgb(183 92 24 / 10%);
  backdrop-filter: none;
}
.hero-profile-card b, .hero-role-card b { color: #5d341d; }
.hero-profile-card small, .hero-role-card span { color: #806956; }
.hero-profile-card i { color: #d86616; }
.quick-searches { color: #806956; }
.quick-searches button { color: #a95520; background: rgb(255 255 255 / 72%); }
.quick-searches button:hover, .quick-searches button:focus-visible { color: #fff; background: #d86616; }

@media (min-width: 1280px) and (max-width: 640px) {
  .recruit-hero { min-height: 0; padding: 34px 22px 26px; border-radius: 18px; }
  .hero-copy h1 { font-size: 34px; }
}

.recruit-page {
  --recruit-ink: #123f49;
  --recruit-muted: #5f7f87;
  --recruit-line: #d7e9ed;
  --recruit-accent: #0f8f8c;
  --recruit-accent-soft: #e8f7f8;
  color: var(--recruit-ink);
  background: radial-gradient(circle at 80% 3%, rgb(198 235 242 / 62%), transparent 26rem);
}
.recruit-page--home { margin-top: 0 !important; padding-bottom: 0 !important; }

.recruit-subnav {
  border-color: rgb(215 233 237 / 92%);
  background: rgb(255 255 255 / 94%);
  box-shadow: 0 8px 24px rgb(18 63 73 / 8%);
}

.recruit-subnav button { color: #4f7079; }
.recruit-subnav button:hover,
.recruit-subnav button.active { color: #0f777d; }
.recruit-subnav button.active::after { background: linear-gradient(90deg, #0f8f8c, #2f80ed); }
.recruit-nav-account button { color: #4f7079; }
.recruit-nav-account button:hover { color: #0f8f8c; }

.recruit-hero {
  background-color: #123f49;
  background-image:
    linear-gradient(120deg, rgb(18 63 73 / 98%), rgb(19 112 119 / 94%) 62%, rgb(47 128 237 / 88%)),
    repeating-linear-gradient(90deg, rgb(255 255 255 / 5%) 0 1px, transparent 1px 42px),
    repeating-linear-gradient(0deg, rgb(255 255 255 / 4%) 0 1px, transparent 1px 42px);
}

.hero-copy h1 { color: #fff; }
.eyebrow { color: #8de0ef; }
.hero-profile-card i { color: #8de0ef; }
.quick-searches { color: #dff8f3; }
.recruit-page .section-kicker,
.recruit-page .search-detail-section h3 { color: #0f8f8c; }
.quick-searches button { color: #dff8f3; background: rgb(255 255 255 / 12%); }
.hero-avatar { background: #0f8f8c; }
.hero-search .ant-btn { border-color: #0f8f8c; background: #0f8f8c; }
.hero-search .ant-btn:hover { border-color: #0b777c; background: #0b777c; }
.quick-searches button:hover,
.quick-searches button:focus-visible { color: #123f49; background: #8de0ef; }

.home-job-categories button:hover,
.home-job-categories button.active { border-color: #b9e7ee; color: #0f777d; background: #e8f7f8; }
.home-job-categories button,
.home-job-categories button:hover,
.home-job-categories button.active { color: #000; }
.home-job-grid .post-title:hover { color: #0f8f8c; }
.home-companies-section { width: min(100%, 1180px); margin: 30px auto 0; }
.home-companies-section .home-jobs-heading { margin-bottom: 18px; }
.home-companies-section .home-jobs-heading > span { color: var(--recruit-muted); font-size: 16px; }
.home-company-card { min-height: 190px !important; }
.home-company-more { margin-top: 18px; }
.home-company-card .post-title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.company-post-count { color: #0f8f8c; font-size: 15px; white-space: nowrap; }
.company-home-post { margin: 12px 0 0; overflow: hidden; color: var(--recruit-muted); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.home-job-grid .post-salary-top,
.home-job-grid .post-salary,
.search-post-topline b,
.search-detail-actions strong { color: #dc3545; }
.service-icon { color: #0f8f8c; background: #e8f7f8; }
.service-grid a { color: #0f8f8c; }
.search-detail-actions .ant-btn-primary,
.recruit-page .post-apply,
.recruit-page .show-more-posts { border-color: #0f8f8c; background: linear-gradient(135deg, #0f9f9b, #2f80ed); color: #fff; }
.search-detail-actions .ant-btn-primary:hover,
.recruit-page .post-apply:hover,
.recruit-page .show-more-posts:hover { border-color: #0b777c; background: linear-gradient(135deg, #0b8587, #2563c7); }
.recruit-page .search-detail-actions .ant-btn-primary {
  border-color: #0f8f8c !important;
  background: #0f8f8c !important;
  color: #fff !important;
  box-shadow: 0 7px 16px rgb(15 143 140 / 18%);
}
.recruit-page .search-detail-actions .ant-btn-primary:hover,
.recruit-page .search-detail-actions .ant-btn-primary:focus-visible {
  border-color: #0b777c !important;
  background: #0b777c !important;
  color: #fff !important;
}
.recruit-page .post-tags span,
.recruit-page .search-post-card.selected { background: #eaf6ff; }
.show-more-posts:focus-visible { border-color: #2f80ed; background: #eaf6ff; color: #2368b5; }

.recruit-home-footer {
  grid-column: 1 / -1;
  width: 100%;
  margin-left: 0;
  margin-top: 34px;
  padding: 44px max(24px, calc((100% - 1180px) / 2)) 18px;
  color: #aeb2bc;
  background: #303338;
  box-shadow: 0 -18px 40px rgb(48 51 56 / 10%);
}
:global(.portal-main--recruit-home) .recruit-page,
:global(.portal-main--recruit) .recruit-page--rich-footer {
  min-height: 100vh;
  margin-top: 0;
  padding-bottom: 0 !important;
  align-content: stretch;
}
:global(.portal-main--recruit-home) .recruit-home-footer,
:global(.portal-main--recruit) .recruit-page--rich-footer .recruit-home-footer { margin-top: auto; }
/* 首页、公司、在线简历、用户页共用富页脚；职位页不显示，避免页脚叠加。
   这里在页面侧按同一口径覆盖掉公共页脚。
   不修改 layouts/portal.vue。

   注意：整条选择器必须包在同一个 :global() 里。Vue 的 scoped 编译器解析不了
   :global() 内部嵌套的 :has(...) 括号，会把选择器截断错位（实测会把 display:none
   落到 .portal-shell 上，导致整个页面空白）。 */
:global(.portal-shell:has(.recruit-page--rich-footer) .portal-footer) { display: none; }
/* 深色富页脚要贴到页面最底部，去掉公共主容器下内边距，否则页脚下方会留一条浅色缝。 */
:global(.portal-shell:has(.recruit-page--rich-footer) .portal-main) {
  min-height: 0;
  padding-top: 0;
  padding-bottom: 0;
}
/* 全屏导航使用 100vw 时，避免招聘外壳因滚动条宽度产生横向滚动条。
   必须用 clip 而不是 hidden：hidden 会把另一轴强制成 auto，等于给这两层
   套上滚动容器，里面所有 position: sticky 都会失效 —— 导航条、搜索筛选区
   的吸顶就是这么被废掉的（职位页没有这两条，所以那边一直正常）。
   clip 只裁剪、不生成滚动容器，两个目的可以同时达成。 */
:global(.portal-shell--recruit:has(.recruit-page--rich-footer)) {
  overflow-x: clip;
}
:global(.portal-shell--recruit:has(.recruit-page--rich-footer) .portal-main--recruit) {
  overflow-x: clip;
}
/* 直接覆盖门户公共页脚和主容器的默认留白，确保公司/简历/用户页的招聘页脚贴底。 */
:global(.portal-shell--recruit .portal-main--recruit) {
  padding-bottom: 0 !important;
}
:global(.portal-shell--recruit .portal-footer) {
  display: none !important;
}
.recruit-footer-inner { display: grid; grid-template-columns: 1.45fr .8fr .8fr 1.2fr; gap: 44px; width: min(1180px, 100%); margin: 0 auto; }
.recruit-footer-intro h2 { max-width: 270px; margin: 10px 0 12px; color: #e1e3e7; font-size: 23px; line-height: 1.35; }
.recruit-footer-intro > p:not(.recruit-footer-kicker),
.recruit-footer-contact p { max-width: 320px; margin: 0; color: #9da2ad; font-size: 13px; line-height: 1.8; }
.recruit-footer-kicker { margin: 0; color: #b8bdc7; font-size: 12px; font-weight: 700; letter-spacing: .16em; }
.recruit-footer-badge { display: inline-flex; align-items: center; gap: 7px; margin-top: 18px; padding: 6px 10px; border: 1px solid #5b6068; border-radius: 999px; color: #c4c8d0; background: #3a3e44; font-size: 12px; }
.recruit-footer-badge :deep(svg) { font-size: 14px; }
.recruit-footer-column h3,
.recruit-footer-contact h3 { margin: 2px 0 16px; color: #d9dce1; font-size: 15px; }
.recruit-footer-column { display: grid; align-content: start; gap: 11px; }
.recruit-footer-column button { width: max-content; padding: 0; border: 0; color: #a5aab5; background: transparent; cursor: pointer; font: inherit; font-size: 13px; text-align: left; }
.recruit-footer-column button:hover { color: #e0e2e6; }
.recruit-footer-contact { padding-left: 28px; border-left: 1px solid #4b4f56; }
.recruit-footer-contact strong { display: block; margin-bottom: 8px; color: #d6d9df; font-size: 17px; }
.recruit-footer-contact button { display: inline-flex; align-items: center; gap: 8px; margin-top: 18px; padding: 9px 13px; border: 1px solid #5b6068; border-radius: 8px; color: #c6cad2; background: #3a3e44; cursor: pointer; font-size: 13px; }
.recruit-footer-contact button:hover { border-color: #777d87; background: #444850; }
.recruit-footer-bottom { display: flex; align-items: center; justify-content: space-between; gap: 16px; width: min(1180px, 100%); margin: 38px auto 0; padding-top: 16px; border-top: 1px solid #454950; color: #969ca7; font-size: 12px; }
.recruit-footer-mark { display: inline-flex; align-items: baseline; gap: 8px; color: #e0e2e6; font-size: 18px; font-weight: 800; letter-spacing: .08em; }
.recruit-footer-mark small { color: #969ca7; font-size: 9px; font-weight: 500; letter-spacing: .14em; }

@media (min-width: 1280px) and (max-width: 900px) {
  .recruit-page {
    overflow-x: visible;
  }

  .recruit-page > .recruit-subnav,
  .recruit-page > .recruit-tabs {
    width: 100%;
    max-width: 100%;
    margin-right: 0;
    margin-left: 0;
  }

  .recruit-footer-inner { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 30px 24px; }
  .recruit-footer-intro { grid-column: 1 / -1; }
  .recruit-footer-contact { padding-left: 0; border-top: 1px solid rgb(126 225 229 / 24%); border-left: 0; padding-top: 24px; }
}
@media (min-width: 1280px) and (max-width: 560px) {
  .recruit-home-footer { padding: 34px 20px 16px; }
  .recruit-footer-inner { grid-template-columns: 1fr 1fr; gap: 28px 16px; }
  .recruit-footer-contact { grid-column: 1 / -1; }
  .recruit-footer-bottom { align-items: flex-start; flex-direction: column; gap: 8px; margin-top: 30px; }
  .recruit-footer-mark { align-self: flex-end; }
}

/* 仅收敛职位浏览页的窄屏布局，避免导航内边距和结果列制造横向溢出。 */
@media (min-width: 1280px) and (max-width: 900px) {
  .recruit-page > .recruit-subnav,
  .recruit-page > .recruit-tabs {
    box-sizing: border-box;
    min-width: 0;
  }

  .recruit-page > .recruit-subnav {
    overflow-x: auto;
  }

  .recruit-page > .recruit-tabs {
    overflow-x: clip;
  }
}

@media (min-width: 1280px) and (min-width: 641px) {
  .filter-toolbar {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (min-width: 1280px) and (min-width: 901px) {
  .search-detail-card {
    position: sticky;
    top: 76px;
    align-self: start;
    max-height: calc(100vh - 96px);
    overflow: auto;
  }
}

@media (min-width: 1280px) and (max-width: 900px) {
  .search-detail-card {
    position: static;
    max-height: none;
    overflow: visible;
  }
}

.filter-actions {
  padding-top: 0;
  border-top: 0;
  justify-content: flex-start;
}

.home-job-card .post-card-company {
  flex: 1 1 auto;
}

.company-post-count {
  font-size: 14px;
  font-weight: 600;
}

.recruit-page--home {
  --recruit-home-content-width: 1180px;
  grid-template-columns: minmax(24px, 1fr) minmax(0, var(--recruit-home-content-width)) minmax(24px, 1fr);
  row-gap: 0;
  background: #f3f5f8;
}

.recruit-page--jobs {
  /* 配色 token 已上提到 .recruit-page，公司页共用同一份。这里只留吸顶工作台相关。 */
  /* 关掉滚动锚定：工作台收拢时高度骤减，Chrome 的 scroll anchoring 会把
     scrollY 反向补偿同样的距离，于是跨临界点来回抖（实测 28 次翻转，
     且能把滚动一路顶回顶部）。详见 company-directory.vue 里的同类注释。 */
  overflow-anchor: none;
  /* 吸顶工作台：--jobs-topbar-h 首帧兜底，JS 实测导航条高度后会覆盖它。
     --jobs-bar-h 是收拢后的实高，右栏详情的吸顶偏移要用；
     改动下面的 padding / 行高时，这三个数必须一起改。 */
  --jobs-topbar-h: 60px;
  --jobs-bar-h: 110px;
  --jobs-ease: cubic-bezier(0.22, 0.61, 0.36, 1);
  row-gap: 0;
}

/* ── 吸顶工作台：导航条之下，搜索行 + 筛选行整块固定 ──────────────
   .jobs-search-band 是 .recruit-page 的直接子元素（grid-column 1/-1），
   筛选面板也放进它里面整体吸顶。两块分开吸顶不行：收拢动画期间
   上面那块的高度在变，下面那块的高度基准却是固定的，中间会露出缝。

   margin-top 56px 不是为了留白，而是决定吸顶时机：
   元素在文档里的原始位置必须落在吸顶线（60px）之下，吸顶才是一个
   「事件」而不是页面的初始状态。28（.recruit-page 的负上边距所抵消掉的）
   + 56 = 84，即下滑 24px 后收拢。 */
.recruit-page--jobs > .jobs-search-band {
  position: sticky;
  z-index: 30; /* 低于导航条的 40，高于页面内容 */
  top: var(--jobs-topbar-h, 60px);
  padding: 20px 24px 12px;
  transition: padding 260ms var(--jobs-ease);
}

/* 底板单独一层伪元素，而不是把背景直接画在元素上：
   这样整层 chrome（底色 + 磨砂 + 分隔线 + 投影）可以用 opacity 一起淡入，
   不去过渡 backdrop-filter —— 后者在部分内核上是跳变而不是渐变。
   常驻 DOM、只改 opacity，也避免了 content 从无到有时的那一帧闪动。
   注意这个块本身已经是通栏的（.recruit-page 没有左右内边距，且它是
   grid-column 1/-1），不要在这里做外扩，会撑出横向滚动条。 */
.recruit-page--jobs > .jobs-search-band::before {
  position: absolute;
  z-index: -1;
  inset: 0;
  border-bottom: 1px solid var(--job-line);
  background: rgb(255 255 255 / 92%);
  box-shadow: 0 10px 24px rgb(27 42 61 / 8%);
  backdrop-filter: blur(16px) saturate(135%);
  content: '';
  opacity: 0;
  transition: opacity 260ms var(--jobs-ease);
}
.recruit-page--jobs > .jobs-search-band.is-pinned {
  padding: 10px 24px;
}
.recruit-page--jobs > .jobs-search-band.is-pinned::before {
  opacity: 1;
}
/* 搜索行收窄：54 → 42。输入框高度由 min-height 决定，按钮由 height 决定，
   两者都得写，否则一个收了一个没收，行会歪。 */
.recruit-page--jobs .jobs-search-form .ant-input-affix-wrapper,
.recruit-page--jobs .jobs-search-form .ant-btn {
  transition: min-height 260ms var(--jobs-ease), height 260ms var(--jobs-ease),
    font-size 260ms var(--jobs-ease);
}
.recruit-page--jobs
  > .jobs-search-band.is-pinned
  .jobs-search-form
  .ant-input-affix-wrapper,
.recruit-page--jobs > .jobs-search-band.is-pinned .jobs-search-form .ant-btn {
  height: 42px;
  min-height: 42px;
}
.recruit-page--jobs > .jobs-search-band.is-pinned .jobs-search-form .ant-btn {
  font-size: 15px;
}
/* 筛选行原先是页签里的独立块，靠 .jobs-search-band 的下内边距与搜索框拉开；
   现在它在工作台内部，间距改由自己的 margin-top 表达，才能跟着一起收拢。 */
.recruit-page--jobs .jobs-search-band .filter-panel {
  margin: 14px auto 0;
  transition: margin-top 260ms var(--jobs-ease);
}
.recruit-page--jobs
  > .jobs-search-band.is-pinned
  .filter-panel {
  margin-top: 8px;
}

@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .recruit-page--jobs > .jobs-search-band::before {
    background: #fff;
  }
}

@media (prefers-reduced-motion: reduce) {
  .recruit-page--jobs > .jobs-search-band,
  .recruit-page--jobs > .jobs-search-band::before,
  .recruit-page--jobs .jobs-search-form .ant-input-affix-wrapper,
  .recruit-page--jobs .jobs-search-form .ant-btn,
  .recruit-page--jobs .jobs-search-band .filter-panel {
    transition: none;
  }

  /* 悬停展开保留（那是行为不是动效），只去掉位移和翻转的过渡 */
  .recruit-page--jobs .filter-panel :deep(.ant-select),
  .recruit-page--jobs .filter-panel :deep(.ant-select-arrow),
  .recruit-page--jobs .filter-panel :deep(.ant-select-selector) {
    transition: none;
  }

  .recruit-page--jobs .filter-panel :deep(.ant-select-open) {
    transform: none;
  }

  .recruit-page--jobs .filter-panel :deep(.ant-select-open .ant-select-arrow) {
    transform: none;
  }
}

/* Jobs 页筛选器采用搜索框下方的单行胶囊布局。 */
/* 固定 40px 一行：上下 padding 归零，靠 align-items:center 居中，
   内部控件都压在 40px 以内（下拉 35px、重置按钮 40px），不会撑高。 */
.recruit-page--jobs .filter-panel {
  display: flex;
  width: min(1180px, 100%);
  height: 40px;
  align-items: center;
  gap: 12px;
  margin: 0 auto 18px;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}
.recruit-page--jobs .filter-heading {
  display: none;
}
.recruit-page--jobs .filter-toolbar {
  display: flex;
  flex: 1 1 auto;
  flex-wrap: nowrap;
  min-width: 0;
  gap: 12px;
}
.recruit-page--jobs .filter-control {
  flex: 1 1 0;
  width: auto;
  min-width: 0;
}
/* 整行只用职位页的蓝，走 --job-* 令牌而不是各自的硬编码十六进制。
   下拉是输入控件不是动作，绿色在本页只留给「立即沟通」，摆在这一行会跟主 CTA 抢注意力。
   描边/悬停/聚焦三级同色系递进，箭头也归到 --job-muted，
   免得出现「绿灰描边 + 高饱和绿悬停 + 中性灰文字」三套颜色并排。 */
.recruit-page--jobs .filter-panel :deep(.ant-select-selector),
.recruit-page--jobs .filter-panel :deep(.ant-cascader-picker) {
  min-height: 35px;
  border: 2px solid var(--job-line) !important;
  border-radius: 999px !important;
  background: #fff !important;
  box-shadow: none !important;
  transition: border-color 140ms ease, background-color 140ms ease;
}
.recruit-page--jobs .filter-panel :deep(.ant-select-selection-placeholder),
.recruit-page--jobs .filter-panel :deep(.ant-select-selection-item),
.recruit-page--jobs .filter-panel :deep(.ant-cascader-picker-label) {
  color: var(--job-ink);
  font-size: 14px;
}
/* 箭头跟随开合翻转，是「这一项已展开」最省字的提示 */
.recruit-page--jobs .filter-panel :deep(.ant-select-arrow),
.recruit-page--jobs .filter-panel :deep(.ant-cascader-picker-arrow) {
  color: var(--job-muted);
  transition: transform 180ms var(--jobs-ease), color 180ms var(--jobs-ease);
}
.recruit-page--jobs .filter-panel :deep(.ant-select-open .ant-select-arrow) {
  color: var(--job-blue);
  transform: rotate(180deg);
}
/* 打开时也要留住高亮：焦点在弹出层上，光靠 .ant-select-focused 会在展开瞬间掉色 */
.recruit-page--jobs .filter-panel :deep(.ant-select-selector:hover),
.recruit-page--jobs .filter-panel :deep(.ant-cascader-picker:hover),
.recruit-page--jobs .filter-panel :deep(.ant-select-focused .ant-select-selector),
.recruit-page--jobs .filter-panel :deep(.ant-select-open .ant-select-selector) {
  border-color: var(--job-blue) !important;
  background: var(--job-blue-soft) !important;
}
/* 抬起要作用在 .ant-select 上而不是 selector：箭头是 .ant-select 的兄弟节点，
   只抬 selector 会让箭头和胶囊脱开。位移不进布局，工具栏等分宽度不受影响。 */
.recruit-page--jobs .filter-panel :deep(.ant-select) {
  transition: transform 180ms var(--jobs-ease);
}
.recruit-page--jobs .filter-panel :deep(.ant-select-open) {
  transform: translateY(-1px);
}
.recruit-page--jobs .filter-panel :deep(.ant-select-open .ant-select-selector) {
  box-shadow: 0 6px 16px rgb(47 111 179 / 16%) !important;
}
.recruit-page--jobs .filter-actions {
  flex: 0 0 auto;
  margin-left: 2px;
}
.recruit-page--jobs .reset-button {
  height: 40px;
  padding-inline: 10px;
  color: var(--job-muted);
}
.recruit-page--jobs .reset-button:hover {
  color: var(--job-blue) !important;
  background: var(--job-blue-soft);
}
/* ── 筛选下拉的弹出层：交互色与触发器同一套蓝 ────────────────────
   弹出层由 antd 挂在 body 下，够不到 .recruit-page--jobs 的 --job-* 令牌
   （自定义属性只沿 DOM 继承），所以这里写等值十六进制，改色时两处要一起改。
   作用域靠 jobs-filter-dropdown 锁住：该类的消费者只有职位页筛选器，
   profile-setup / online-resume 里的地址级联仍走上面那套旧蓝，不受影响。 */
:global(.jobs-filter-dropdown.ant-select-dropdown),
:global(.jobs-filter-dropdown .ant-select-dropdown) {
  padding: 6px;
  border: 1px solid #d7e5f4;
  border-radius: 14px;
  box-shadow: 0 12px 28px rgb(27 42 61 / 10%);
}
:global(.jobs-filter-dropdown .ant-select-item) {
  border-radius: 8px;
  color: #1b2a3d !important;
  background: transparent !important;
  font-size: 14px;
}
/* 悬停与选中共用淡蓝底，靠字色与字重区分：
   整块填色相同会让鼠标一移开就分不清选了哪个。 */
:global(.jobs-filter-dropdown
    .ant-select-item-option-active:not(.ant-select-item-option-selected)) {
  color: #1b2a3d !important;
  background: #e8f1fb !important;
}
:global(.jobs-filter-dropdown .ant-select-item-option-selected) {
  color: #2f6fb3 !important;
  background: #e8f1fb !important;
  font-weight: 600;
}
/* 地址是级联选择器，弹出层结构与普通下拉不同，得单独写一遍 */
:global(.jobs-filter-dropdown.ant-cascader-menus),
:global(.jobs-filter-dropdown .ant-cascader-menus) {
  border: 1px solid #d7e5f4;
  border-radius: 14px;
  box-shadow: 0 12px 28px rgb(27 42 61 / 10%);
}
:global(.jobs-filter-dropdown .ant-cascader-menu-item) {
  border-radius: 8px;
  color: #1b2a3d !important;
  background: transparent !important;
  font-size: 14px;
}
:global(.jobs-filter-dropdown .ant-cascader-menu-item:hover) {
  color: #1b2a3d !important;
  background: #e8f1fb !important;
}
:global(.jobs-filter-dropdown .ant-cascader-menu-item-active) {
  color: #2f6fb3 !important;
  background: #e8f1fb !important;
  font-weight: 600;
}
/* ── 职位类型：两级分类下拉 ──────────────────────────────────────
   面板 680×220。左栏取 200px 而非四等分，是为了容下
   「施工技能类（一线操作岗）」这类 12 字中文大类名而不折行：
   200 − 栏内边距 16 − 按钮内边距 20 = 164px 可用 > 12 字 × 13px。
   选中大类以白卡浮于淡蓝栏 + 左侧 3px 品牌蓝竖条，表达
   「这一类正在展开」；比整块填色克制，也适配紧凑的面板高度。 */
:global(.recruit-filter-dropdown.job-type-dropdown) {
  /* antd 会把按触发器算出的宽度写成内联 style，需 !important 才能覆盖 */
  width: 680px !important;
  min-width: 680px !important;
  max-width: 680px !important;
  padding: 0 !important;
  overflow: hidden;
  border: 1px solid #d7e5f4;
  border-radius: 14px;
  box-shadow: 0 12px 28px rgb(27 42 61 / 10%);
}
.job-type-panel {
  display: flex;
  width: 100%;
  height: 220px;
}
.job-type-groups {
  flex: 0 0 200px;
  padding: 10px 8px;
  overflow-y: auto;
  background: #f7fafd;
  border-right: 1px solid #d7e5f4;
  /* scrollbar-width/color 是标准属性，::-webkit-scrollbar 供旧内核兜底 */
  scrollbar-width: thin;
  scrollbar-color: #c4d8ee transparent;
}
.job-type-groups::-webkit-scrollbar,
.job-type-items::-webkit-scrollbar {
  width: 4px;
}
.job-type-groups::-webkit-scrollbar-thumb,
.job-type-items::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: #c4d8ee;
}
.job-type-groups::-webkit-scrollbar-track,
.job-type-items::-webkit-scrollbar-track {
  background: transparent;
}
.job-type-group {
  position: relative;
  display: block;
  width: 100%;
  padding: 8px 10px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #5f7288;
  font-size: 13px;
  line-height: 1.35;
  text-align: left;
  cursor: pointer;
  transition: background-color 120ms ease, color 120ms ease;
}
.job-type-group:hover {
  color: #1b2a3d;
}
.job-type-group.is-active {
  background: #fff;
  color: #2f6fb3;
  font-weight: 500;
  box-shadow: 0 1px 3px rgb(27 42 61 / 6%);
}
.job-type-group.is-active::before {
  position: absolute;
  top: 50%;
  left: 0;
  width: 3px;
  height: 16px;
  border-radius: 999px;
  background: #2f6fb3;
  content: '';
  transform: translateY(-50%);
}
.job-type-items {
  display: grid;
  flex: 1 1 auto;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 2px 4px;
  align-content: start;
  padding: 10px 8px;
  overflow-y: auto;
}
.job-type-item {
  padding: 8px 10px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #1b2a3d;
  font-size: 13px;
  line-height: 1.35;
  text-align: left;
  cursor: pointer;
  transition: background-color 120ms ease, color 120ms ease;
}
/* 与普通下拉同理：悬停只铺底，选中才上蓝字加粗，两者不靠底色区分 */
.job-type-item:hover {
  background: #e8f1fb;
  color: #1b2a3d;
}
.job-type-item.is-selected {
  background: #e8f1fb;
  color: #2f6fb3;
  font-weight: 500;
}
/* 窄屏：面板跟随视口宽度，右栏降为两列，左栏收窄（大类名允许折行）。 */
@media (max-width: 760px) {
  :global(.recruit-filter-dropdown.job-type-dropdown) {
    /* 窄屏筛选器横向排列、「职位类型」偏右，下拉若左对齐触发器会冲出视口，故改为贴左 */
    right: auto !important;
    left: 16px !important;
    width: calc(100vw - 32px) !important;
    min-width: 0 !important;
    max-width: calc(100vw - 32px) !important;
  }
  .job-type-groups {
    flex-basis: 140px;
  }
  .job-type-items {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (prefers-reduced-motion: reduce) {
  .job-type-group,
  .job-type-item {
    transition: none;
  }
}

.recruit-page--home > .recruit-subnav {
  padding-right: max(24px, calc((100% - var(--recruit-home-content-width)) / 2 + 24px));
  padding-left: max(24px, calc((100% - var(--recruit-home-content-width)) / 2 + 24px));
}

.recruit-page--home > .recruit-hero {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: minmax(220px, 265px) minmax(0, 1fr) auto;
  align-items: center;
  width: 100%;
  min-height: 168px;
  gap: 24px;
  margin: 0;
  padding: 30px max(24px, calc((100% - var(--recruit-home-content-width)) / 2));
  border-radius: 0;
  overflow: visible;
  background: linear-gradient(110deg, #e9f3ff 0%, #d9e9ff 100%);
  box-shadow: none;
}

.recruit-page--home .hero-topline { display: none; }
.recruit-page--home .hero-copy,
.recruit-page--home .hero-visual { display: none; }
.recruit-page--home .hero-search { grid-column: 1 / -1; grid-row: 1; justify-self: center; width: min(100%, 900px); gap: 0; }
.recruit-page--home .hero-search .ant-input-affix-wrapper { min-height: 54px; border: 2px solid #08b6bd; border-radius: 12px 0 0 12px; box-shadow: none; }
.recruit-page--home .hero-search .ant-btn { min-width: 118px; height: 54px; border: 0; border-radius: 0 12px 12px 0; background: #08b6bd; box-shadow: none; font-size: 17px; font-weight: 700; }
.recruit-page--home .hero-search .ant-btn:hover { background: #079da4; }
.recruit-page--home .quick-searches { grid-column: 1 / -1; grid-row: 2; display: flex; justify-self: center; justify-content: flex-start; width: min(100%, 900px); gap: 8px 18px; color: #60758a; font-size: 13px; }
.recruit-page--home .quick-searches button { padding: 4px 10px; border: 1px solid #d8e9f2; border-radius: 4px; background: #fff; color: #168da0; font-size: 13px; transition: color 160ms ease, border-color 160ms ease, background 160ms ease, transform 160ms ease; }
.recruit-page--home .quick-searches button:hover, .recruit-page--home .quick-searches button:focus-visible { border-color: #08b6bd; background: #e9fbfc; color: #087782; outline: 0; transform: translateY(-1px); }

.recruit-page--home > .recruit-category-showcase {
  grid-column: 2;
  display: grid;
  grid-template-columns: minmax(240px, 360px) minmax(0, 1fr);
  height: 400px;
  min-height: 0;
  margin: 0;
  overflow: visible;
  border: 1px solid color-mix(in srgb, var(--banner-color) 45%, #fff 55%);
  border-radius: 20px;
  background: linear-gradient(135deg, color-mix(in srgb, var(--banner-color-soft) 72%, #fff 28%), #fff 54%, color-mix(in srgb, var(--banner-color-soft) 48%, #fff 52%));
  box-shadow: 0 14px 38px color-mix(in srgb, var(--banner-color-deep) 16%, transparent);
  transition: border-color 420ms ease, background 420ms ease, box-shadow 420ms ease;
}

.recruit-category-sidebar { display: flex; flex-direction: column; justify-content: flex-start; min-height: 0; overflow-y: auto; padding: 8px 0; border-radius: 19px 0 0 19px; background: linear-gradient(165deg, color-mix(in srgb, var(--banner-color-deep) 84%, #fff 16%), var(--banner-color-deep)); scrollbar-color: rgb(255 255 255 / 38%) transparent; scrollbar-width: thin; transition: background 420ms ease; }
.recruit-category-sidebar button { display: flex; height: 60px; min-height: 60px; align-items: center; justify-content: space-between; gap: 12px; margin: 2px 8px; padding: 0 22px; border: 0; border-radius: 6px; color: #fff; background: transparent; cursor: pointer; font-size: 16px; text-align: left; transition: background 160ms ease, color 160ms ease, padding 160ms ease; }
.recruit-category-sidebar button:hover,
.recruit-category-sidebar button.active { padding-left: 26px; background: rgb(255 255 255 / 16%); }
.recruit-category-sidebar button :deep(svg) { flex: 0 0 auto; color: #d7deeb; }
.recruit-category-content { position: relative; min-width: 0; min-height: 0; overflow-x: hidden; overflow-y: auto; border-radius: 0 19px 19px 0; background: #fff; scrollbar-color: #aebfd8 transparent; scrollbar-width: thin; }
.recruit-category-sidebar::-webkit-scrollbar, .recruit-category-content::-webkit-scrollbar { width: 8px; }
.recruit-category-sidebar::-webkit-scrollbar-track, .recruit-category-content::-webkit-scrollbar-track { background: transparent; }
.recruit-category-sidebar::-webkit-scrollbar-thumb { border: 2px solid transparent; border-radius: 99px; background: rgb(255 255 255 / 35%); background-clip: padding-box; }
.recruit-category-sidebar::-webkit-scrollbar-thumb:hover { background: rgb(255 255 255 / 65%); background-clip: padding-box; }
.recruit-category-content::-webkit-scrollbar-thumb { border: 2px solid transparent; border-radius: 99px; background: #aebfd8; background-clip: padding-box; }
.recruit-category-content::-webkit-scrollbar-thumb:hover { background: #7895bb; background-clip: padding-box; }
.recruit-ad-carousel { position: absolute; inset: 0; }
.recruit-ad-image-button { display: block; width: 100%; height: 100%; padding: 0; border: 0; background: transparent; cursor: pointer; text-align: inherit; }
.recruit-ad-image-button:focus-visible { outline: 3px solid rgb(255 255 255 / 85%); outline-offset: -5px; }
.recruit-ad-carousel img { display: block; width: 100%; height: 100%; object-fit: cover; }
.recruit-ad-carousel::after { position: absolute; inset: 0; border-radius: inherit; background: linear-gradient(115deg, color-mix(in srgb, var(--banner-color) 12%, transparent), transparent 42%, color-mix(in srgb, var(--banner-color) 16%, transparent)); box-shadow: inset 0 0 48px color-mix(in srgb, var(--banner-color) 12%, transparent); content: ''; pointer-events: none; transition: background 420ms ease, box-shadow 420ms ease; }
.recruit-ad-dots { position: absolute; right: 0; bottom: 18px; left: 0; display: flex; justify-content: center; gap: 10px; }
.recruit-ad-dots button { width: 10px; height: 10px; padding: 0; border: 0; border-radius: 99px; background: rgb(255 255 255 / 55%); cursor: pointer; transition: width 160ms ease, background 160ms ease; }
.recruit-ad-dots button.active { width: 30px; background: var(--banner-color); box-shadow: 0 0 14px color-mix(in srgb, var(--banner-color) 45%, transparent); }
.category-detail-head { padding: 10px 30px 8px; border-bottom: 1px solid #d9e3f0; color: #183b67; font-size: 18px; line-height: 1.35; }
.category-post-groups { display: grid; gap: 14px; padding: 20px 30px 28px; }
.category-post-group { display: grid; gap: 7px; padding-bottom: 12px; border-bottom: 1px solid rgb(149 171 205 / 28%); }
.category-post-group:last-child { border-bottom: 0; }
.category-post-group h3 { margin: 0; color: #183b67; font-size: 15px; font-weight: 700; transition: color 160ms ease; }
.category-post-links { display: flex; flex-wrap: wrap; gap: 5px 22px; }
.category-post-links button { padding: 2px 4px; border: 0; border-radius: 4px; background: transparent; color: #34465f; cursor: pointer; font: inherit; font-size: 14px; line-height: 1.55; transition: color 160ms ease, background 160ms ease, transform 160ms ease; }
.category-post-links button:hover, .category-post-links button:focus-visible { color: #164f9d; background: rgb(255 255 255 / 72%); outline: 0; transform: translateY(-1px); }
.category-post-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; padding: 20px 30px; }
.category-post-grid button { display: flex; min-width: 0; align-items: center; justify-content: space-between; gap: 12px; padding: 12px; border: 1px solid #edf0f5; border-radius: 8px; background: #fff; color: #34465f; cursor: pointer; text-align: left; }
.category-post-grid button:hover { border-color: #a9bcf9; color: #315fd0; }
.category-post-grid button strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.category-post-grid button span { flex: 0 0 auto; color: #8995a8; font-size: 12px; }
.category-empty { grid-column: 1 / -1; color: #8995a8; }

.recruit-page--home > .city-opportunities-section {
  grid-column: 2;
  width: 100%;
  margin: 52px auto 60px;
}

.recruit-page--home > .recruit-tabs,
.recruit-page--home > .home-companies-section {
  grid-column: 2;
  width: 100%;
  max-width: none;
  min-width: 0;
  margin-right: 0;
  margin-left: 0;
}

.recruit-page--home > .recruit-tabs { margin-top: 30px; padding: 0; }
.recruit-page--home .home-jobs-heading { width: 100%; }
.recruit-page--home .home-job-grid { width: 100%; grid-template-columns: repeat(3, minmax(0, 1fr)); }
.recruit-page--home .home-job-grid .post-card { width: 100%; min-width: 0; }
.recruit-page--home .home-company-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.recruit-page--home .home-companies-section { margin-top: 46px; }
.recruit-page--home .home-company-more { max-width: 100%; }

.city-opportunities-heading { margin-bottom: 34px; text-align: center; }
.city-opportunities-heading h2 { margin: 0; color: #202b3d; font-size: 32px; font-weight: 800; letter-spacing: 0.02em; }
.city-opportunities-viewport { position: relative; }
.city-opportunities-grid { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 12px; }
.city-opportunity-card { position: relative; display: flex; min-width: 0; aspect-ratio: 1.82; align-items: flex-end; overflow: hidden; padding: 18px; border: 0; border-radius: 14px; background-color: #173c58; background-position: center; background-size: cover; color: #fff; cursor: pointer; text-align: left; transition: transform 180ms ease, box-shadow 180ms ease; }
.city-opportunity-card:hover { z-index: 1; box-shadow: 0 12px 28px rgb(14 33 55 / 25%); transform: translateY(-3px); }
.city-opportunity-copy { display: grid; gap: 3px; }
.city-opportunity-copy strong { font-size: 16px; line-height: 1.2; }
.city-opportunity-copy small { color: rgb(255 255 255 / 84%); font-size: 14px; }
.city-opportunity-arrow { position: absolute; top: 13px; right: 15px; color: #fff; font-size: 24px; }
.city-opportunity-nav { position: absolute; top: 50%; z-index: 2; display: grid; width: 32px; height: 62px; place-items: center; padding: 0; transform: translateY(-50%); border: 0; border-radius: 17px; background: #fff; box-shadow: 0 5px 16px rgb(24 47 80 / 13%); color: #8a9aab; cursor: pointer; font-size: 16px; transition: color 160ms ease, box-shadow 160ms ease, transform 160ms ease; }
.city-opportunity-nav:hover, .city-opportunity-nav:focus-visible { box-shadow: 0 7px 18px rgb(24 47 80 / 18%); color: #3f82b9; outline: 0; }
.city-opportunity-nav:active { transform: translateY(-50%) scale(.96); }
.city-opportunity-prev { left: -16px; }
.city-opportunity-next { right: -16px; }

@media (min-width: 1280px) and (max-width: 1100px) {
  .recruit-page--home { grid-template-columns: 20px minmax(0, 1fr) 20px; }
  .recruit-page--home .home-job-grid,
  .recruit-page--home .home-company-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .recruit-page--home > .recruit-hero { grid-template-columns: minmax(150px, 220px) minmax(0, 1fr) auto; padding-right: 24px; padding-left: 24px; }
  .recruit-page--home > .recruit-category-showcase { grid-template-columns: 250px minmax(0, 1fr); }
  .recruit-category-sidebar button { padding-inline: 22px; font-size: 16px; }
  .city-opportunities-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
  .city-opportunity-prev { left: -10px; }
  .city-opportunity-next { right: -10px; }
}

@media (min-width: 1280px) and (max-width: 700px) {
  .recruit-page--home { grid-template-columns: 14px minmax(0, 1fr) 14px; }
  .recruit-page--home > .recruit-hero { grid-template-columns: minmax(0, 1fr) auto; gap: 16px; padding: 24px 18px; }
  .recruit-page--home .hero-brand-wordmark { grid-column: 1; grid-row: 1; }
  .recruit-page--home .hero-tools { grid-column: 2; grid-row: 1; gap: 8px; }
  .recruit-page--home .hero-tool-icon { width: 42px; height: 42px; font-size: 18px; }
  .recruit-page--home .hero-search { grid-column: 1 / -1; grid-row: 2; }
  .recruit-page--home .hero-search .ant-input-affix-wrapper,
  .recruit-page--home .hero-search .ant-btn { min-height: 52px; height: 52px; }
  .recruit-page--home .hero-search .ant-btn { min-width: 108px; padding-inline: 12px; }
  .recruit-page--home > .recruit-category-showcase { grid-template-columns: minmax(0, 1fr); height: auto; min-height: 0; margin-top: 18px; }
  .recruit-page--home > .recruit-tabs { margin-top: 24px; }
  .recruit-page--home .home-jobs-heading { align-items: flex-start; }
  .recruit-page--home .home-job-grid,
  .recruit-page--home .home-company-grid { grid-template-columns: minmax(0, 1fr); gap: 12px; }
  .recruit-page--home .home-job-grid .post-card { min-height: 0; }
  .recruit-page--home .home-company-card { min-height: 0 !important; }
  .recruit-page--home .home-companies-section { margin-top: 34px; }
  .recruit-category-sidebar { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); padding: 6px; border-radius: 18px 18px 0 0; }
  .recruit-category-sidebar button { min-height: 48px; padding: 0 10px; font-size: 13px; }
  .recruit-category-content { min-height: 250px; border-radius: 0 0 18px 18px; }
  .category-post-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); padding: 14px; }
  .recruit-page--home > .city-opportunities-section { margin: 38px auto 42px; }
  .city-opportunities-heading { margin-bottom: 20px; }
  .city-opportunities-heading h2 { font-size: 26px; }
  .city-opportunities-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
  .city-opportunity-card { padding: 13px; border-radius: 10px; }
  .city-opportunity-copy strong { font-size: 16px; }
  .city-opportunity-copy small { font-size: 12px; }
  .city-opportunity-nav { width: 28px; height: 54px; border-radius: 15px; font-size: 14px; }
  .city-opportunity-prev { left: -8px; }
  .city-opportunity-next { right: -8px; }
}

/* 热门企业采用三列企业卡片布局，职位入口与企业主页入口保持独立。 */
.home-companies-section {
  box-sizing: border-box;
  width: 100%;
  padding: 28px 0 30px;
  border-radius: 18px;
  background: #f1f7fb;
}
.home-companies-section .home-jobs-heading {
  display: block;
  width: 100%;
  margin: 0 0 24px;
  padding: 0;
  border: 0;
  text-align: center;
}
.home-companies-section .home-jobs-heading h2 {
  margin: 0;
  color: #183b4c;
  font-size: 28px;
  font-weight: 800;
  letter-spacing: 0;
}
.home-company-grid {
  width: 100%;
  margin-inline: 0;
  gap: 18px;
}
.home-company-card {
  display: flex !important;
  flex-direction: column;
  gap: 0 !important;
  box-sizing: border-box;
  width: 100%;
  height: auto !important;
  min-height: 0 !important;
  padding: 20px 20px 0 !important;
  overflow: hidden;
  border: 1px solid #d4e7f4 !important;
  border-radius: 10px !important;
  background: #fff !important;
  box-shadow: 0 8px 22px rgb(59 112 150 / 7%) !important;
  transition: border-color 160ms ease, box-shadow 160ms ease, transform 160ms ease;
}
.home-company-card:hover {
  border-color: #8fc9e8 !important;
  box-shadow: 0 14px 28px rgb(59 112 150 / 13%) !important;
  transform: translateY(-4px);
}
.home-company-card:nth-child(3n + 1),
.home-company-card:nth-child(3n + 2),
.home-company-card:nth-child(3n) { border-top: 3px solid #7fc8e8 !important; }
.home-company-card:focus-visible { border-color: #4da7d5 !important; box-shadow: 0 0 0 3px rgb(77 167 213 / 22%), 0 14px 28px rgb(59 112 150 / 13%) !important; outline: 0; }
.home-company-card:active { transform: translateY(-1px); }
.hot-company-header {
  display: flex;
  align-items: center;
  box-sizing: border-box;
  width: 381.33px;
  max-width: none;
  min-height: 68px;
  gap: 14px;
  margin: -20px -20px 0;
  padding: 12px 20px;
  border-bottom: 0;
  background: #f4f9fc;
}
.hot-company-logo {
  display: grid;
  width: 44px;
  height: 44px;
  flex: 0 0 44px;
  place-items: center;
  overflow: hidden;
  border: 1px solid #d4e7f4;
  border-radius: 8px;
  background: #fff;
  color: #1f2937;
  font-size: 16px;
  font-weight: 700;
  object-fit: contain;
}
.hot-company-logo-fallback { background: #fff; }
.hot-company-heading { display: grid; min-width: 0; gap: 5px; }
.company-favorite-button { display: grid; width: 32px; height: 32px; flex: 0 0 32px; margin-left: auto; place-items: center; border: 1px solid #dcebf3; border-radius: 50%; background: #fff; color: #9aabb5; cursor: pointer; transition: color 160ms ease, border-color 160ms ease, background 160ms ease; }
.company-favorite-button:hover, .company-favorite-button.active { border-color: #f2b24b; background: #fff8e8; color: #e99a22; }
.hot-company-name {
  overflow: hidden;
  color: #173f55;
  font-size: 17px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hot-company-meta {
  overflow: hidden;
  color: #4f7187;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hot-company-meta i { margin: 0 7px; color: #9ca3af; font-style: normal; }
.hot-company-posts { display: grid; flex: 0 0 auto; align-content: start; padding: 4px 0 0; }
.hot-company-post {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  align-content: start;
  box-sizing: border-box;
  width: 340.33px;
  max-width: none;
  min-width: 0;
  gap: 7px;
  padding: 12px 0 10px;
  border: 0;
  border-bottom: 0;
  background: transparent;
  color: #1f2937;
  cursor: pointer;
  text-align: left;
}
.hot-company-post + .hot-company-post { border-top: 1px solid #f0f0f0; }
.hot-company-post:hover strong { color: #2563eb; }
.hot-company-post-heading { display: flex; min-width: 0; align-items: center; justify-content: space-between; gap: 12px; }
.hot-company-post strong {
  overflow: hidden;
  color: #1f2937;
  font-size: 15px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hot-company-post-heading b { flex: 0 0 auto; color: #ef4444; font-size: 14px; font-weight: 600; white-space: nowrap; }
.hot-company-tags { display: flex; min-width: 0; flex-wrap: wrap; gap: 6px; }
.hot-company-tags span {
  height: 27.05px;
  padding: 4px 9px;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  background: #f5f5f5;
  color: #4b5563;
  font-size: 13px;
  line-height: 1.2;
}
.hot-company-empty { margin: 16px 0; color: #6b7280; font-size: 13px; }
.hot-company-more {
  display: none;
}
.hot-company-more:hover,
.hot-company-more:focus-visible { border-color: #52bed5; background: #eefaff; color: #08738d; }

.home-job-card {
  border-color: #d4e7f4 !important;
  border-radius: 10px !important;
  background: #fff !important;
  height: 190px !important;
  min-height: 190px !important;
  box-shadow: 0 8px 22px rgb(59 112 150 / 7%) !important;
  transition: border-color 160ms ease, box-shadow 160ms ease, transform 160ms ease;
}
.home-job-card:nth-child(3n + 1),
.home-job-card:nth-child(3n + 2),
.home-job-card:nth-child(3n) { border-top: 3px solid #7fc8e8 !important; }
.home-job-card:hover { border-color: #8fc9e8 !important; box-shadow: 0 14px 28px rgb(59 112 150 / 13%) !important; transform: translateY(-4px); }
.home-job-card:focus-visible { border-color: #4da7d5 !important; box-shadow: 0 0 0 3px rgb(77 167 213 / 22%), 0 14px 28px rgb(59 112 150 / 13%) !important; outline: 0; }
.home-job-card:active { transform: translateY(-1px); }
.home-job-card .post-title { color: #173f55; }
.home-job-card .post-salary-top { color: #dc3545 !important; }
.home-job-card .post-title-row {
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.home-job-title-actions {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}
.home-job-title-group { display: inline-flex; min-width: 0; align-items: center; gap: 10px; }
.home-job-title-group,
.home-job-chat-button,
.home-job-card .post-card-company {
  border: 0;
  padding: 0;
  background: transparent;
  cursor: pointer;
  font: inherit;
  text-align: left;
}
.home-job-title-group { color: inherit; }
.home-job-chat-button { display: inline-grid; place-items: center; }
.home-job-card .post-card-company { max-width: 68%; }
.home-job-chat-icon { flex: 0 0 auto; color: #b6c0c6; font-size: 17px; }
.home-job-card:hover .home-job-chat-icon,
.home-job-card:focus-visible .home-job-chat-icon { color: #2f91c4; transform: translateX(2px); }
.home-job-card .post-tags span { border-color: #d2e7f4; border-radius: 5px; background: #edf7fd; color: #5e8198; }
.home-job-card .post-tags .post-tag-location,
.home-job-card .post-tags .post-tag-experience,
.home-job-card .post-tags .post-tag-education { border-color: #e1e6ea; background: #f5f5f5; color: #4b5563; }
.home-job-card .post-card-actions { border-top-color: #d4e7f4; background: #f4f9fc; }
.home-job-card .post-card-company { color: #31596f; }
.home-job-company-logo { display: grid; width: 44px; height: 44px; flex: 0 0 44px; place-items: center; overflow: hidden; border: 1px solid #d4e7f4; border-radius: 9px; background: #fff; color: #3986b3; font-size: 15px; font-weight: 700; object-fit: contain; }
.home-job-company-logo-fallback { background: #e7f3fb; }
.home-job-card .company-meta-detail { color: #4f7187; }

@media (min-width: 1280px) and (max-width: 700px) {
  .home-job-card { height: auto !important; min-height: 0 !important; }
  .home-companies-section { padding: 24px 14px 26px; }
  .home-companies-section .home-jobs-heading h2 { font-size: 27px; }
  .home-company-grid { gap: 12px; }
}

/* ══ 首页推荐区（热招职位 / 热门企业）═══════════════════════════
   这一区的配色前后叠了四层：最早是暖棕（#d86616 / #fff0df），后来换青绿
   （#0f8f8c / #16756f），再改成现在的浅天蓝（#7fc8e8 / #173f55）——
   而门户其余六页（职位、企业目录、企业主页、职位详情、沟通）都已统一到
   --job-*。首页是唯一还挂着另一套蓝的地方。这里一次收口，之后改主色只需
   动 .recruit-page 上的 token。

   下面同时处理几个具体问题，逐条注明。 */

/* ── 区块标题 ────────────────────────────────────────────────
   三个区块原本各一个样式：热招职位 28px --recruit-ink(#17343d)、
   热门企业 28px #183b4c、城市热招 32px #202b3d。统一到 token。 */
.home-jobs-heading h2,
.home-companies-section .home-jobs-heading h2,
.city-opportunities-heading h2 {
  color: var(--job-ink);
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.01em;
}

/* ── 分类胶囊 ────────────────────────────────────────────────
   原来有一条 `.home-job-categories button, button:hover, button.active
   { color: #000 }` 把三种状态一起压成黑字，整个筛选器看不出选了谁
   （截图里十颗胶囊长得一模一样）。重新分工：未选白底描边、悬停淡蓝、
   选中实心蓝 —— 和职位页的筛选胶囊同一套语言。 */
.home-job-categories button {
  /* 左右内衬从 25 收到 19：补上「全部」这颗后共 11 颗，原内衬下总宽约 1206px
     超过 1180 的容器，末颗会单独折到第二行居中，看着像个孤零零的补丁。 */
  padding: 5px 19px;
  border-color: var(--job-line);
  background: #fff;
  color: var(--job-muted);
  font-weight: 500;
  transition: border-color 160ms ease, background-color 160ms ease, color 160ms ease;
}
.home-job-categories button:hover {
  border-color: var(--job-blue);
  background: var(--job-blue-soft);
  color: var(--job-blue);
}
.home-job-categories button.active {
  border-color: var(--job-blue);
  background: var(--job-blue);
  color: #fff;
  font-weight: 600;
}

/* ── 职位卡 ──────────────────────────────────────────────────
   去掉顶部那条 3px 亮蓝边。三条 nth-child(3n+1 / 3n+2 / 3n) 规则写的是
   同一个颜色，等于「所有卡片都加一条」，既没区分出任何东西，又让每张卡
   看着像贴了标签页的文件夹。 */
.home-job-card,
.home-job-card:nth-child(3n + 1),
.home-job-card:nth-child(3n + 2),
.home-job-card:nth-child(3n) {
  border-color: var(--job-line) !important;
  border-top-width: 1px !important;
  border-radius: 14px !important;
  box-shadow: 0 1px 2px rgb(27 42 61 / 4%) !important;
}
.home-job-card:hover {
  border-color: var(--job-blue) !important;
  box-shadow: 0 12px 28px rgb(47 111 179 / 12%) !important;
}
.home-job-card:focus-visible {
  border-color: var(--job-blue) !important;
  box-shadow: 0 0 0 3px rgb(47 111 179 / 18%) !important;
}
/* 高度原本写死 190px 的 !important，内容一涨就溢出。改成下限：
   同一行的卡片由 grid 拉伸对齐，仍然一样高。 */
.home-job-card { height: auto !important; min-height: 196px !important; }
.home-job-card .post-title { color: var(--job-ink); }
.home-job-card .post-salary-top { color: var(--job-salary) !important; }

/* ── 沟通按钮 ────────────────────────────────────────────────
   原本是 17px 的 message-square，无边框无底色，摆在职位名旁边读着就是个空
   方框/复选框（截图里「会计 □」）；改成绿底圆钮后，圆环又和卡片上其它元素
   打架。现在是裸图标：无边框、无底板，绿色本身就是「可点」的信号 ——
   门户里绿 = 动作，和职位页的「立即沟通」同族。 */
.home-job-chat-button {
  display: inline-grid;
  width: 24px;
  height: 24px;
  flex: 0 0 24px;
  place-items: center;
  padding: 0;
  border: 0;
  background: transparent;
  transition: color 160ms ease, transform 140ms ease;
}
.home-job-chat-icon { font-size: 16px; color: var(--job-green); }
.home-job-chat-button:active { transform: scale(0.9); }
/* 顺序要紧：悬停卡片的规则在前、悬停按钮的在后，同为 (0,3,0) 时后者胜出。
   卡片悬停那条来自更早的版本（#2f91c4 + translateX），得显式改回绿色。 */
.home-job-card:hover .home-job-chat-icon,
.home-job-card:focus-visible .home-job-chat-icon { color: var(--job-green); transform: none; }
.home-job-chat-button:hover { background: transparent; transform: scale(1.16); }
.home-job-chat-button:hover .home-job-chat-icon { color: color-mix(in srgb, var(--job-green) 82%, #000); }

/* ── 元信息行 ────────────────────────────────────────────────
   地点 / 经验 / 学历做成无底色的灰字 + 小图标（原来三个灰胶囊，
   底色 #f5f5f5 配 #4b5563，对比度低、一屏 27 颗糊成一片）。 */
.home-job-card .post-tags { gap: 6px 14px; }
.home-job-card .post-tags span,
.home-job-card .post-tags .post-tag-location,
.home-job-card .post-tags .post-tag-experience,
.home-job-card .post-tags .post-tag-education {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--job-muted);
  font-size: 12.5px;
}
.home-job-card .post-tags :deep(svg) { width: 13px; height: 13px; flex-shrink: 0; color: #94a9c0; }

.home-job-card .post-card-actions { border-top-color: var(--job-line); background: #f8fafc; }
.home-job-card .post-card-company { color: var(--job-ink); }
.home-job-card .company-meta-detail { color: var(--job-muted); }
.home-job-company-logo { border-color: var(--job-line); color: var(--job-blue); }
.home-job-company-logo-fallback { background: var(--job-blue-soft); color: var(--job-blue); }

/* ── 查看更多 ────────────────────────────────────────────────
   原来是青绿→蓝的对角渐变胶囊（#0f9f9b → #2f80ed），和门户任何一处都不
   呼应。改成 --job-blue 实心 + 加深悬停。 */
.recruit-page .show-more-posts {
  width: auto;
  min-width: 210px;
  padding: 0 34px;
  border: 1px solid var(--job-blue);
  border-radius: 10px;
  background: var(--job-blue);
  color: #fff;
  font-weight: 600;
  box-shadow: 0 8px 20px rgb(47 111 179 / 18%);
}
.recruit-page .show-more-posts:hover,
.recruit-page .show-more-posts:focus-visible {
  border-color: color-mix(in srgb, var(--job-blue) 84%, #000);
  background: color-mix(in srgb, var(--job-blue) 84%, #000);
  color: #fff;
}
.recruit-page .show-more-posts:focus-visible { outline: 2px solid var(--job-blue); outline-offset: 3px; }

/* ── 企业卡 ──────────────────────────────────────────────────
   同类处理：去顶边、统一 token。 */
.home-companies-section { background: transparent; }
/* align-items 必须显式改成 stretch。
   基础规则 `.post-card { display: grid; align-items: center }` 里的 center 会
   一路继承到企业卡上（这里只覆盖了 display 和 flex-direction，没覆盖
   align-items）。column flex 下 center 的含义是「横向居中且按内容收缩」，
   于是头部灰带缩成 366px 居中、左右各留 7px，职位区缩成 297px、各留 21px ——
   职位的左边缘比公司名右移了一大截。
   原来那两条写死的 381.33px / 340.33px 就是在绕这个症状：容器不拉伸，
   只好把宽度算好写死。 */
.home-company-card { align-items: stretch; }
.home-company-card,
.home-company-card:nth-child(3n + 1),
.home-company-card:nth-child(3n + 2),
.home-company-card:nth-child(3n) {
  border-color: var(--job-line) !important;
  border-top-width: 1px !important;
  border-radius: 14px !important;
  box-shadow: 0 1px 2px rgb(27 42 61 / 4%) !important;
}
.home-company-card:hover {
  border-color: var(--job-blue) !important;
  box-shadow: 0 12px 28px rgb(47 111 179 / 12%) !important;
}
/* 头部与职位行的宽度原本写死 381.33px / 340.33px —— 按某一次栅格算出来的
   魔数，换个容器尺寸就对不上，也是 meta 被截断的直接原因。改回随容器。 */
.hot-company-header {
  width: auto;
  margin-bottom: 0;
  border-bottom: 1px solid var(--job-line);
  background: #f8fafc;
}
.hot-company-post { width: auto; }
.hot-company-logo { border-color: var(--job-line); border-radius: 9px; color: var(--job-blue); }
/* 无 logo 时给首字块上淡蓝底（有 logo 的仍留白 —— logo 图多是透明底，需要白背板），
   和职位卡的首字块同一处理 */
.hot-company-logo-fallback { background: var(--job-blue-soft); }
.hot-company-name { color: var(--job-ink); }
.hot-company-meta { color: var(--job-muted); }
.hot-company-meta i { margin: 0 6px; color: #b8c6d6; }
/* 收藏星：裸星，无圆环、无底板。
   未收藏 = 灰色描边（lucide 原生的空心轮廓），收藏后 = 实心绿。
   绿是门户的「动作」色，和职位页的「立即沟通」同族。 */
.company-favorite-button {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  margin-left: auto;
  place-items: center;
  padding: 0;
  border: 0;
  background: transparent;
  color: #a3aebd;
  cursor: pointer;
  font-size: 19px;
  transition: color 160ms ease, transform 140ms ease;
}
/* 只有已收藏才填实；未收藏不写 fill，<path> 上那条 fill="none" 表现属性
   就恢复了作用，星形保持空心轮廓。

   填色必须打到 path 上，不能只写在 svg 上：表现属性虽然输给 CSS 规则，
   却赢过**继承值** —— 写在 svg 上的 fill 传不下去（实测
   computedStyle(svg).fill 已是目标色，path 仍然是 none，星形照旧空心）。 */
.company-favorite-button.active :deep(svg *) { fill: currentColor; }
/* 底板和描边要显式写回 transparent，不能只靠基础规则里的 border: 0 ——
   前面还有一条 `.company-favorite-button:hover, .active { border-color: …;
   background: #fff8e8 }`，它是 (0,2,0)，比基础规则的 (0,1,0) 重，
   光靠位置压不住，悬停时那块暖白圆底会自己冒回来。 */
.company-favorite-button:hover {
  border-color: transparent;
  background: transparent;
  color: var(--job-green);
  transform: scale(1.12);
}
.company-favorite-button:active { transform: scale(0.94); }
.company-favorite-button.active {
  border-color: transparent;
  background: transparent;
  color: var(--job-green);
}
.company-favorite-button:focus-visible { outline: 2px solid var(--job-green); outline-offset: 2px; border-radius: 6px; }
.hot-company-post:hover strong { color: var(--job-blue); }
.hot-company-post-heading b { color: var(--job-salary); }
.hot-company-tags { gap: 6px 14px; }
.hot-company-tags span,
.hot-company-tag-location,
.hot-company-tag-experience,
.hot-company-tag-education {
  display: inline-flex;
  height: auto;
  align-items: center;
  gap: 5px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--job-muted);
  font-size: 12.5px;
}
.hot-company-tags :deep(svg) { width: 13px; height: 13px; flex-shrink: 0; color: #94a9c0; }

@property --banner-color {
  syntax: '<color>';
  inherits: true;
  initial-value: #2a91d7;
}

@property --banner-color-deep {
  syntax: '<color>';
  inherits: true;
  initial-value: #0b4a88;
}

@property --banner-color-soft {
  syntax: '<color>';
  inherits: true;
  initial-value: #d8efff;
}

.recruit-page--home {
  --banner-color: #2a91d7;
  --banner-color-deep: #0b4a88;
  --banner-color-soft: #d8efff;
  --banner-foreground: #fff;
  position: relative;
  isolation: isolate;
  background: #f3f5f8;
  transition: background-color 600ms ease;
}

/* 轮播区域两侧的留白染色：从海报顶部向下自然衰减。 */
.recruit-page--home::before {
  position: absolute;
  z-index: 0;
  top: 227px;
  right: 0;
  left: 0;
  height: 400px;
  background: linear-gradient(
    to bottom,
    rgb(var(--theme-rgb, 42 145 215) / 0) 0,
    rgb(var(--theme-rgb, 42 145 215) / 0.12) 24px,
    rgb(var(--theme-rgb, 42 145 215) / 0)
  );
  content: '';
  pointer-events: none;
  transition: opacity 600ms ease;
}

.recruit-page--home > *:not(.recruit-topbar) {
  position: relative;
  z-index: 1;
}

.recruit-page--home > .recruit-hero {
  background: linear-gradient(
    180deg,
    rgb(var(--theme-rgb, 42 145 215) / 0.84),
    rgb(var(--theme-rgb, 42 145 215) / 0.46) 58%,
    rgb(var(--theme-rgb, 42 145 215) / 0.08)
  );
  border-color: transparent;
  box-shadow: none;
  transition: background 600ms ease;
}

/* 搜索控件与热门职位文字承接当前海报主题，白色表面保持原有对比度。 */
.jobs-search-form .ant-input-affix-wrapper {
  min-height: 54px;
  border: 2px solid var(--banner-color, #2a91d7);
  border-radius: 12px 0 0 12px;
  box-shadow: none;
}
.jobs-search-form .ant-btn {
  min-width: 118px;
  height: 54px;
  border: 0;
  border-radius: 0 12px 12px 0;
  background: color-mix(in srgb, var(--banner-color-deep, #0b4a88) 64%, var(--banner-color, #2a91d7) 36%);
  box-shadow: none;
  font-size: 17px;
  font-weight: 700;
}
.recruit-page--home .hero-search .ant-input-affix-wrapper {
  border-color: var(--banner-color, #2a91d7);
  transition: border-color 600ms ease, box-shadow 600ms ease;
}
.recruit-page--home .hero-search .ant-input-affix-wrapper:hover,
.recruit-page--home .hero-search .ant-input-affix-wrapper:focus-within {
  border-color: var(--banner-color-deep, #0b4a88);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--banner-color) 18%, transparent);
}
.recruit-page--home .hero-search .ant-input-prefix,
.recruit-page--home .hero-search :deep(.ant-input-prefix) {
  color: color-mix(in srgb, var(--banner-color-deep, #0b4a88) 72%, #60758a 28%);
  transition: color 600ms ease;
}
.recruit-page--home .hero-search :deep(.ant-input::placeholder) {
  color: color-mix(in srgb, var(--banner-color-deep, #0b4a88) 58%, #94a3b8 42%);
  transition: color 600ms ease;
}
.recruit-page--home .hero-search .ant-btn {
  background: color-mix(in srgb, var(--banner-color-deep, #0b4a88) 64%, var(--banner-color, #2a91d7) 36%);
  transition: background 600ms ease, border-color 600ms ease;
}
.recruit-page--home .hero-search .ant-btn:hover,
.recruit-page--home .hero-search .ant-btn:focus-visible {
  background: var(--banner-color-deep, #0b4a88);
}
.recruit-page--home .quick-searches {
  color: color-mix(in srgb, var(--banner-color-deep, #0b4a88) 62%, #60758a 38%);
  transition: color 600ms ease;
}
.recruit-page--home .quick-searches button {
  border-color: color-mix(in srgb, var(--banner-color) 28%, #d8e9f2 72%);
  color: color-mix(in srgb, var(--banner-color-deep, #0b4a88) 70%, #168da0 30%);
  transition: color 600ms ease, border-color 600ms ease, background 600ms ease, transform 160ms ease;
}
.recruit-page--home .quick-searches button:hover,
.recruit-page--home .quick-searches button:focus-visible {
  border-color: var(--banner-color, #2a91d7);
  background: color-mix(in srgb, var(--banner-color) 12%, #fff 88%);
  color: var(--banner-color-deep, #0b4a88);
}

.jobs-search-form .ant-input-affix-wrapper:hover,
.jobs-search-form .ant-input-affix-wrapper:focus-within {
  border-color: var(--banner-color-deep, #0b4a88);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--banner-color) 18%, transparent);
}
.jobs-search-form .ant-input-prefix,
.jobs-search-form :deep(.ant-input-prefix) {
  color: color-mix(in srgb, var(--banner-color-deep, #0b4a88) 72%, #60758a 28%);
}
.jobs-search-form :deep(.ant-input::placeholder) {
  color: color-mix(in srgb, var(--banner-color-deep, #0b4a88) 58%, #94a3b8 42%);
}

.recruit-page--home > .recruit-category-showcase {
  border: 0;
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--banner-color-soft) 72%, #fff 28%),
    #fff 54%,
    color-mix(in srgb, var(--banner-color-soft) 48%, #fff 52%)
  );
  transition: background 600ms ease, border-color 600ms ease, box-shadow 600ms ease;
}

/* 区域 1 与区域 2 共用同一条连续背景，避免搜索区和两侧留白之间出现断层。 */
.recruit-page--home {
  background: linear-gradient(
    to bottom,
    color-mix(in srgb, var(--banner-color-deep, #0b4a88) 84%, #f3f5f8 16%) 60px,
    color-mix(in srgb, var(--banner-color, #2a91d7) 62%, #f3f5f8 38%) 228px,
    color-mix(in srgb, var(--banner-color, #2a91d7) 12%, #f3f5f8 88%) 430px,
    #f3f5f8 700px
  );
  transition: background 600ms ease;
}
.recruit-page--home::before { display: none; }
.recruit-page--home > .recruit-hero { background: transparent; }

/* 富页脚页面自身不能保留普通招聘页的 24px 底部留白。 */
.recruit-page--rich-footer {
  min-height: 100vh;
  margin-top: 0 !important;
  padding-bottom: 0 !important;
  align-content: stretch;
}

/* 去掉基类那层右上角的淡绿径向光晕（公司 / 在线简历 / 用户页）。
   这里必须排除首页：--home 自带整套渐变背景，与本规则同权重、位置却更靠前，
   不排除的话这条 transparent 会把它一并抹掉，首页顶部那条蓝色渐变就没了。 */
.recruit-page--rich-footer:not(.recruit-page--home) {
  background: transparent;
}

@media (prefers-reduced-motion: reduce) {
  .recruit-page--home,
  .recruit-page--home > .recruit-hero,
  .recruit-page--home > .recruit-category-showcase { transition: none; }
}
</style>

<style>
/* 非 scoped：这里要命中的是公共框架的容器类，scoped 加不上 data-v 属性。

   背景：框架给 .portal-main--recruit 设了 overflow-x: auto，而 CSS 规定
   一轴非 visible 时另一轴会计算成 auto，于是 main.portal-main 变成了一个
   高度等于内容、永远不会滚动的滚动容器。它仍是内部元素最邻近的滚动容器，
   于是其内所有 position: sticky 全部失效 —— 职位详情卡钉不住，
   连页面自己的招聘顶部导航条也钉不住。

   框架里已经有一段同样的补丁，但只写了首页（:has(.recruit-page--home)）。
   这里补上职位页，作用范围严格限定在 .recruit-page--jobs 存在的时刻，
   选择器特异性高于框架原规则，无需 !important。
   放开后滚动落到整页上，用的是浏览器原生滚动条。 */
.portal-shell--recruit:has(.recruit-page--jobs),
.portal-main--recruit:has(.recruit-page--jobs) {
  overflow: visible;
}
</style>
