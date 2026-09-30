package cn.iocoder.txgy.module.erp.service.purchase;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.txgy.framework.common.exception.ErrorCode;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.inspection.ErpArrivalInspectionPageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.inspection.ErpArrivalInspectionSaveReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpArrivalInspectionDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpArrivalInspectionItemDO;
import cn.iocoder.txgy.module.erp.dal.mysql.purchase.ErpArrivalInspectionItemMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.purchase.ErpArrivalInspectionMapper;
import cn.iocoder.txgy.module.erp.dal.redis.no.ErpNoRedisDAO;
import cn.iocoder.txgy.module.erp.enums.ErpAuditStatus;
import cn.iocoder.txgy.module.erp.service.product.ErpProductService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.*;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.*;

@Service
@Validated
public class ErpArrivalInspectionServiceImpl implements ErpArrivalInspectionService {

    @Resource private ErpArrivalInspectionMapper inspectionMapper;
    @Resource private ErpArrivalInspectionItemMapper itemMapper;
    @Resource private ErpNoRedisDAO noRedisDAO;
    @Resource private ErpProductService productService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createArrivalInspection(ErpArrivalInspectionSaveReqVO reqVO) {
        List<ErpArrivalInspectionItemDO> items = validateItems(reqVO.getItems());
        String no = noRedisDAO.generate(ErpNoRedisDAO.ARRIVAL_INSPECTION_NO_PREFIX);
        if (inspectionMapper.selectByNo(no) != null) throw exception(ARRIVAL_INSPECTION_NO_EXISTS);
        ErpArrivalInspectionDO inspection = BeanUtils.toBean(reqVO, ErpArrivalInspectionDO.class)
                .setNo(no).setStatus(ErpAuditStatus.DRAFT.getStatus());
        inspectionMapper.insert(inspection);
        items.forEach(item -> item.setInspectionId(inspection.getId()));
        itemMapper.insertBatch(items);
        return inspection.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateArrivalInspection(ErpArrivalInspectionSaveReqVO reqVO) {
        ErpArrivalInspectionDO inspection = validateEditable(reqVO.getId(), ARRIVAL_INSPECTION_UPDATE_FAIL_STATUS);
        List<ErpArrivalInspectionItemDO> items = validateItems(reqVO.getItems());
        inspectionMapper.updateById(BeanUtils.toBean(reqVO, ErpArrivalInspectionDO.class));
        itemMapper.deleteByInspectionId(inspection.getId());
        items.forEach(item -> item.setId(null).setInspectionId(inspection.getId()));
        itemMapper.insertBatch(items);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteArrivalInspection(List<Long> ids) {
        for (ErpArrivalInspectionDO inspection : inspectionMapper.selectByIds(ids)) {
            validateEditable(inspection.getId(), ARRIVAL_INSPECTION_DELETE_FAIL_STATUS);
            inspectionMapper.deleteById(inspection.getId());
            itemMapper.deleteByInspectionId(inspection.getId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitArrivalInspection(Long id, Long userId) {
        validateEditable(id, ARRIVAL_INSPECTION_SUBMIT_FAIL_STATUS);
        inspectionMapper.updateById(new ErpArrivalInspectionDO().setId(id)
                .setStatus(ErpAuditStatus.PROCESS.getStatus()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveArrivalInspection(Long id, Long userId) {
        validateStatus(id, ErpAuditStatus.PROCESS.getStatus(), ARRIVAL_INSPECTION_APPROVE_FAIL_STATUS);
        inspectionMapper.updateById(new ErpArrivalInspectionDO().setId(id)
                .setStatus(ErpAuditStatus.APPROVE.getStatus()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectArrivalInspection(Long id, Long userId) {
        validateStatus(id, ErpAuditStatus.PROCESS.getStatus(), ARRIVAL_INSPECTION_APPROVE_FAIL_STATUS);
        inspectionMapper.updateById(new ErpArrivalInspectionDO().setId(id)
                .setStatus(ErpAuditStatus.REJECT.getStatus()));
    }

    /**
     * 校验单据处于可编辑（草稿 / 审批驳回 / 已撤销）状态，并返回单据
     */
    private ErpArrivalInspectionDO validateEditable(Long id, ErrorCode code) {
        ErpArrivalInspectionDO inspection = validateArrivalInspection(id);
        if (!ObjectUtil.equal(inspection.getStatus(), ErpAuditStatus.DRAFT.getStatus())
                && !ObjectUtil.equal(inspection.getStatus(), ErpAuditStatus.REJECT.getStatus())
                && !ObjectUtil.equal(inspection.getStatus(), ErpAuditStatus.CANCEL.getStatus())) {
            throw exception(code, inspection.getNo());
        }
        return inspection;
    }

    /**
     * 校验单据处于指定状态，并返回单据
     */
    private ErpArrivalInspectionDO validateStatus(Long id, Integer status, ErrorCode code) {
        ErpArrivalInspectionDO inspection = validateArrivalInspection(id);
        if (!ObjectUtil.equal(inspection.getStatus(), status)) {
            throw exception(code, inspection.getNo());
        }
        return inspection;
    }

    private List<ErpArrivalInspectionItemDO> validateItems(List<ErpArrivalInspectionSaveReqVO.Item> reqItems) {
        if (CollUtil.isEmpty(reqItems)) {
            throw exception(ARRIVAL_INSPECTION_ITEM_COUNT_INVALID);
        }
        for (ErpArrivalInspectionSaveReqVO.Item item : reqItems) {
            BigDecimal deliveryCount = ObjectUtil.defaultIfNull(item.getDeliveryCount(), BigDecimal.ZERO);
            BigDecimal qualifiedCount = ObjectUtil.defaultIfNull(item.getQualifiedCount(), BigDecimal.ZERO);
            BigDecimal unqualifiedCount = ObjectUtil.defaultIfNull(item.getUnqualifiedCount(), BigDecimal.ZERO);
            // 到货数量必须大于零，且合格数 + 不合格数必须等于到货数
            if (deliveryCount.signum() <= 0
                    || qualifiedCount.add(unqualifiedCount).compareTo(deliveryCount) != 0) {
                throw exception(ARRIVAL_INSPECTION_ITEM_COUNT_INVALID);
            }
        }
        List<ErpProductDO> products = productService.validProductList(convertSet(reqItems, ErpArrivalInspectionSaveReqVO.Item::getProductId));
        Map<Long, ErpProductDO> productMap = convertMap(products, ErpProductDO::getId);
        return convertList(reqItems, req -> BeanUtils.toBean(req, ErpArrivalInspectionItemDO.class)
                .setProductUnitId(productMap.get(req.getProductId()).getUnitId()));
    }

    @Override public ErpArrivalInspectionDO getArrivalInspection(Long id) { return inspectionMapper.selectById(id); }
    @Override public ErpArrivalInspectionDO validateArrivalInspection(Long id) {
        ErpArrivalInspectionDO inspection = inspectionMapper.selectById(id);
        if (inspection == null) throw exception(ARRIVAL_INSPECTION_NOT_EXISTS);
        return inspection;
    }
    @Override public PageResult<ErpArrivalInspectionDO> getArrivalInspectionPage(ErpArrivalInspectionPageReqVO reqVO) { return inspectionMapper.selectPage(reqVO); }
    @Override public List<ErpArrivalInspectionItemDO> getItemListByInspectionId(Long inspectionId) { return itemMapper.selectListByInspectionId(inspectionId); }
    @Override public List<ErpArrivalInspectionItemDO> getItemListByInspectionIds(Collection<Long> inspectionIds) {
        return CollUtil.isEmpty(inspectionIds) ? Collections.emptyList() : itemMapper.selectListByInspectionIds(inspectionIds);
    }
}
