package cn.iocoder.txgy.module.bidding.service.externalfetch;
// 规范依据：设计文档 v0.6 §四/§六/§九（采集源白名单/频控/通道；启用源=外链 /go 白名单成员）

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo.CrawlSourcePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo.CrawlSourceRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo.CrawlSourceSaveReqVO;

/**
 * 采集源配置管理（G 轨）。
 *
 * <p>不变量：
 * <ul>
 *   <li>sourceCode 唯一；baseUrl 必须 http(s) 且可解析 host；channelCode 必须是已注册通道（未知通道拒绝保存，防配置错乱）；</li>
 *   <li>enabled=true 的源才进入外链 /go 白名单（fail-closed：默认关闭）；</li>
 *   <li>删除/停用保护：存在未终态采集任务或展示中（ACTIVE）外部公告引用该源时拒绝——避免“列表还在、原文跳转已断/任务孤儿”。</li>
 * </ul>
 */
public interface CrawlSourceService {

    /** 采集源分页（G 轨配置列表） */
    PageResult<CrawlSourceRespVO> getSourcePage(CrawlSourcePageReqVO reqVO);

    /** 采集源详情 */
    CrawlSourceRespVO getSource(Long id);

    /** 新建采集源（默认不启用） */
    Long createSource(CrawlSourceSaveReqVO reqVO);

    /** 修改采集源（代码不可改） */
    void updateSource(CrawlSourceSaveReqVO reqVO);

    /** 启停采集源（启用=进入 /go 白名单；停用前须无 ACTIVE 公告引用，否则拒绝） */
    void setSourceEnabled(Long id, Boolean enabled);

    /** 删除采集源（物理删除配置行；须无未终态任务且无 ACTIVE 公告引用） */
    void deleteSource(Long id);

}
