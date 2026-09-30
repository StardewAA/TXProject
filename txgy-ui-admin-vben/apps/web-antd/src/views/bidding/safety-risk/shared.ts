/* 安许联动 · 三个主体面板的共享代码（2026-09-30「安许风险联动」按主体拆分）
 *
 * 【来源】原 `views/bidding/safety-risk/index.vue`（1698 行单体页，菜单 60005500）。该页的页头总说明
 *   （原 L2-18：四块能力 / 七个端点 / 岗位码表与字典口径）已按主体拆进三个面板的文件头；页头那句
 *   "菜单终态 60005500 / component bidding/safety-risk/index / componentName SafetyRiskIndex" 已失效：
 *   该菜单在 sql/history/2026-09-28-credential-three-ledger-ia.sql 第 2 步里被改为「企业资质(60005200)下的按钮」
 *   （type=3、path/component 置空），前端不再有指向该页的路由，故 index.vue 按本次拆分删除。
 *
 * 【为什么要有本文件】拆分前这些实现只有一份（在 index.vue 里）；拆成 panels/ 下三个面板后：
 *   · ② 多岗兼任校验（员工侧）与 ④ 关键岗位任职/变更登记（项目侧）都要「关键岗位码表」（L117-129）；
 *   · ① 风险卡片 / 风险大盘与风险工单（企业侧）与 ②③（员工侧）都要「企业主体 → entId」（L131/L133-137/L234-253）；
 *   · 风险工单「指派责任人」（企业侧）与 ④b「任职人员」（项目侧）都要「员工档案下拉」（L406-424）；
 *   复制多份必然漂移（口径：同一能力只留一处实现），故抽到本文件。
 *
 * 【搬迁原则】函数体 / 文案 / 接口调用 / 权限码 / 字段名与拆分前**逐字一致**；唯一的形式变化是把
 *   页面级单例 ref 改成 use*() 工厂返回的实例 —— 三个宿主各自只挂一个面板（互不同屏），语义与拆分前一致；
 *   同时也避免模块级单例在不同台账页之间串状态（切租户后残留上一次的 entId）。
 *
 * 【权限】读取 bidding:qualification:permit:query / 写动作 bidding:qualification:permit:update
 *   （页面级口径不变，判权实现见 useSafetyCan）。 */
import { computed, ref } from 'vue';

import { useAccess } from '@vben/access';
import { useAccessStore } from '@vben/stores';

import { getPersonnelProfilePage, type PersonnelProfileApi } from '#/api/bidding/personnel';
import { fetchSubjectProfile } from '#/api/bidding/subject';

import { useCertMeta } from '../certificate/useCertMeta';

/* ===== 权限判定（原 index.vue L107-115 原样搬迁） ===== */
/** 拆分前是页面内的 can()：accessCodes 为空（未接权限）或持有通配时放行 */
export function useSafetyCan() {
  const { hasAccessByCodes } = useAccess();
  const accessStore = useAccessStore();

  function can(code: string): boolean {
    const codes = accessStore.accessCodes ?? [];
    if (codes.length === 0) return true;
    return codes.includes('*') || codes.includes('*:*:*') || hasAccessByCodes([code]);
  }

  return { can };
}

/* ===== 关键岗位码表（原 index.vue L117-129 原样搬迁） ===== */
/** 关键岗位码表兜底（字典 key_post 为主路径——后端 KeyPostEnum 是本码表的唯一事实来源，
 * 字典可在后台维护文案；此数组仅在字典尚未下发的环境兜底，不参与主展示路径） */
export const KEY_POST_OPTIONS = [
  { label: '项目经理（PROJECT_MANAGER）', value: 'PROJECT_MANAGER' },
  { label: '技术负责人（TECH_LEADER）', value: 'TECH_LEADER' },
  { label: '专职安全员（SAFETY_OFFICER）', value: 'SAFETY_OFFICER' },
  { label: '质量负责人（QUALITY_OFFICER）', value: 'QUALITY_OFFICER' },
  { label: '施工员（CONSTRUCTION_ENGINEER）', value: 'CONSTRUCTION_ENGINEER' },
];

