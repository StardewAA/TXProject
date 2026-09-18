package cn.iocoder.txgy.module.erp.service.sale;

import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.erp.controller.admin.sale.vo.customer.ErpCustomerPageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.sale.vo.customer.ErpCustomerSaveReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.sale.ErpCustomerDO;
import cn.iocoder.txgy.module.erp.dal.mysql.sale.ErpCustomerMapper;
import cn.iocoder.txgy.module.erp.service.integration.ErpWmsMasterDataSyncService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.CUSTOMER_NOT_ENABLE;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.CUSTOMER_NOT_EXISTS;

/**
 * ERP 客户 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class ErpCustomerServiceImpl implements ErpCustomerService {

    @Resource
    private ErpCustomerMapper customerMapper;
    @Resource
    private ErpWmsMasterDataSyncService wmsMasterDataSyncService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCustomer(ErpCustomerSaveReqVO createReqVO) {
        validatePartnerCodeUnique(null, createReqVO.getPartnerCode());
        // 插入
        ErpCustomerDO customer = BeanUtils.toBean(createReqVO, ErpCustomerDO.class);
        customer.setSyncVersion(1L);
        customerMapper.insert(customer);
        wmsMasterDataSyncService.syncCustomerAfterCommit(customer);
        // 返回
        return customer.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCustomer(ErpCustomerSaveReqVO updateReqVO) {
        // 校验存在
        ErpCustomerDO oldCustomer = validateCustomerExists(updateReqVO.getId());
        if (!Objects.equals(oldCustomer.getPartnerCode(), updateReqVO.getPartnerCode())) {
            throw exception(cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.CUSTOMER_PARTNER_CODE_IMMUTABLE);
        }
        validatePartnerCodeUnique(updateReqVO.getId(), updateReqVO.getPartnerCode());
        // 更新
        ErpCustomerDO updateObj = BeanUtils.toBean(updateReqVO, ErpCustomerDO.class);
        updateObj.setSyncVersion(oldCustomer.getSyncVersion() == null ? 1L : oldCustomer.getSyncVersion() + 1);
        customerMapper.updateById(updateObj);
        wmsMasterDataSyncService.syncCustomerAfterCommit(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCustomer(Long id) {
        // 校验存在
        ErpCustomerDO customer = validateCustomerExists(id);
        // 删除
        customerMapper.deleteById(id);
        customer.setStatus(CommonStatusEnum.DISABLE.getStatus());
        customer.setSyncVersion(customer.getSyncVersion() == null ? 1L : customer.getSyncVersion() + 1);
        wmsMasterDataSyncService.syncCustomerAfterCommit(customer);
    }

    private ErpCustomerDO validateCustomerExists(Long id) {
        ErpCustomerDO customer = customerMapper.selectById(id);
        if (customer == null) {
            throw exception(CUSTOMER_NOT_EXISTS);
        }
        return customer;
    }

    private void validatePartnerCodeUnique(Long id, String partnerCode) {
        ErpCustomerDO customer = customerMapper.selectByPartnerCode(partnerCode);
        if (customer != null && !Objects.equals(customer.getId(), id)) {
            throw exception(cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.CUSTOMER_PARTNER_CODE_DUPLICATE,
                    partnerCode);
        }
    }

    @Override
    public ErpCustomerDO getCustomer(Long id) {
        return customerMapper.selectById(id);
    }

    @Override
    public ErpCustomerDO validateCustomer(Long id) {
        ErpCustomerDO customer = customerMapper.selectById(id);
        if (customer == null) {
            throw exception(CUSTOMER_NOT_EXISTS);
        }
        if (CommonStatusEnum.isDisable(customer.getStatus())) {
            throw exception(CUSTOMER_NOT_ENABLE, customer.getName());
        }
        return customer;
    }

    @Override
    public List<ErpCustomerDO> getCustomerList(Collection<Long> ids) {
        return customerMapper.selectByIds(ids);
    }

    @Override
    public PageResult<ErpCustomerDO> getCustomerPage(ErpCustomerPageReqVO pageReqVO) {
        return customerMapper.selectPage(pageReqVO);
    }

    @Override
    public List<ErpCustomerDO> getCustomerListByStatus(Integer status) {
        return customerMapper.selectListByStatus(status);
    }

}
