package cn.iocoder.txgy.module.erp.service.stock;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.erp.controller.admin.stock.vo.record.ErpStockRecordPageReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.stock.ErpStockRecordDO;
import cn.iocoder.txgy.module.erp.dal.mysql.stock.ErpStockRecordMapper;
import cn.iocoder.txgy.module.erp.enums.stock.ErpStockSourceSystemEnum;
import cn.iocoder.txgy.module.erp.service.stock.bo.ErpStockRecordCreateReqBO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/**
 * ERP 产品库存明细 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class ErpStockRecordServiceImpl implements ErpStockRecordService {

    @Resource
    private ErpStockRecordMapper stockRecordMapper;

    @Resource
    private ErpStockService stockService;

    @Override
    public ErpStockRecordDO getStockRecord(Long id) {
        return stockRecordMapper.selectById(id);
    }

    @Override
    public PageResult<ErpStockRecordDO> getStockRecordPage(ErpStockRecordPageReqVO pageReqVO) {
        return stockRecordMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createStockRecord(ErpStockRecordCreateReqBO createReqBO) {
        // 1. 更新库存
        BigDecimal totalCount = stockService.updateStockCountIncrement(
                createReqBO.getProductId(), createReqBO.getWarehouseId(), createReqBO.getCount());
        // 2. 补全归属信息
        //    来源系统：ERP 内部写入一律为 ERP；WMS 同步等外部来源由调用方显式指定
        if (createReqBO.getSourceSystem() == null || createReqBO.getSourceSystem().isBlank()) {
            createReqBO.setSourceSystem(ErpStockSourceSystemEnum.ERP.getSystem());
        }
        //    责任人：销售出库/退货由调用方传销售员；其余业务取当前登录用户（操作人）
        if (createReqBO.getSourceUserId() == null) {
            createReqBO.setSourceUserId(SecurityFrameworkUtils.getLoginUserId());
        }
        // 3. 创建库存明细
        ErpStockRecordDO stockRecord = BeanUtils.toBean(createReqBO, ErpStockRecordDO.class)
                .setTotalCount(totalCount);
        stockRecordMapper.insert(stockRecord);
    }

}