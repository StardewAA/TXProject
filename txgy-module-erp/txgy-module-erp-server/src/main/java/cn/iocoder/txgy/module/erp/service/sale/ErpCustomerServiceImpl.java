package cn.iocoder.txgy.module.erp.service.sale;

import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.erp.controller.admin.sale.vo.customer.ErpCustomerPageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.sale.vo.customer.ErpCustomerSaveReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.sale.ErpCustomerDO;
import cn.iocoder.txgy.module.erp.dal.mysql.sale.ErpCustomerMapper;
import cn.iocoder.txgy.module.erp.dal.redis.no.ErpNoRedisDAO;
import cn.iocoder.txgy.module.erp.service.integration.ErpMesMasterDataSyncService;
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
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.CUSTOMER_PARTNER_CODE_DUPLICATE;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.CUSTOMER_PARTNER_CODE_IMMUTABLE;

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
    @Resource
    private ErpMesMasterDataSyncService mesMasterDataSyncService;

    @Resource
    private ErpNoRedisDAO noRedisDAO;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCustomer(ErpCustomerSaveReqVO createReqVO) {
        ErpCustomerDO customer = BeanUtils.toBean(createReqVO, ErpCustomerDO.class);
        // 统一编码由服务端生成，保证跨模块同源（MES/WMS 均消费该编码）
        customer.setPartnerCode(generatePartnerCode());
        customer.setSyncVersion(1L);
        // 插入
        customerMapper.insert(customer);
        wmsMasterDataSyncService.syncCustomerAfterCommit(customer);
        mesMasterDataSyncService.syncCustomerAfterCommit(customer);
        // 返回
        return customer.getId();
    }

    /**
     * 生成客户统一编码，格式：CLI + yyyyMMdd + 6 位自增，例如 CLI20260917000001
     *
     * 与 MES 编码规则 MD_CLIENT_CODE 前缀保持一致；循环重试以规避 Redis 计数与落库数据不一致导致的重复
     */
    private String generatePartnerCode() {
        for (int i = 0; i < 10; i++) {
            String partnerCode = noRedisDAO.generate(ErpNoRedisDAO.CUSTOMER_PARTNER_CODE_PREFIX);
            if (customerMapper.selectByPartnerCode(partnerCode) == null) {
                return partnerCode;
            }
        }
        throw exception(CUSTOMER_PARTNER_CODE_DUPLICATE, "自动生成失败，请重试");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCustomer(ErpCustomerSaveReqVO updateReqVO) {
        // 校验存在
        ErpCustomerDO oldCustomer = validateCustomerExists(updateReqVO.getId());
        // 统一编码由服务端生成，不允许通过接口修改，始终沿用原编码
        if (updateReqVO.getPartnerCode() != null
                && !Objects.equals(oldCustomer.getPartnerCode(), updateReqVO.getPartnerCode())) {
            throw exception(CUSTOMER_PARTNER_CODE_IMMUTABLE);
        }
        // 更新
        ErpCustomerDO updateObj = BeanUtils.toBean(updateReqVO, ErpCustomerDO.class);
        updateObj.setPartnerCode(oldCustomer.getPartnerCode());
        updateObj.setSyncVersion(oldCustomer.getSyncVersion() == null ? 1L : oldCustomer.getSyncVersion() + 1);
        customerMapper.updateById(updateObj);
        wmsMasterDataSyncService.syncCustomerAfterCommit(updateObj);
        mesMasterDataSyncService.syncCustomerAfterCommit(updateObj);
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
        mesMasterDataSyncService.syncCustomerAfterCommit(customer);
    }

    private ErpCustomerDO validateCustomerExists(Long id) {
        ErpCustomerDO customer = customerMapper.selectById(id);
        if (customer == null) {
            throw exception(CUSTOMER_NOT_EXISTS);
        }
        return customer;
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
