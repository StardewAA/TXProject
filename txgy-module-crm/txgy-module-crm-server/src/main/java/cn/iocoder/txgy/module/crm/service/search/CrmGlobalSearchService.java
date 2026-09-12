package cn.iocoder.txgy.module.crm.service.search;

import cn.iocoder.txgy.module.crm.controller.admin.search.vo.CrmGlobalSearchRespVO;

/**
 * CRM 全局检索 Service 接口
 *
 * @author txgycloud
 */
public interface CrmGlobalSearchService {

    /**
     * 全局检索：按关键字（公司名 / 手机号 / 电话 / 编号）跨模块搜索
     *
     * @param keyword 关键字
     * @param limit   每个模块返回的最大条数
     * @return 检索结果
     */
    CrmGlobalSearchRespVO searchAll(String keyword, Integer limit);

}
