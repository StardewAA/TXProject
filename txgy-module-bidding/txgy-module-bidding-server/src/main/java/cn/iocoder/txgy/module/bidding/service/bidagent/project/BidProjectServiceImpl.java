package cn.iocoder.txgy.module.bidding.service.bidagent.project;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidProjectDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidagent.BidProjectMapper;
import cn.iocoder.txgy.module.bidding.api.dto.bidagent.BidContextRespDTO;
import cn.iocoder.txgy.module.bidding.service.bidagent.fact.BidFactService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_PROJECT_NOT_EXISTS;

/**
 * 项目上下文服务实现
 */
@Service
public class BidProjectServiceImpl implements BidProjectService {

    @Resource
    // 注意：字段名须与 Mapper Bean 名一致（bidProjectMapper）。写成 projectMapper 时，
    // @Resource 会按字段名先命中平台既有 dal.mysql.project.ProjectMapper（JDK 动态代理，类型不符）而注入失败。
    private BidProjectMapper bidProjectMapper;

    @Resource
    private BidFactService factService;

    @Override
    public BidContextRespDTO getContext(Long projectId, Long tenantId) {
        if (tenantId == null) {
            return emptyContext();
        }
        return TenantUtils.execute(tenantId, () -> {
            BidProjectDO project = bidProjectMapper.selectById(projectId);
            if (project == null) {
                throw exception(BID_PROJECT_NOT_EXISTS);
            }
            BidContextRespDTO resp = new BidContextRespDTO();
            resp.setProjectFacts(parseJson(project.getFactsJson()));
            resp.setTenderParse(parseJson(project.getTenderParseJson()));
            resp.setGlobalFacts(factService.getFacts(tenantId, null));
            return resp;
        });
    }

    private BidContextRespDTO emptyContext() {
        BidContextRespDTO resp = new BidContextRespDTO();
        resp.setProjectFacts(Map.of());
        resp.setTenderParse(Map.of());
        resp.setGlobalFacts(java.util.List.of());
        return resp;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJson(String json) {
        if (StrUtil.isBlank(json)) {
            return Map.of();
        }
        try {
            return JSONUtil.toBean(json, Map.class);
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
