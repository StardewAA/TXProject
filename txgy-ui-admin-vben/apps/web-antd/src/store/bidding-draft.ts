/* 招投标域 · 前端内存草稿（Pinia，不落库）
 * 用途：项目台账「新建招标项目」「新建标段」由弹窗大表单改为「内存草稿 + 详情页 create 态」。
 * 草稿只存内存；点保存时才调后端 POST 创建（后端自动生成正式编号），成功即移除本草稿。
 * 作用域：两个草稿桶
 *   · tenderProjectDrafts：父级 = 项目编号 → 招标项目草稿数组
 *   · bidSectionDrafts：  父级 = 招标项目编号 → 标段草稿数组
 * 草稿 name 随 fields 实时同步（列表显示「名称 /（待完善）」）。 */
import { ref } from 'vue';

import { defineStore } from 'pinia';

export interface BiddingDraft {
  /** 临时编号，如 DRAFT_1758000000000 */
  tempCode: string;
  /** 上级关联编号（项目编号 / 招标项目编号） */
  parentCode: string;
  /** 上级类型：PROJECT / TENDER_PROJECT / BID_SECTION（公告草稿的父级 = 标段编号） */
  parentType: 'PROJECT' | 'TENDER_PROJECT' | 'BID_SECTION';
  /** 名称（初始为空；随字段实时同步） */
  name: string;
  /** 创建时间 */
  createdAt: string;
  /** 用户填过的字段值（规范码 → 值，编辑过程中暂存） */
  fields: Record<string, string>;
}

/** 招标项目草稿：招标项目名在 TENDER_PROJECT_NAME；标段名在 BID_SECTION_NAME；项目名在 PROJECT_NAME */
const TP_NAME_CODE = 'TENDER_PROJECT_NAME';
const BS_NAME_CODE = 'BID_SECTION_NAME';
const P_NAME_CODE = 'PROJECT_NAME';

