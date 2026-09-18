package cn.iocoder.txgy.module.product.service.enterprise;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.product.controller.admin.enterprise.vo.ProductEnterprisePageReqVO;
import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.ProductEnterpriseDO;
import cn.iocoder.txgy.module.product.dal.mysql.enterprise.ProductEnterpriseMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import java.util.List;
import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.product.enums.ErrorCodeConstants.*;

@Service
@Validated
public class ProductEnterpriseServiceImpl implements ProductEnterpriseService {
    @Resource
    private ProductEnterpriseMapper mapper;

    public Long create(ProductEnterpriseDO d) {
        if (mapper.selectByCreditCode(d.getCreditCode()) != null)
            throw exception(ENTERPRISE_CREDIT_CODE_EXISTS);
        if (d.getAuditStatus() == null)
            d.setAuditStatus(0);
        mapper.insert(d);
        return d.getId();
    }

    public void update(ProductEnterpriseDO d) {
        if (mapper.selectById(d.getId()) == null)
            throw exception(ENTERPRISE_NOT_EXISTS);
        ProductEnterpriseDO same = mapper.selectByCreditCode(d.getCreditCode());
        if (same != null && !same.getId().equals(d.getId()))
            throw exception(ENTERPRISE_CREDIT_CODE_EXISTS);
        mapper.updateById(d);
    }

    public void delete(Long id) {
        if (mapper.selectById(id) == null)
            throw exception(ENTERPRISE_NOT_EXISTS);
        mapper.deleteById(id);
    }

    public ProductEnterpriseDO get(Long id) {
        return mapper.selectById(id);
    }

    public PageResult<ProductEnterpriseDO> page(ProductEnterprisePageReqVO r) {
        return mapper.selectPage(r);
    }

    public List<ProductEnterpriseDO> getApproved() {
        return mapper.selectApproved();
    }

    public void audit(Long id, Integer status, String remark) {
        ProductEnterpriseDO d = mapper.selectById(id);
        if (d == null)
            throw exception(ENTERPRISE_NOT_EXISTS);
        d.setAuditStatus(status);
        d.setAuditRemark(remark);
        mapper.updateById(d);
    }

    public ProductEnterpriseDO validateApproved(Long id) {
        ProductEnterpriseDO d = mapper.selectById(id);
        if (d == null)
            throw exception(ENTERPRISE_NOT_EXISTS);
        if (!Integer.valueOf(1).equals(d.getAuditStatus()))
            throw exception(ENTERPRISE_NOT_APPROVED);
        return d;
    }
}
