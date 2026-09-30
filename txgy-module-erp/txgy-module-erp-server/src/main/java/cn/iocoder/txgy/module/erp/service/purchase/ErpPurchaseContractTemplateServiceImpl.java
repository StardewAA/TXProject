package cn.iocoder.txgy.module.erp.service.purchase;

import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.contract.ErpPurchaseContractTemplateSaveReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseContractTemplateDO;
import cn.iocoder.txgy.module.erp.dal.mysql.purchase.ErpPurchaseContractTemplateMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ErpPurchaseContractTemplateServiceImpl implements ErpPurchaseContractTemplateService {
    @Resource private ErpPurchaseContractTemplateMapper mapper;

    @Override @Transactional(rollbackFor = Exception.class)
    public Long create(ErpPurchaseContractTemplateSaveReqVO reqVO) {
        ErpPurchaseContractTemplateDO row = BeanUtils.toBean(reqVO, ErpPurchaseContractTemplateDO.class);
        normalize(row);
        clearOtherDefault(row);
        mapper.insert(row);
        return row.getId();
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void update(ErpPurchaseContractTemplateSaveReqVO reqVO) {
        ErpPurchaseContractTemplateDO row = BeanUtils.toBean(reqVO, ErpPurchaseContractTemplateDO.class);
        normalize(row);
        clearOtherDefault(row);
        mapper.updateById(row);
    }

    @Override public void delete(Long id) { mapper.deleteById(id); }
    @Override public ErpPurchaseContractTemplateDO get(Long id) { return mapper.selectById(id); }
    @Override public List<ErpPurchaseContractTemplateDO> getEnabledList() { return mapper.selectAllEnabled(); }

    private void normalize(ErpPurchaseContractTemplateDO row) {
        if (row.getDefaultFlag() == null) row.setDefaultFlag(false);
        if (row.getStatus() == null) row.setStatus(0);
    }

    private void clearOtherDefault(ErpPurchaseContractTemplateDO row) {
        if (!Boolean.TRUE.equals(row.getDefaultFlag())) return;
        for (ErpPurchaseContractTemplateDO existing : mapper.selectAllEnabled()) {
            if (row.getId() == null || !row.getId().equals(existing.getId())) {
                mapper.updateById(new ErpPurchaseContractTemplateDO().setId(existing.getId()).setDefaultFlag(false));
            }
        }
    }
}
