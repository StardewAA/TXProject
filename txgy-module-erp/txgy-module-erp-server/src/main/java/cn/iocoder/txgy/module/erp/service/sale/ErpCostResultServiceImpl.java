package cn.iocoder.txgy.module.erp.service.sale;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.number.MoneyUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.txgy.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.txgy.module.erp.controller.admin.sale.vo.cost.*;
import cn.iocoder.txgy.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.sale.*;
import cn.iocoder.txgy.module.erp.dal.mysql.sale.*;
import cn.iocoder.txgy.module.erp.dal.redis.no.ErpNoRedisDAO;
import cn.iocoder.txgy.module.erp.enums.ErpAuditStatus;
import cn.iocoder.txgy.module.erp.service.approval.ErpApprovalNotificationService;
import cn.iocoder.txgy.module.erp.service.product.ErpProductService;
import cn.iocoder.txgy.module.erp.util.ErpAuditStatusUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.*;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.*;
@Service
public class ErpCostResultServiceImpl implements ErpCostResultService {
    public static final String BPM_PROCESS_DEFINITION_KEY = "erp-cost-result-approval";
    @Resource private ErpCostResultMapper resultMapper; @Resource private ErpCostResultItemMapper itemMapper;
    @Resource private ErpProductService productService; @Resource private ErpNoRedisDAO noRedisDAO;
    @Resource private BpmProcessInstanceApi bpmApi; @Resource private ErpApprovalNotificationService notificationService;
    @Override @Transactional(rollbackFor = Exception.class)
    public Long create(ErpCostResultSaveReqVO reqVO) {
        validateVersion(reqVO, null); List<ErpCostResultItemDO> items = buildItems(reqVO.getItems());
        String no = noRedisDAO.generate(ErpNoRedisDAO.COST_RESULT_NO_PREFIX);
        if (resultMapper.selectByNo(no) != null) throw exception(COST_RESULT_NO_EXISTS);
        ErpCostResultDO result = BeanUtils.toBean(reqVO, ErpCostResultDO.class).setNo(no)
                .setStatus(ErpAuditStatus.DRAFT.getStatus()).setBusinessStatus(0).setReceivedAt(LocalDateTime.now())
                .setCurrency(ObjectUtil.defaultIfNull(reqVO.getCurrency(), "CNY")); fillTotals(result, items);
        resultMapper.insert(result); items.forEach(i -> i.setCostResultId(result.getId())); itemMapper.insertBatch(items); return result.getId();
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public void update(ErpCostResultSaveReqVO reqVO) { ErpCostResultDO result = editable(reqVO.getId()); validateVersion(reqVO, result.getId());
        List<ErpCostResultItemDO> items = buildItems(reqVO.getItems()); ErpCostResultDO update = BeanUtils.toBean(reqVO, ErpCostResultDO.class); fillTotals(update, items); resultMapper.updateById(update);
        itemMapper.deleteByCostResultId(result.getId()); items.forEach(i -> i.setId(null).setCostResultId(result.getId())); itemMapper.insertBatch(items); }
    @Override @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> ids) { for (Long id : ids) { editable(id); resultMapper.deleteById(id); itemMapper.deleteByCostResultId(id); } }
    @Override @Transactional(rollbackFor = Exception.class)
    public void submit(Long id, Long userId) { ErpCostResultDO result = editable(id);
        if (itemMapper.selectListByCostResultId(id).stream().anyMatch(i -> i.getMappingStatus() == null || i.getMappingStatus() == 0)) throw exception(COST_RESULT_MAPPING_INCOMPLETE);
        String pid = bpmApi.createProcessInstance(userId, new BpmProcessInstanceCreateReqDTO().setProcessDefinitionKey(BPM_PROCESS_DEFINITION_KEY)
                .setBusinessKey(String.valueOf(id)).setVariables(Map.of("costNo", result.getNo(), "totalPrice", result.getTotalPrice()))).getCheckedData();
        resultMapper.updateById(new ErpCostResultDO().setId(id).setStatus(ErpAuditStatus.PROCESS.getStatus()).setProcessInstanceId(pid));
        notificationService.sendBusinessSubmittedAfterCommit(userId, "造价结果", result.getNo(), id, "/erp/sale/cost"); }
    @Override public void updateAuditStatus(Long id, Integer bpmStatus) { ErpCostResultDO result = exists(id); if (!ObjectUtil.equal(result.getStatus(), ErpAuditStatus.PROCESS.getStatus())) return;
        Integer status = ErpAuditStatusUtils.convertBpmResultToAuditStatus(bpmStatus); resultMapper.updateById(new ErpCostResultDO().setId(id).setStatus(status).setBusinessStatus(ObjectUtil.equal(status, ErpAuditStatus.APPROVE.getStatus()) ? 20 : result.getBusinessStatus())); }
    @Override public void convert(ErpCostResultConvertReqVO reqVO) { ErpCostResultDO result = exists(reqVO.getId()); if (!ObjectUtil.equal(result.getStatus(), ErpAuditStatus.APPROVE.getStatus())) throw exception(COST_RESULT_CONVERT_FAIL_STATUS, result.getNo());
        resultMapper.updateById(new ErpCostResultDO().setId(result.getId()).setConvertedType(reqVO.getConvertedType()).setBusinessStatus(30)); }
    private void validateVersion(ErpCostResultSaveReqVO reqVO, Long id) { ErpCostResultDO old = resultMapper.selectByProjectAndVersion(reqVO.getProjectCode(), reqVO.getCostVersion()); if (old != null && !old.getId().equals(id)) throw exception(COST_RESULT_VERSION_DUPLICATE); }
    private List<ErpCostResultItemDO> buildItems(List<ErpCostResultSaveReqVO.Item> source) { if (CollUtil.isEmpty(source) || source.stream().anyMatch(i -> i.getCount().signum() < 0 || i.getUnitPrice().signum() < 0)) throw exception(COST_RESULT_MAPPING_INCOMPLETE);
        Set<Long> productIds = source.stream().map(ErpCostResultSaveReqVO.Item::getProductId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet()); Map<Long, ErpProductDO> products = convertMap(productService.validProductList(productIds), ErpProductDO::getId);
        return convertList(source, i -> { ErpCostResultItemDO item = BeanUtils.toBean(i, ErpCostResultItemDO.class); if (i.getProductId() != null) item.setProductUnitId(products.get(i.getProductId()).getUnitId()).setMappingStatus(20); else item.setMappingStatus(ObjectUtil.defaultIfNull(i.getMappingStatus(), 0)); return item.setTotalPrice(MoneyUtils.priceMultiply(i.getUnitPrice(), i.getCount())); }); }
    private void fillTotals(ErpCostResultDO result, List<ErpCostResultItemDO> items) { result.setItemCount(items.size()).setMappedCount((int) items.stream().filter(i -> i.getMappingStatus() != null && i.getMappingStatus() != 0).count()).setTotalPrice(getSumValue(items, ErpCostResultItemDO::getTotalPrice, BigDecimal::add, BigDecimal.ZERO)); }
    private ErpCostResultDO exists(Long id) { ErpCostResultDO v = resultMapper.selectById(id); if (v == null) throw exception(COST_RESULT_NOT_EXISTS); return v; }
    private ErpCostResultDO editable(Long id) { ErpCostResultDO v = exists(id); if (!ObjectUtil.equal(v.getStatus(), ErpAuditStatus.DRAFT.getStatus()) && !ObjectUtil.equal(v.getStatus(), ErpAuditStatus.REJECT.getStatus())) throw exception(COST_RESULT_UPDATE_FAIL_STATUS, v.getNo()); return v; }
    @Override public ErpCostResultDO get(Long id) { return resultMapper.selectById(id); } @Override public PageResult<ErpCostResultDO> getPage(ErpCostResultPageReqVO reqVO) { return resultMapper.selectPage(reqVO); }
    @Override public List<ErpCostResultItemDO> getItems(Collection<Long> ids) { return CollUtil.isEmpty(ids) ? Collections.emptyList() : itemMapper.selectListByCostResultIds(ids); }
}
