package cn.iocoder.txgy.module.bidding.service.bidagent.project;

import cn.iocoder.txgy.module.bidding.api.dto.bidagent.BidContextRespDTO;

/**
 * 项目上下文服务（项目事实卡 + 招标文件解析 + 全局事实）
 */
public interface BidProjectService {

    /**
     * 获取项目上下文
     *
     * @param projectId 项目编号
     * @param tenantId  租户编号
     */
    BidContextRespDTO getContext(Long projectId, Long tenantId);
}
