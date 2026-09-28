package cn.iocoder.txgy.module.product.service.audit;

import cn.iocoder.txgy.module.product.dal.dataobject.audit.ProductBusinessOperationAuditDO;
import cn.iocoder.txgy.module.product.dal.mysql.audit.ProductBusinessOperationAuditMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import static cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService.invalid;

/** 统一记录商城经营关键动作，避免仅依赖前端日志或可变业务表。 */
@Service
public class ProductBusinessOperationAuditService {

    @Resource private ProductBusinessOperationAuditMapper mapper;

    public void success(Long enterpriseId, Long shopId, String actorType, Long actorId,
                        String action, String resourceType, Long resourceId, String detail) {
        ProductBusinessOperationAuditDO row = new ProductBusinessOperationAuditDO();
        row.setEnterpriseId(enterpriseId);
        row.setShopId(shopId);
        row.setActorType(actorType);
        row.setActorId(actorId);
        row.setAction(action);
        row.setResourceType(resourceType);
        row.setResourceId(resourceId);
        row.setResult("SUCCESS");
        row.setDetail(limit(detail));
        if (mapper.insert(row) != 1) throw invalid("经营操作审计记录保存失败");
    }

    private static String limit(String detail) {
        if (detail == null) return null;
        return detail.length() <= 1000 ? detail : detail.substring(0, 1000);
    }
}
