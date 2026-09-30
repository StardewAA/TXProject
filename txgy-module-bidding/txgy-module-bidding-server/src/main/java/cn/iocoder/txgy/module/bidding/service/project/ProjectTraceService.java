package cn.iocoder.txgy.module.bidding.service.project;

// [规范依据]《招投标模块开发规范》§4.4（跨域只读聚合需服务端完成，前端逐域拼装易口径不一致）
//           + 数据规范 V2.0 编号体系（project_code / tender_project_code / bid_section_code 三级关联）
// [业务目标] 一个项目（或招标项目/标段）追溯到全部信息：主档、标段、内部公告、外部公告、备案、关注、参与主体、统计
// [为什么] 六个业务域分散存储，追溯若由前端多次调用（且需自行推断关联关系）会出现遗漏与性能问题；
//           服务端按编号集合一次精确命中，并对"外部公告 BID_SECTION_CODES 串"做编号级匹配（不做全表 LIKE 检索）

import cn.iocoder.txgy.module.bidding.controller.admin.project.vo.ProjectTraceRespVO;

public interface ProjectTraceService {

    /**
     * 招标项目维度追溯（主入口：项目下所有信息）。
     *
     * @param tenderProjectCode 招标项目编号
     */
    ProjectTraceRespVO traceByTenderProject(String tenderProjectCode);

    /**
     * 项目维度追溯（向上聚合该项目下全部招标项目及其标段/公告等）。
     *
     * @param projectCode 项目编号
     */
    ProjectTraceRespVO traceByProject(String projectCode);

    /**
     * 项目全景追溯（契约《管理端出参放开政务字段》方案 A）。
     *
     * <p>追溯出参里内嵌项目/招标项目/标段（{@code ProjectTraceRespVO}），
     * 故 includeGovernment 会一并作用于这些内嵌对象，避免"详情能看到、追溯里看不到"的口径割裂。
     *
     * @param includeGovernment true=管理端填报视角；false=对外口径
     */
    ProjectTraceRespVO traceByProject(String projectCode, boolean includeGovernment);

    /**
     * 标段维度追溯（标段 → 所属招标项目 → 项目）。
     *
     * @param bidSectionCode 标段（包）编号
     */
    ProjectTraceRespVO traceByBidSection(String bidSectionCode);
}