export const useBiddingDraftStore = defineStore('bidding-draft', () => {
  // 顶层项目草稿（项目台账列表「登记项目」，无父级）
  const projectDrafts = ref<BiddingDraft[]>([]);
  // 项目编号 → 招标项目草稿
  const tenderProjectDrafts = ref<Record<string, BiddingDraft[]>>({});
  // 招标项目编号 → 标段草稿
  const bidSectionDrafts = ref<Record<string, BiddingDraft[]>>({});

  /* ================= 项目草稿（顶层） ================= */
  function addProjectDraft(draft: BiddingDraft): void {
    projectDrafts.value = [...projectDrafts.value, draft];
  }
  function removeProjectDraft(tempCode: string): void {
    projectDrafts.value = projectDrafts.value.filter((d) => d.tempCode !== tempCode);
  }
  function updateProjectDraftFields(tempCode: string, fields: Record<string, string>): void {
    const draft = projectDrafts.value.find((d) => d.tempCode === tempCode);
    if (draft) {
      draft.fields = { ...fields };
      draft.name = fields[P_NAME_CODE] || draft.name;
    }
  }
  function findProjectDraft(tempCode: string): BiddingDraft | undefined {
    return projectDrafts.value.find((d) => d.tempCode === tempCode);
  }

  /* ================= 招标项目草稿 ================= */
  function addTenderProjectDraft(projectCode: string, draft: BiddingDraft): void {
    tenderProjectDrafts.value[projectCode] = [...(tenderProjectDrafts.value[projectCode] ?? []), draft];
  }
  function removeTenderProjectDraft(projectCode: string, tempCode: string): void {
    tenderProjectDrafts.value[projectCode] = (tenderProjectDrafts.value[projectCode] ?? []).filter((d) => d.tempCode !== tempCode);
  }
  function updateTenderProjectDraftFields(tempCode: string, fields: Record<string, string>): void {
    for (const key of Object.keys(tenderProjectDrafts.value)) {
      const list = tenderProjectDrafts.value[key] ?? [];
      const draft = list.find((d) => d.tempCode === tempCode);
      if (draft) {
        draft.fields = { ...fields };
        draft.name = fields[TP_NAME_CODE] || draft.name;
        return;
      }
    }
  }
  function findTenderProjectDraft(tempCode: string): BiddingDraft | undefined {
    for (const list of Object.values(tenderProjectDrafts.value)) {
      const d = list.find((x) => x.tempCode === tempCode);
      if (d) return d;
    }
    return undefined;
  }

  /* ================= 标段草稿 ================= */
  function addBidSectionDraft(tenderProjectCode: string, draft: BiddingDraft): void {
    bidSectionDrafts.value[tenderProjectCode] = [...(bidSectionDrafts.value[tenderProjectCode] ?? []), draft];
  }
  function removeBidSectionDraft(tenderProjectCode: string, tempCode: string): void {
    bidSectionDrafts.value[tenderProjectCode] = (bidSectionDrafts.value[tenderProjectCode] ?? []).filter((d) => d.tempCode !== tempCode);
  }
  function updateBidSectionDraftFields(tempCode: string, fields: Record<string, string>): void {
    for (const key of Object.keys(bidSectionDrafts.value)) {
      const list = bidSectionDrafts.value[key] ?? [];
      const draft = list.find((d) => d.tempCode === tempCode);
      if (draft) {
        draft.fields = { ...fields };
        draft.name = fields[BS_NAME_CODE] || draft.name;
        return;
      }
    }
  }
  function findBidSectionDraft(tempCode: string): BiddingDraft | undefined {
    for (const list of Object.values(bidSectionDrafts.value)) {
      const d = list.find((x) => x.tempCode === tempCode);
      if (d) return d;
    }
    return undefined;
  }

  /* ================= 公告草稿（父级 = 标段编号） =================
   * 标段详情页「关联公告」区块「＋ 新建公告」：内存草稿（不落库），
   * 保存时才 POST /bidding/notice/create（后端生成正式公告记录）；成功即移除本草稿。 */
  const noticeDrafts = ref<Record<string, BiddingDraft[]>>({});
  function addNoticeDraft(bidSectionCode: string, draft: BiddingDraft): void {
    noticeDrafts.value[bidSectionCode] = [...(noticeDrafts.value[bidSectionCode] ?? []), draft];
  }
  function removeNoticeDraft(bidSectionCode: string, tempCode: string): void {
    noticeDrafts.value[bidSectionCode] = (noticeDrafts.value[bidSectionCode] ?? []).filter((d) => d.tempCode !== tempCode);
  }
  function updateNoticeDraftFields(tempCode: string, fields: Record<string, string>): void {
    for (const key of Object.keys(noticeDrafts.value)) {
      const list = noticeDrafts.value[key] ?? [];
      const draft = list.find((d) => d.tempCode === tempCode);
      if (draft) {
        draft.fields = { ...fields };
        draft.name = fields.NOTICE_NAME || fields.PUBLICITY_NAME || draft.name;
        return;
      }
    }
  }
  function findNoticeDraft(tempCode: string): BiddingDraft | undefined {
    for (const list of Object.values(noticeDrafts.value)) {
      const d = list.find((x) => x.tempCode === tempCode);
      if (d) return d;
    }
    return undefined;
  }

  /** 按临时编号在四个桶里找草稿（详情页 create 态校验用） */
  function findDraft(tempCode: string): BiddingDraft | undefined {
    return findProjectDraft(tempCode) ?? findTenderProjectDraft(tempCode) ?? findBidSectionDraft(tempCode) ?? findNoticeDraft(tempCode);
  }

  return {
    projectDrafts,
    addProjectDraft,
    removeProjectDraft,
    updateProjectDraftFields,
    findProjectDraft,
    tenderProjectDrafts,
    addTenderProjectDraft,
    removeTenderProjectDraft,
    updateTenderProjectDraftFields,
    findTenderProjectDraft,
    bidSectionDrafts,
    addBidSectionDraft,
    removeBidSectionDraft,
    updateBidSectionDraftFields,
    findBidSectionDraft,
    noticeDrafts,
    addNoticeDraft,
    removeNoticeDraft,
    updateNoticeDraftFields,
    findNoticeDraft,
    findDraft,
  };
});
