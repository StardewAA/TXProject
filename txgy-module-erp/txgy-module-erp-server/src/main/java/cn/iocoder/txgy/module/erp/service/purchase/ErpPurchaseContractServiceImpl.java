package cn.iocoder.txgy.module.erp.service.purchase;

import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.txgy.framework.common.exception.ErrorCode;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.contract.ErpPurchaseContractPageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.contract.ErpPurchaseContractSaveReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseContractDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseOrderDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpSupplierDO;
import cn.iocoder.txgy.module.erp.dal.mysql.purchase.ErpPurchaseContractMapper;
import cn.iocoder.txgy.module.erp.dal.redis.no.ErpNoRedisDAO;
import cn.iocoder.txgy.module.erp.enums.ErpAuditStatus;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.*;

@Service
@Validated
public class ErpPurchaseContractServiceImpl implements ErpPurchaseContractService {

    @Resource private ErpPurchaseContractMapper contractMapper;
    @Resource private ErpNoRedisDAO noRedisDAO;
    @Resource private ErpPurchaseOrderService purchaseOrderService;
    @Resource private ErpSupplierService supplierService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPurchaseContract(ErpPurchaseContractSaveReqVO reqVO) {
        prepareAndValidate(reqVO);
        String no = noRedisDAO.generate(ErpNoRedisDAO.PURCHASE_CONTRACT_NO_PREFIX);
        if (contractMapper.selectByNo(no) != null) throw exception(PURCHASE_CONTRACT_NO_EXISTS);
        ErpPurchaseContractDO contract = BeanUtils.toBean(reqVO, ErpPurchaseContractDO.class)
                .setNo(no).setStatus(ErpAuditStatus.DRAFT.getStatus())
                .setTotalAmount(ObjectUtil.defaultIfNull(reqVO.getTotalAmount(), BigDecimal.ZERO));
        contractMapper.insert(contract);
        return contract.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePurchaseContract(ErpPurchaseContractSaveReqVO reqVO) {
        validateEditable(reqVO.getId(), PURCHASE_CONTRACT_UPDATE_FAIL_STATUS);
        prepareAndValidate(reqVO);
        contractMapper.updateById(BeanUtils.toBean(reqVO, ErpPurchaseContractDO.class));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePurchaseContract(List<Long> ids) {
        for (ErpPurchaseContractDO contract : contractMapper.selectByIds(ids)) {
            validateEditable(contract.getId(), PURCHASE_CONTRACT_DELETE_FAIL_STATUS);
            contractMapper.deleteById(contract.getId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitPurchaseContract(Long id) {
        validateEditable(id, PURCHASE_CONTRACT_SUBMIT_FAIL_STATUS);
        contractMapper.updateById(new ErpPurchaseContractDO().setId(id).setStatus(ErpAuditStatus.PROCESS.getStatus()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approvePurchaseContract(Long id) {
        ErpPurchaseContractDO contract = validateProcess(id);
        contractMapper.updateById(new ErpPurchaseContractDO().setId(contract.getId())
                .setStatus(ErpAuditStatus.APPROVE.getStatus()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectPurchaseContract(Long id) {
        ErpPurchaseContractDO contract = validateProcess(id);
        contractMapper.updateById(new ErpPurchaseContractDO().setId(contract.getId())
                .setStatus(ErpAuditStatus.REJECT.getStatus()));
    }

    private ErpPurchaseContractDO validateProcess(Long id) {
        ErpPurchaseContractDO contract = validatePurchaseContract(id);
        if (!ObjectUtil.equal(contract.getStatus(), ErpAuditStatus.PROCESS.getStatus())) {
            throw exception(PURCHASE_CONTRACT_APPROVE_FAIL_STATUS, contract.getNo());
        }
        return contract;
    }

    private ErpPurchaseContractDO validateEditable(Long id, ErrorCode code) {
        ErpPurchaseContractDO contract = validatePurchaseContract(id);
        if (!ObjectUtil.equal(contract.getStatus(), ErpAuditStatus.DRAFT.getStatus())
                && !ObjectUtil.equal(contract.getStatus(), ErpAuditStatus.REJECT.getStatus())
                && !ObjectUtil.equal(contract.getStatus(), ErpAuditStatus.CANCEL.getStatus())) {
            throw exception(code, contract.getNo());
        }
        return contract;
    }

    /** 到期日期不能早于生效日期 */
    private void validateDate(ErpPurchaseContractSaveReqVO reqVO) {
        if (reqVO.getEffectiveDate() != null && reqVO.getExpireDate() != null
                && reqVO.getExpireDate().isBefore(reqVO.getEffectiveDate())) {
            throw exception(PURCHASE_CONTRACT_DATE_INVALID);
        }
    }

    /**
     * 在合同草稿落库前完成关联订单校验、供应商签约资料默认值和付款比例校验。
     * 所有自动带入值都会随合同保存为快照，基础档案后续变化不会改写历史合同。
     */
    private void prepareAndValidate(ErpPurchaseContractSaveReqVO reqVO) {
        validateDate(reqVO);
        if (reqVO.getPurchaseOrderId() != null) {
            ErpPurchaseOrderDO order = purchaseOrderService.validatePurchaseOrder(reqVO.getPurchaseOrderId());
            if (reqVO.getSupplierId() != null && !ObjectUtil.equal(reqVO.getSupplierId(), order.getSupplierId())) {
                throw exception(PURCHASE_CONTRACT_ORDER_SUPPLIER_MISMATCH);
            }
            reqVO.setSupplierId(order.getSupplierId());
            reqVO.setPurchaseOrderNo(order.getNo());
            if (reqVO.getTotalAmount() == null) reqVO.setTotalAmount(order.getTotalPrice());
            if (reqVO.getAdvancePaymentRatio() == null && order.getDepositPrice() != null
                    && order.getTotalPrice() != null && order.getTotalPrice().signum() > 0) {
                reqVO.setAdvancePaymentRatio(order.getDepositPrice()
                        .multiply(new BigDecimal("100")).divide(order.getTotalPrice(), 2, java.math.RoundingMode.HALF_UP));
            }
        }
        ErpSupplierDO supplier = supplierService.validateSupplier(reqVO.getSupplierId());
        if (isBlank(reqVO.getSupplierNameSnapshot())) reqVO.setSupplierNameSnapshot(supplier.getName());
        if (isBlank(reqVO.getSellerBankName())) reqVO.setSellerBankName(supplier.getBankName());
        if (isBlank(reqVO.getSellerBankAccount())) reqVO.setSellerBankAccount(supplier.getBankAccount());
        if (isBlank(reqVO.getSellerPhone())) {
            reqVO.setSellerPhone(!isBlank(supplier.getTelephone()) ? supplier.getTelephone() : supplier.getMobile());
        }
        if (isBlank(reqVO.getSellerFax())) reqVO.setSellerFax(supplier.getFax());
        validatePaymentRatios(reqVO);
    }

    private void validatePaymentRatios(ErpPurchaseContractSaveReqVO reqVO) {
        BigDecimal advance = reqVO.getAdvancePaymentRatio();
        BigDecimal acceptance = reqVO.getAcceptancePaymentRatio();
        BigDecimal warranty = reqVO.getWarrantyRetentionRatio();
        if (advance == null && acceptance == null && warranty == null) return;
        BigDecimal total = ObjectUtil.defaultIfNull(advance, BigDecimal.ZERO)
                .add(ObjectUtil.defaultIfNull(acceptance, BigDecimal.ZERO))
                .add(ObjectUtil.defaultIfNull(warranty, BigDecimal.ZERO));
        if (total.compareTo(new BigDecimal("100")) != 0) {
            throw exception(PURCHASE_CONTRACT_PAYMENT_RATIO_INVALID);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Override public ErpPurchaseContractDO getPurchaseContract(Long id) { return contractMapper.selectById(id); }
    @Override public ErpPurchaseContractDO validatePurchaseContract(Long id) {
        ErpPurchaseContractDO contract = contractMapper.selectById(id);
        if (contract == null) throw exception(PURCHASE_CONTRACT_NOT_EXISTS);
        return contract;
    }
    @Override public PageResult<ErpPurchaseContractDO> getPurchaseContractPage(ErpPurchaseContractPageReqVO reqVO) { return contractMapper.selectPage(reqVO); }
}
