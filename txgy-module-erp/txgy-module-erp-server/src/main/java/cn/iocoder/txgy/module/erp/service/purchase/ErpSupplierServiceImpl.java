package cn.iocoder.txgy.module.erp.service.purchase;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.supplier.ErpSupplierPageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.supplier.ErpSupplierSaveReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpSupplierDO;
import cn.iocoder.txgy.module.erp.dal.mysql.purchase.ErpSupplierMapper;
import cn.iocoder.txgy.module.erp.service.integration.ErpWmsMasterDataSyncService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.*;

/**
 * ERP 供应商 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class ErpSupplierServiceImpl implements ErpSupplierService {

    @Resource
    private ErpSupplierMapper supplierMapper;
    @Resource
    private ErpWmsMasterDataSyncService wmsMasterDataSyncService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSupplier(ErpSupplierSaveReqVO createReqVO) {
        validatePartnerCodeUnique(null, createReqVO.getPartnerCode());
        validateUserIdUnique(null, createReqVO.getUserId());
        ErpSupplierDO supplier = BeanUtils.toBean(createReqVO, ErpSupplierDO.class);
        supplier.setSyncVersion(1L);
        supplierMapper.insert(supplier);
        wmsMasterDataSyncService.syncSupplierAfterCommit(supplier);
        return supplier.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSupplier(ErpSupplierSaveReqVO updateReqVO) {
        // 校验存在
        ErpSupplierDO oldSupplier = validateSupplierExists(updateReqVO.getId());
        if (!Objects.equals(oldSupplier.getPartnerCode(), updateReqVO.getPartnerCode())) {
            throw exception(SUPPLIER_PARTNER_CODE_IMMUTABLE);
        }
        validatePartnerCodeUnique(updateReqVO.getId(), updateReqVO.getPartnerCode());
        validateUserIdUnique(updateReqVO.getId(), updateReqVO.getUserId());
        // 更新
        ErpSupplierDO updateObj = BeanUtils.toBean(updateReqVO, ErpSupplierDO.class);
        updateObj.setSyncVersion(oldSupplier.getSyncVersion() == null ? 1L : oldSupplier.getSyncVersion() + 1);
        supplierMapper.updateById(updateObj);
        wmsMasterDataSyncService.syncSupplierAfterCommit(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSupplier(Long id) {
        // 校验存在
        ErpSupplierDO supplier = validateSupplierExists(id);
        // 删除
        supplierMapper.deleteById(id);
        supplier.setStatus(CommonStatusEnum.DISABLE.getStatus());
        supplier.setSyncVersion(supplier.getSyncVersion() == null ? 1L : supplier.getSyncVersion() + 1);
        wmsMasterDataSyncService.syncSupplierAfterCommit(supplier);
    }

    private ErpSupplierDO validateSupplierExists(Long id) {
        ErpSupplierDO supplier = supplierMapper.selectById(id);
        if (supplier == null) {
            throw exception(SUPPLIER_NOT_EXISTS);
        }
        return supplier;
    }

    private void validatePartnerCodeUnique(Long id, String partnerCode) {
        ErpSupplierDO supplier = supplierMapper.selectByPartnerCode(partnerCode);
        if (supplier != null && !Objects.equals(supplier.getId(), id)) {
            throw exception(SUPPLIER_PARTNER_CODE_DUPLICATE, partnerCode);
        }
    }

    /**
     * 一个登录账号只能绑定一家供应商，否则「按登录账号反查供应商」结果不确定，
     * 供应商数据隔离会失效。
     */
    private void validateUserIdUnique(Long id, Long userId) {
        if (userId == null) {
            return;
        }
        ErpSupplierDO bound = supplierMapper.selectByUserId(userId);
        if (bound != null && !Objects.equals(bound.getId(), id)) {
            throw exception(SUPPLIER_USER_DUPLICATE, bound.getName());
        }
    }

    @Override
    public ErpSupplierDO getSupplier(Long id) {
        return supplierMapper.selectById(id);
    }

    @Override
    public ErpSupplierDO getSupplierByUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        return supplierMapper.selectByUserId(userId);
    }

    @Override
    public ErpSupplierDO validateSupplier(Long id) {
        ErpSupplierDO supplier = supplierMapper.selectById(id);
        if (supplier == null) {
            throw exception(SUPPLIER_NOT_EXISTS);
        }
        if (CommonStatusEnum.isDisable(supplier.getStatus())) {
            throw exception(SUPPLIER_NOT_ENABLE, supplier.getName());
        }
        return supplier;
    }

    @Override
    public List<ErpSupplierDO> getSupplierList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        return supplierMapper.selectByIds(ids);
    }

    @Override
    public PageResult<ErpSupplierDO> getSupplierPage(ErpSupplierPageReqVO pageReqVO) {
        return supplierMapper.selectPage(pageReqVO);
    }

    @Override
    public List<ErpSupplierDO> getSupplierListByStatus(Integer status) {
        return supplierMapper.selectListByStatus(status);
    }

}
