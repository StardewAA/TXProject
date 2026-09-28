package cn.iocoder.txgy.module.erp.service.finance;

import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.txgy.framework.common.exception.ErrorCode;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.erp.controller.admin.finance.vo.invoice.ErpInvoicePageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.finance.vo.invoice.ErpInvoiceSaveReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.finance.ErpInvoiceDO;
import cn.iocoder.txgy.module.erp.dal.mysql.finance.ErpInvoiceMapper;
import cn.iocoder.txgy.module.erp.dal.redis.no.ErpNoRedisDAO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.*;

@Service
@Validated
public class ErpInvoiceServiceImpl implements ErpInvoiceService {

    /** 发票类型：增值税专用发票 */
    private static final Integer INVOICE_TYPE_SPECIAL = 1;
    /** 业务类型：采购进项 */
    private static final Integer BIZ_TYPE_PURCHASE = 1;
    /** 发票状态：待认证 */
    private static final Integer STATUS_WAIT_CERTIFY = 0;
    /** 发票状态：已认证 */
    private static final Integer STATUS_CERTIFIED = 1;
    /** 发票状态：已作废 */
    private static final Integer STATUS_VOID = 3;

    @Resource private ErpInvoiceMapper invoiceMapper;
    @Resource private ErpNoRedisDAO noRedisDAO;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createInvoice(ErpInvoiceSaveReqVO reqVO) {
        String no = noRedisDAO.generate(ErpNoRedisDAO.INVOICE_NO_PREFIX);
        if (invoiceMapper.selectByNo(no) != null) throw exception(INVOICE_NO_EXISTS);
        ErpInvoiceDO invoice = BeanUtils.toBean(reqVO, ErpInvoiceDO.class)
                .setNo(no).setStatus(STATUS_WAIT_CERTIFY)
                .setInvoiceType(ObjectUtil.defaultIfNull(reqVO.getInvoiceType(), INVOICE_TYPE_SPECIAL))
                .setBizType(ObjectUtil.defaultIfNull(reqVO.getBizType(), BIZ_TYPE_PURCHASE))
                .setAmount(ObjectUtil.defaultIfNull(reqVO.getAmount(), BigDecimal.ZERO))
                .setTaxAmount(ObjectUtil.defaultIfNull(reqVO.getTaxAmount(), BigDecimal.ZERO));
        invoiceMapper.insert(invoice);
        return invoice.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateInvoice(ErpInvoiceSaveReqVO reqVO) {
        validateEditable(reqVO.getId(), INVOICE_UPDATE_FAIL_STATUS);
        invoiceMapper.updateById(BeanUtils.toBean(reqVO, ErpInvoiceDO.class));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteInvoice(List<Long> ids) {
        for (ErpInvoiceDO invoice : invoiceMapper.selectByIds(ids)) {
            validateEditable(invoice.getId(), INVOICE_DELETE_FAIL_STATUS);
            invoiceMapper.deleteById(invoice.getId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void certifyInvoice(Long id) {
        ErpInvoiceDO invoice = validateInvoice(id);
        // 只有「待认证」的发票才能认证
        if (!ObjectUtil.equal(invoice.getStatus(), STATUS_WAIT_CERTIFY)) {
            throw exception(INVOICE_STATUS_INVALID, invoice.getNo());
        }
        invoiceMapper.updateById(new ErpInvoiceDO().setId(id).setStatus(STATUS_CERTIFIED)
                .setCertifyTime(LocalDateTime.now()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidInvoice(Long id) {
        ErpInvoiceDO invoice = validateInvoice(id);
        // 已作废的发票不能重复作废
        if (ObjectUtil.equal(invoice.getStatus(), STATUS_VOID)) {
            throw exception(INVOICE_STATUS_INVALID, invoice.getNo());
        }
        invoiceMapper.updateById(new ErpInvoiceDO().setId(id).setStatus(STATUS_VOID));
    }

    /** 只有「待认证」「已认证」的发票可以修改 / 删除 */
    private ErpInvoiceDO validateEditable(Long id, ErrorCode code) {
        ErpInvoiceDO invoice = validateInvoice(id);
        if (!ObjectUtil.equal(invoice.getStatus(), STATUS_WAIT_CERTIFY)
                && !ObjectUtil.equal(invoice.getStatus(), STATUS_CERTIFIED)) {
            throw exception(code, invoice.getNo());
        }
        return invoice;
    }

    @Override public ErpInvoiceDO getInvoice(Long id) { return invoiceMapper.selectById(id); }
    @Override public ErpInvoiceDO validateInvoice(Long id) {
        ErpInvoiceDO invoice = invoiceMapper.selectById(id);
        if (invoice == null) throw exception(INVOICE_NOT_EXISTS);
        return invoice;
    }
    @Override public PageResult<ErpInvoiceDO> getInvoicePage(ErpInvoicePageReqVO reqVO) { return invoiceMapper.selectPage(reqVO); }
}
