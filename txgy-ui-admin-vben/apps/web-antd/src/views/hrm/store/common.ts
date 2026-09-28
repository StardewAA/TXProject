import type { HrmCompanyProfileApi } from '#/api/hrm/company';
import type { HrmInsuranceSchemeApi } from '#/api/hrm/insurance/scheme';

import { acceptHMRUpdate, defineStore } from 'pinia';

import { getCompanyProfileList } from '#/api/hrm/company';
import { getInsuranceSchemeSimpleList } from '#/api/hrm/insurance/scheme';

/**
 * HRM 公共基础数据缓存（F-06：跨页面共享状态集中管理）
 *
 * <p>用工主体、社保方案这类"下拉数据"在多个页面被反复请求（用工主体信息页、合同模板、
 * 签署记录、用工需求单…），以前每个页面各拉各的。这里统一放到 Pinia store 里按 TTL 缓存，
 * 页面之间共享同一份数据；保存/删除后调用 clear 主动失效。</p>
 */

/** 缓存有效期：5 分钟 */
const CACHE_TTL = 5 * 60 * 1000;

export const useHrmCommonStore = defineStore('hrm-common', {
  state: () => ({
    /** 用工主体（公司信息）列表 */
    companyProfiles: [] as HrmCompanyProfileApi.CompanyProfile[],
    companyProfilesLoadedAt: 0,
    /** 启用中的社保方案列表 */
    insuranceSchemes: [] as HrmInsuranceSchemeApi.InsuranceScheme[],
    insuranceSchemesLoadedAt: 0,
  }),
  getters: {
    /** 用工主体下拉选项 */
    companyProfileOptions(state): Array<{ label: string; value: number }> {
      return state.companyProfiles
        .filter((item) => item.id !== undefined)
        .map((item) => ({
          label: item.name ?? `主体 ${item.id}`,
          value: item.id as number,
        }));
    },
    /** 社保方案下拉选项 */
    insuranceSchemeOptions(state): Array<{ label: string; value: number }> {
      return state.insuranceSchemes
        .filter((item) => item.id !== undefined)
        .map((item) => ({
          label: item.name ?? `方案 ${item.id}`,
          value: item.id as number,
        }));
    },
  },
  actions: {
    /**
     * 获得用工主体列表（命中缓存直接返回）
     *
     * @param force 是否强制刷新
     */
    async getCompanyProfiles(force = false) {
      const fresh =
        this.companyProfilesLoadedAt > 0 &&
        Date.now() - this.companyProfilesLoadedAt < CACHE_TTL;
      if (!force && fresh) {
        return this.companyProfiles;
      }
      const list = (await getCompanyProfileList()) ?? [];
      this.companyProfiles = list;
      this.companyProfilesLoadedAt = Date.now();
      return this.companyProfiles;
    },

    /** 用工主体变更后主动失效缓存 */
    clearCompanyProfiles() {
      this.companyProfiles = [];
      this.companyProfilesLoadedAt = 0;
    },

    /**
     * 获得启用中的社保方案列表（命中缓存直接返回）
     *
     * @param force 是否强制刷新
     */
    async getInsuranceSchemes(force = false) {
      const fresh =
        this.insuranceSchemesLoadedAt > 0 &&
        Date.now() - this.insuranceSchemesLoadedAt < CACHE_TTL;
      if (!force && fresh) {
        return this.insuranceSchemes;
      }
      const list = (await getInsuranceSchemeSimpleList()) ?? [];
      this.insuranceSchemes = list;
      this.insuranceSchemesLoadedAt = Date.now();
      return this.insuranceSchemes;
    },

    /** 社保方案变更后主动失效缓存 */
    clearInsuranceSchemes() {
      this.insuranceSchemes = [];
      this.insuranceSchemesLoadedAt = 0;
    },
  },
});

if (import.meta.hot) {
  import.meta.hot.accept(acceptHMRUpdate(useHrmCommonStore, import.meta.hot));
}
