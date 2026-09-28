package cn.iocoder.txgy.module.bidding.service.bidagent.shadow;

import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidTaskShadowDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidagent.BidTaskShadowMapper;
import cn.iocoder.txgy.module.bidding.api.dto.bidagent.BidTaskShadowReqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 任务影子状态服务实现
 */
@Service
@Slf4j
public class BidTaskShadowServiceImpl implements BidTaskShadowService {

    @Resource
    private BidTaskShadowMapper shadowMapper;

    @Override
    public void report(BidTaskShadowReqDTO reqDTO) {
        if (reqDTO.getTenantId() == null) {
            log.warn("[report][缺少租户编号，跳过影子状态上报] taskId={}", reqDTO.getTaskId());
            return;
        }
        TenantUtils.execute(reqDTO.getTenantId(), () -> {
            BidTaskShadowDO existing = shadowMapper.selectByTaskId(reqDTO.getTaskId());
            BidTaskShadowDO shadow = existing != null ? existing : new BidTaskShadowDO();
            shadow.setTenantId(reqDTO.getTenantId());
            shadow.setTaskId(reqDTO.getTaskId());
            shadow.setProjectId(reqDTO.getProjectId());
            shadow.setStatus(reqDTO.getStatus());
            shadow.setPhase(reqDTO.getPhase());
            shadow.setProgress(reqDTO.getProgress());
            shadow.setMessage(reqDTO.getMessage());
            shadow.setAgentUpdatedAt(reqDTO.getUpdatedAt());
            if (existing == null) {
                shadowMapper.insert(shadow);
            } else {
                shadowMapper.updateById(shadow);
            }
            return null;
        });
    }
}