/** 下拉选项：字典优先；字典未下发（如后端未升级）时回落兜底数组，避免下拉空白导致无法登记 */
export function useKeyPostOptions() {
  const { dictOptions } = useCertMeta();
  return computed(() =>
    dictOptions('key_post').length > 0 ? dictOptions('key_post') : KEY_POST_OPTIONS,
  );
}

/* ===== 企业主体 → entId（原 index.vue L131、L133-137、L234-253 原样搬迁） =====
   口径依据：07-主体域前端交付-业务口径说明（企业主体=法人/其他组织，uscc 唯一键）；
   原实现让使用者手填"企业主体 id"，属"用户不可能知道的字段"，改为接口带出。
   缺口 B：后端若提供 GET /bidding/subject/profile/list（多主体选项），只需改本函数 —— 见
   docs/电子证照前前端契约/后端变更请求-平台企业资质审查与录入方式扩展-2026-09-20.md §3 */
export function useCurrentSubject() {
  const entId = ref<number | undefined>(undefined);
  const subjectLoading = ref(false);
  const currentSubject = ref<null | { id: number; label: string }>(null);

  /* 本企业：普通租户只能看自己，无需也不应"选企业"——由主体档案自动带出并只读展示名称 */
  async function loadCurrentSubject(): Promise<void> {
    subjectLoading.value = true;
    try {
      // 契约《主体档案合并一租户一档》：投标/招标共用同一份企业档案，单次无类型调用
      const profile = await fetchSubjectProfile().catch(() => null);
      if (profile?.id !== undefined && profile?.id !== null && profile.subjectName) {
        const label = profile.uscc ? profile.subjectName + '（' + profile.uscc + '）' : profile.subjectName;
        currentSubject.value = { id: Number(profile.id), label };
        if (entId.value === undefined) {
          entId.value = Number(profile.id);
        }
      }
    } finally {
      subjectLoading.value = false;
    }
  }

  /* subjectLoading / currentSubject 是原页头「本企业名称」展示用的（页头随嵌入模式一并移除，当前无消费者）。
     保留的理由：它们与 entId 由同一次请求产出，删掉就得分叉出第二份主体查询；将来要在台账页补
     "本企业：xxx + 重新扫描/刷新"工具条时可直接接上，不必重写取主体的口径。 */
  return { currentSubject, entId, loadCurrentSubject, subjectLoading };
}

/* ===== 员工档案下拉（原 index.vue L406-424 原样搬迁） =====
   两个消费者：企业侧风险工单「指派责任人」的筛选与指派弹窗、项目侧 ④b「任职人员」下拉。
   口径不变：按姓名远程搜索、pageSize=20、idCard 只写不读故用 姓名+工号 做标识。 */
export function useAssignPersons() {
  const assignPersons = ref<Array<{ label: string; value: number }>>([]);
  const assignPersonLoading = ref(false);

  async function loadAssignPersons(keyword?: string) {
    assignPersonLoading.value = true;
    try {
      const page = await getPersonnelProfilePage({ pageNo: 1, pageSize: 20, personName: keyword ?? '' });
      assignPersons.value = (page.list ?? [])
        .map((p: PersonnelProfileApi.Profile) => ({
          // idCard 只写不读（列表回传 idCardMasked），故用姓名 + 工号做标识
          label: `${p.personName ?? '—'}${p.personNo ? `（${p.personNo}）` : ''}`,
          value: Number(p.id ?? 0),
        }))
        .filter((o) => o.value > 0);
    } catch {
      // 静默：档案列表不可用时下拉为空
    } finally {
      assignPersonLoading.value = false;
    }
  }

  return { assignPersonLoading, assignPersons, loadAssignPersons };
}
