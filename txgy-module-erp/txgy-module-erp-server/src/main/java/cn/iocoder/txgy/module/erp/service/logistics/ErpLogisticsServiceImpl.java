package cn.iocoder.txgy.module.erp.service.logistics;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONObject;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.erp.controller.admin.logistics.vo.ErpLogisticsAttachmentVO;
import cn.iocoder.txgy.module.erp.controller.admin.logistics.vo.ErpLogisticsDetailRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.logistics.vo.ErpLogisticsEventPageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.logistics.vo.ErpLogisticsEventPageRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.logistics.vo.ErpLogisticsEventRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.logistics.vo.ErpLogisticsExceptionHandleReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.logistics.vo.ErpLogisticsNodeReceiveReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.logistics.vo.ErpLogisticsPageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.logistics.vo.ErpLogisticsPageRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.logistics.vo.ErpLogisticsReceiptReceiveReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.logistics.vo.ErpLogisticsTaskReceiveReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.logistics.ErpDeliveryReceiptDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.logistics.ErpLogisticsEventLogDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.logistics.ErpLogisticsExceptionDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.logistics.ErpLogisticsFulfillmentEventDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.logistics.ErpLogisticsNodeDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.logistics.ErpLogisticsRelationDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.logistics.ErpLogisticsRelationItemDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseReturnDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.sale.ErpSaleOrderDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.sale.ErpSaleOutDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.sale.ErpSaleReturnDO;
import cn.iocoder.txgy.module.erp.dal.mysql.logistics.ErpDeliveryReceiptMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.logistics.ErpLogisticsEventLogMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.logistics.ErpLogisticsExceptionMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.logistics.ErpLogisticsFulfillmentEventMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.logistics.ErpLogisticsNodeMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.logistics.ErpLogisticsRelationItemMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.logistics.ErpLogisticsRelationMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.purchase.ErpPurchaseReturnMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.sale.ErpSaleOrderMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.sale.ErpSaleOutMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.sale.ErpSaleReturnMapper;
import cn.iocoder.txgy.module.erp.enums.ErpAuditStatus;
import cn.iocoder.txgy.module.infra.api.file.FileApi;
import cn.iocoder.txgy.module.infra.api.file.dto.FileMetadataRespDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.LOGISTICS_EXCEPTION_NOT_EXISTS;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.LOGISTICS_EVENT_NOT_PENDING_COMPENSATION;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.LOGISTICS_ATTACHMENT_INVALID;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.LOGISTICS_NODE_INVALID;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.LOGISTICS_RECEIPT_INVALID;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.LOGISTICS_RELATION_NOT_EXISTS;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.LOGISTICS_REQUEST_CONFLICT;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.LOGISTICS_SOURCE_ORDER_NOT_EXISTS;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.LOGISTICS_SOURCE_ORDER_NOT_APPROVED;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.LOGISTICS_SOURCE_ORDER_TYPE_INVALID;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.LOGISTICS_TASK_CONFLICT;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.LOGISTICS_TENANT_MISMATCH;

@Service
@Validated
@Slf4j
public class ErpLogisticsServiceImpl implements ErpLogisticsService {

    private static final String PROCESS_SUCCESS = "SUCCESS";
    private static final String PROCESS_REJECTED = "REJECTED";
    private static final String PROCESS_PENDING_COMPENSATION = "PENDING_COMPENSATION";

    private static final String STATUS_PENDING_DISPATCH = "PENDING_DISPATCH";
    private static final String STATUS_PENDING_LOADING = "PENDING_LOADING";
    private static final String STATUS_IN_TRANSIT = "IN_TRANSIT";
    private static final String STATUS_ARRIVED = "ARRIVED";
    private static final String STATUS_SIGNED = "SIGNED";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_EXCEPTION_HANDLING = "EXCEPTION_HANDLING";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String STATUS_CLOSED = "CLOSED";

    private static final Set<String> SOURCE_ORDER_TYPES = Set.of(
            "SALE_ORDER", "SALE_OUT", "PURCHASE_RETURN", "SALE_RETURN");
    private static final Set<String> EXCEPTION_NODE_CODES = Set.of(
            "VEHICLE_BREAKDOWN", "ROUTE_DEVIATION", "TRANSPORT_TIMEOUT", "CARGO_DAMAGED",
            "QUANTITY_EXCEPTION", "RECEIPT_REJECTED", "LOCATION_INTERRUPTED", "OTHER_EXCEPTION");
    private static final Set<String> RECEIPT_TYPES = Set.of(
            "NORMAL_RECEIPT", "PARTIAL_RECEIPT", "DAMAGED_RECEIPT", "REJECTED_RECEIPT");
    private static final Set<String> TERMINAL_STATUSES = Set.of(
            STATUS_COMPLETED, STATUS_CANCELLED, STATUS_CLOSED);

    @Resource
    private ErpLogisticsRelationMapper relationMapper;
    @Resource
    private ErpLogisticsRelationItemMapper relationItemMapper;
    @Resource
    private ErpLogisticsNodeMapper nodeMapper;
    @Resource
    private ErpDeliveryReceiptMapper receiptMapper;
    @Resource
    private ErpLogisticsExceptionMapper exceptionMapper;
    @Resource
    private ErpLogisticsFulfillmentEventMapper fulfillmentEventMapper;
    @Resource
    private ErpLogisticsEventLogMapper eventLogMapper;
    @Resource
    private ErpSaleOrderMapper saleOrderMapper;
    @Resource
    private ErpSaleOutMapper saleOutMapper;
    @Resource
    private ErpPurchaseReturnMapper purchaseReturnMapper;
    @Resource
    private ErpSaleReturnMapper saleReturnMapper;
    @Resource
    private ErpLogisticsNotificationService notificationService;
    @Resource
    private FileApi fileApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ErpLogisticsEventRespVO receiveTask(ErpLogisticsTaskReceiveReqVO reqVO) {
        validateTenant(reqVO.getTenantId());
        String requestHash = hash(reqVO);
        ErpLogisticsEventRespVO duplicate = findDuplicate(
                reqVO.getRequestId(), requestHash, reqVO.getLogisticsTaskNo(), reqVO.getEventVersion());
        if (duplicate != null) {
            return duplicate;
        }

        ErpLogisticsRelationDO relation = relationMapper.selectByTaskNoForUpdate(reqVO.getLogisticsTaskNo());
        if (relation == null) {
            if (reqVO.getEventVersion() != 1L) {
                return recordEvent(null, reqVO.getRequestId(), requestHash, reqVO.getLogisticsTaskNo(),
                        "TASK", reqVO.getEventVersion(), PROCESS_PENDING_COMPENSATION,
                        "首个物流任务事件版本必须为 1", reqVO);
            }
            validateSourceOrder(reqVO.getSourceOrderType(), reqVO.getSourceOrderNo());
            ErpLogisticsRelationDO relationByOrder = relationMapper.selectBySourceOrderForUpdate(
                    reqVO.getSourceOrderType(), reqVO.getSourceOrderNo());
            if (relationByOrder != null) {
                throw exception(LOGISTICS_TASK_CONFLICT);
            }
            validateTaskItems(reqVO.getItems());
            relation = BeanUtils.toBean(reqVO, ErpLogisticsRelationDO.class)
                    .setRequestHash(requestHash)
                    .setLogisticsStatus(STATUS_PENDING_DISPATCH)
                    .setLatestEventVersion(reqVO.getEventVersion())
                    .setLatestNodeCode("TASK_CREATED")
                    .setLatestNodeName("物流任务创建")
                    .setLatestNodeTime(LocalDateTime.now())
                    .setLocationOnline(false)
                    .setHasException(false);
            relationMapper.insert(relation);
            Long relationId = relation.getId();
            List<ErpLogisticsRelationItemDO> items = reqVO.getItems().stream()
                    .map(item -> BeanUtils.toBean(item, ErpLogisticsRelationItemDO.class)
                            .setRelationId(relationId)
                            .setItemNameSnapshot(item.getItemName())
                            .setSpecificationSnapshot(item.getSpecification())
                            .setUnitNameSnapshot(item.getUnitName()))
                    .toList();
            relationItemMapper.insertBatch(items);
            addFulfillmentEvent(relation, "TASK_CREATED", "物流任务创建", "已完成 ERP 单据与物流任务关联",
                    LocalDateTime.now(), reqVO.getEventVersion());
            ErpLogisticsEventRespVO result = recordEvent(relation, reqVO.getRequestId(), requestHash,
                    reqVO.getLogisticsTaskNo(), "TASK", reqVO.getEventVersion(), PROCESS_SUCCESS,
                    "物流任务关联成功", reqVO);
            notifySourceOwner(relation, "erp_logistics_task_created");
            return result;
        }

        if (!Objects.equals(relation.getSourceOrderType(), reqVO.getSourceOrderType())
                || !Objects.equals(relation.getSourceOrderNo(), reqVO.getSourceOrderNo())) {
            throw exception(LOGISTICS_TASK_CONFLICT);
        }
        ErpLogisticsEventRespVO versionResult = checkVersion(relation, reqVO.getRequestId(), requestHash,
                "TASK", reqVO.getEventVersion(), reqVO);
        if (versionResult != null) {
            return versionResult;
        }
        boolean estimatedArrivalChanged = !Objects.equals(relation.getEstimatedArrivalTime(),
                reqVO.getEstimatedArrivalTime());
        ErpLogisticsRelationDO update = BeanUtils.toBean(reqVO, ErpLogisticsRelationDO.class)
                .setId(relation.getId()).setRequestId(null).setRequestHash(null)
                .setLatestEventVersion(reqVO.getEventVersion());
        relationMapper.updateById(update);
        relation.setLatestEventVersion(reqVO.getEventVersion());
        relation.setEstimatedArrivalTime(reqVO.getEstimatedArrivalTime());
        addFulfillmentEvent(relation, "TASK_UPDATED", "物流任务更新", "物流任务基础信息已更新",
                LocalDateTime.now(), reqVO.getEventVersion());
        if (estimatedArrivalChanged) {
            String estimatedArrivalText = reqVO.getEstimatedArrivalTime() == null
                    ? "未设置" : reqVO.getEstimatedArrivalTime().toString();
            addFulfillmentEvent(relation, "ESTIMATED_ARRIVAL_UPDATED", "预计到达时间变更",
                    "预计到达时间已更新为 " + estimatedArrivalText,
                    LocalDateTime.now(), reqVO.getEventVersion());
            notifySourceOwner(relation, "erp_logistics_eta_updated", Map.of(
                    "estimatedArrivalTime", estimatedArrivalText));
        }
        return recordEvent(relation, reqVO.getRequestId(), requestHash, reqVO.getLogisticsTaskNo(),
                "TASK", reqVO.getEventVersion(), PROCESS_SUCCESS, "物流任务快照更新成功", reqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ErpLogisticsEventRespVO receiveNode(ErpLogisticsNodeReceiveReqVO reqVO) {
        validateTenant(reqVO.getTenantId());
        String requestHash = hash(reqVO);
        ErpLogisticsEventRespVO duplicate = findDuplicate(
                reqVO.getRequestId(), requestHash, reqVO.getLogisticsTaskNo(), reqVO.getEventVersion());
        if (duplicate != null) {
            return duplicate;
        }
        ErpLogisticsRelationDO relation = requireRelationForUpdate(reqVO.getLogisticsTaskNo());
        if (!Objects.equals(relation.getWaybillNo(), reqVO.getWaybillNo())) {
            throw exception(LOGISTICS_TASK_CONFLICT);
        }
        ErpLogisticsEventRespVO versionResult = checkVersion(relation, reqVO.getRequestId(), requestHash,
                "NODE", reqVO.getEventVersion(), reqVO);
        if (versionResult != null) {
            return versionResult;
        }

        String targetStatus = getNodeTargetStatus(reqVO.getNodeCode(), relation);
        ErpLogisticsEventRespVO terminalResult = rejectTerminalRollback(relation, targetStatus,
                reqVO.getRequestId(), requestHash, "NODE", reqVO.getEventVersion(), reqVO);
        if (terminalResult != null) {
            return terminalResult;
        }
        validateAttachments(reqVO.getAttachments());
        ErpLogisticsNodeDO node = BeanUtils.toBean(reqVO, ErpLogisticsNodeDO.class)
                .setRelationId(relation.getId())
                .setRequestHash(requestHash)
                .setReceivedAt(LocalDateTime.now())
                .setAttachmentsJson(toJson(reqVO.getAttachments()));
        nodeMapper.insert(node);

        ErpLogisticsRelationDO update = new ErpLogisticsRelationDO().setId(relation.getId())
                .setLogisticsStatus(targetStatus)
                .setLatestEventVersion(reqVO.getEventVersion())
                .setLatestNodeCode(reqVO.getNodeCode())
                .setLatestNodeName(reqVO.getNodeName())
                .setLatestNodeTime(reqVO.getOccurredAt())
                .setLatestLocation(reqVO.getLocationDescription())
                .setLocationOnline(reqVO.getLocationOnline())
                .setLastOnlineTime(reqVO.getLastOnlineTime());
        if (isExceptionNode(reqVO.getNodeCode())) {
            if (!STATUS_EXCEPTION_HANDLING.equals(relation.getLogisticsStatus())) {
                update.setResumeStatus(relation.getLogisticsStatus());
            }
            update.setHasException(true);
            exceptionMapper.insert(new ErpLogisticsExceptionDO()
                    .setRelationId(relation.getId())
                    .setNodeId(node.getId())
                    .setExceptionCode(reqVO.getNodeCode())
                    .setExceptionName(reqVO.getNodeName())
                    .setDescription(reqVO.getRemark())
                    .setStatus("OPEN")
                    .setOccurredAt(reqVO.getOccurredAt()));
        }
        relationMapper.updateById(update);
        relation.setLogisticsStatus(targetStatus);
        relation.setLatestEventVersion(reqVO.getEventVersion());
        addFulfillmentEvent(relation, reqVO.getNodeCode(), reqVO.getNodeName(), reqVO.getRemark(),
                reqVO.getOccurredAt(), reqVO.getEventVersion());
        ErpLogisticsEventRespVO result = recordEvent(relation, reqVO.getRequestId(), requestHash,
                reqVO.getLogisticsTaskNo(), "NODE", reqVO.getEventVersion(), PROCESS_SUCCESS,
                "物流节点接收成功", reqVO);
        if ("DEPARTED".equals(reqVO.getNodeCode())) {
            notifySourceOwner(relation, "erp_logistics_departed");
        } else if (isExceptionNode(reqVO.getNodeCode())) {
            notifySourceOwner(relation, "erp_logistics_exception");
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ErpLogisticsEventRespVO receiveReceipt(ErpLogisticsReceiptReceiveReqVO reqVO) {
        validateTenant(reqVO.getTenantId());
        if (!RECEIPT_TYPES.contains(reqVO.getReceiptType())) {
            throw exception(LOGISTICS_RECEIPT_INVALID);
        }
        String requestHash = hash(reqVO);
        ErpLogisticsEventRespVO duplicate = findDuplicate(
                reqVO.getRequestId(), requestHash, reqVO.getLogisticsTaskNo(), reqVO.getEventVersion());
        if (duplicate != null) {
            return duplicate;
        }
        ErpLogisticsRelationDO relation = requireRelationForUpdate(reqVO.getLogisticsTaskNo());
        if (!Objects.equals(relation.getSourceOrderNo(), reqVO.getSourceOrderNo())) {
            throw exception(LOGISTICS_RECEIPT_INVALID);
        }
        ErpLogisticsEventRespVO versionResult = checkVersion(relation, reqVO.getRequestId(), requestHash,
                "RECEIPT", reqVO.getEventVersion(), reqVO);
        if (versionResult != null) {
            return versionResult;
        }
        BigDecimal taskQuantity = relationItemMapper.selectListByRelationId(relation.getId()).stream()
                .map(ErpLogisticsRelationItemDO::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal receivedQuantity = receiptMapper.selectListByRelationId(relation.getId()).stream()
                .map(ErpDeliveryReceiptDO::getReceiptQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (receivedQuantity.add(reqVO.getReceiptQuantity()).compareTo(taskQuantity) > 0) {
            throw exception(LOGISTICS_RECEIPT_INVALID);
        }
        boolean normal = "NORMAL_RECEIPT".equals(reqVO.getReceiptType());
        String targetStatus = normal ? STATUS_COMPLETED : STATUS_EXCEPTION_HANDLING;
        ErpLogisticsEventRespVO terminalResult = rejectTerminalRollback(relation, targetStatus,
                reqVO.getRequestId(), requestHash, "RECEIPT", reqVO.getEventVersion(), reqVO);
        if (terminalResult != null) {
            return terminalResult;
        }
        validateAttachments(reqVO.getAttachments());
        receiptMapper.insert(BeanUtils.toBean(reqVO, ErpDeliveryReceiptDO.class)
                .setRelationId(relation.getId())
                .setRequestHash(requestHash)
                .setAttachmentsJson(toJson(reqVO.getAttachments())));

        ErpLogisticsRelationDO update = new ErpLogisticsRelationDO().setId(relation.getId())
                .setLogisticsStatus(targetStatus)
                .setLatestEventVersion(reqVO.getEventVersion())
                .setLatestNodeCode(normal ? "COMPLETED" : reqVO.getReceiptType())
                .setLatestNodeName(normal ? "签收完成" : "签收异常")
                .setLatestNodeTime(reqVO.getReceiptTime());
        if (!normal) {
            update.setResumeStatus(relation.getLogisticsStatus()).setHasException(true);
            exceptionMapper.insert(new ErpLogisticsExceptionDO()
                    .setRelationId(relation.getId())
                    .setExceptionCode(reqVO.getReceiptType())
                    .setExceptionName("签收异常")
                    .setDescription(reqVO.getRemark())
                    .setStatus("OPEN")
                    .setOccurredAt(reqVO.getReceiptTime()));
        }
        relationMapper.updateById(update);
        relation.setLogisticsStatus(targetStatus);
        relation.setLatestEventVersion(reqVO.getEventVersion());
        addFulfillmentEvent(relation, normal ? "RECEIPT_COMPLETED" : reqVO.getReceiptType(),
                normal ? "签收完成" : "签收异常", reqVO.getRemark(), reqVO.getReceiptTime(), reqVO.getEventVersion());
        ErpLogisticsEventRespVO result = recordEvent(relation, reqVO.getRequestId(), requestHash,
                reqVO.getLogisticsTaskNo(), "RECEIPT", reqVO.getEventVersion(), PROCESS_SUCCESS,
                "签收结果接收成功", reqVO);
        notifySourceOwner(relation, normal ? "erp_logistics_received" : "erp_logistics_exception_receipt");
        return result;
    }

    @Override
    public PageResult<ErpLogisticsPageRespVO> getLogisticsPage(ErpLogisticsPageReqVO reqVO) {
        return BeanUtils.toBean(relationMapper.selectPage(reqVO), ErpLogisticsPageRespVO.class);
    }

    @Override
    public ErpLogisticsDetailRespVO getLogisticsDetail(Long id) {
        ErpLogisticsRelationDO relation = relationMapper.selectById(id);
        if (relation == null) {
            throw exception(LOGISTICS_RELATION_NOT_EXISTS);
        }
        return buildDetail(relation);
    }

    @Override
    public ErpLogisticsDetailRespVO getLogisticsByOrder(String sourceOrderType, String sourceOrderNo) {
        ErpLogisticsRelationDO relation = relationMapper.selectBySourceOrder(sourceOrderType, sourceOrderNo);
        if (relation == null) {
            throw exception(LOGISTICS_RELATION_NOT_EXISTS);
        }
        return buildDetail(relation);
    }

    @Override
    public ErpLogisticsEventRespVO getEventResult(String requestId) {
        ErpLogisticsEventLogDO event = eventLogMapper.selectByRequestId(requestId);
        if (event == null) {
            throw exception(LOGISTICS_RELATION_NOT_EXISTS);
        }
        ErpLogisticsRelationDO relation = event.getRelationId() == null
                ? relationMapper.selectByTaskNo(event.getLogisticsTaskNo())
                : relationMapper.selectById(event.getRelationId());
        return buildEventResponse(event, relation);
    }

    @Override
    public PageResult<ErpLogisticsEventPageRespVO> getEventPage(ErpLogisticsEventPageReqVO reqVO) {
        return BeanUtils.toBean(eventLogMapper.selectPage(reqVO), ErpLogisticsEventPageRespVO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ErpLogisticsEventRespVO retryPendingEvent(Long id) {
        ErpLogisticsEventLogDO event = eventLogMapper.selectByIdForUpdate(id);
        if (event == null) {
            throw exception(LOGISTICS_RELATION_NOT_EXISTS);
        }
        if (!PROCESS_PENDING_COMPENSATION.equals(event.getProcessStatus()) || StrUtil.isBlank(event.getRequestPayload())) {
            throw exception(LOGISTICS_EVENT_NOT_PENDING_COMPENSATION);
        }
        return switch (event.getEventType()) {
            case "TASK" -> receiveTask(JSONUtil.toBean(event.getRequestPayload(), ErpLogisticsTaskReceiveReqVO.class));
            case "NODE" -> receiveNode(JSONUtil.toBean(event.getRequestPayload(), ErpLogisticsNodeReceiveReqVO.class));
            case "RECEIPT" -> receiveReceipt(JSONUtil.toBean(event.getRequestPayload(), ErpLogisticsReceiptReceiveReqVO.class));
            default -> throw exception(LOGISTICS_EVENT_NOT_PENDING_COMPENSATION);
        };
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleException(ErpLogisticsExceptionHandleReqVO reqVO) {
        ErpLogisticsExceptionDO exceptionRecord = exceptionMapper.selectById(reqVO.getId());
        if (exceptionRecord == null) {
            throw exception(LOGISTICS_EXCEPTION_NOT_EXISTS);
        }
        ErpLogisticsRelationDO relation = relationMapper.selectByIdForUpdate(exceptionRecord.getRelationId());
        if (relation == null) {
            throw exception(LOGISTICS_RELATION_NOT_EXISTS);
        }
        exceptionRecord = exceptionMapper.selectByIdForUpdate(reqVO.getId());
        if (exceptionRecord == null) {
            throw exception(LOGISTICS_EXCEPTION_NOT_EXISTS);
        }
        if (!"OPEN".equals(exceptionRecord.getStatus())) {
            return;
        }
        exceptionMapper.updateById(new ErpLogisticsExceptionDO().setId(reqVO.getId())
                .setStatus("RESOLVED")
                .setHandlerUserId(SecurityFrameworkUtils.getLoginUserId())
                .setHandleResult(reqVO.getHandleResult())
                .setHandleTime(LocalDateTime.now()));
        if (exceptionMapper.selectOpenCountByRelationId(exceptionRecord.getRelationId()) == 0) {
            ErpLogisticsRelationDO update = new ErpLogisticsRelationDO()
                    .setId(exceptionRecord.getRelationId()).setHasException(false);
            if (STATUS_EXCEPTION_HANDLING.equals(relation.getLogisticsStatus())) {
                update.setLogisticsStatus(StrUtil.blankToDefault(relation.getResumeStatus(), STATUS_IN_TRANSIT));
            }
            relationMapper.updateById(update);
            addFulfillmentEvent(relation, "EXCEPTION_RESOLVED", "物流异常已处理", reqVO.getHandleResult(),
                    LocalDateTime.now(), relation.getLatestEventVersion());
            notifySourceOwner(relation, "erp_logistics_exception_resolved");
        }
    }

    private ErpLogisticsEventRespVO findDuplicate(String requestId, String requestHash,
                                                    String taskNo, Long eventVersion) {
        ErpLogisticsEventLogDO existing = eventLogMapper.selectByRequestIdForUpdate(requestId);
        if (existing == null) {
            existing = eventLogMapper.selectByTaskAndVersionForUpdate(taskNo, eventVersion);
        }
        if (existing == null) {
            return null;
        }
        if (!Objects.equals(existing.getRequestHash(), requestHash)) {
            throw exception(LOGISTICS_REQUEST_CONFLICT);
        }
        ErpLogisticsRelationDO relation = existing.getRelationId() == null
                ? relationMapper.selectByTaskNo(taskNo) : relationMapper.selectById(existing.getRelationId());
        if (PROCESS_PENDING_COMPENSATION.equals(existing.getProcessStatus()) && relation != null
                && existing.getEventVersion() == relation.getLatestEventVersion() + 1) {
            return null;
        }
        return buildEventResponse(existing, relation);
    }

    private ErpLogisticsEventRespVO checkVersion(ErpLogisticsRelationDO relation, String requestId,
                                                  String requestHash, String eventType, Long eventVersion, Object request) {
        long currentVersion = relation.getLatestEventVersion() == null ? 0L : relation.getLatestEventVersion();
        if (eventVersion <= currentVersion) {
            return recordEvent(relation, requestId, requestHash, relation.getLogisticsTaskNo(), eventType,
                    eventVersion, PROCESS_REJECTED, "事件版本过期，当前版本为 " + currentVersion, request);
        }
        if (eventVersion > currentVersion + 1) {
            return recordEvent(relation, requestId, requestHash, relation.getLogisticsTaskNo(), eventType,
                    eventVersion, PROCESS_PENDING_COMPENSATION, "事件版本断档，当前版本为 " + currentVersion, request);
        }
        return null;
    }

    private ErpLogisticsEventRespVO rejectTerminalRollback(ErpLogisticsRelationDO relation, String targetStatus,
                                                             String requestId, String requestHash,
                                                             String eventType, Long eventVersion, Object request) {
        if (TERMINAL_STATUSES.contains(relation.getLogisticsStatus())
                && !TERMINAL_STATUSES.contains(targetStatus)) {
            return recordEvent(relation, requestId, requestHash, relation.getLogisticsTaskNo(), eventType,
                    eventVersion, PROCESS_REJECTED, "终态物流任务不能回退到运输状态", request);
        }
        return null;
    }

    private String getNodeTargetStatus(String nodeCode, ErpLogisticsRelationDO relation) {
        if (EXCEPTION_NODE_CODES.contains(nodeCode)) {
            return STATUS_EXCEPTION_HANDLING;
        }
        return switch (nodeCode) {
            case "TASK_CREATED" -> STATUS_PENDING_DISPATCH;
            case "VEHICLE_ASSIGNED", "LOADING_STARTED", "LOADING_COMPLETED" -> STATUS_PENDING_LOADING;
            case "DEPARTED", "IN_TRANSIT" -> STATUS_IN_TRANSIT;
            case "ARRIVED", "UNLOADING_COMPLETED" -> STATUS_ARRIVED;
            case "SIGNED" -> STATUS_SIGNED;
            case "RECOVERED" -> STATUS_EXCEPTION_HANDLING.equals(relation.getLogisticsStatus())
                    ? StrUtil.blankToDefault(relation.getResumeStatus(), STATUS_IN_TRANSIT)
                    : relation.getLogisticsStatus();
            case "CANCELLED" -> STATUS_CANCELLED;
            case "CLOSED" -> STATUS_CLOSED;
            default -> throw exception(LOGISTICS_NODE_INVALID);
        };
    }

    private boolean isExceptionNode(String nodeCode) {
        return EXCEPTION_NODE_CODES.contains(nodeCode);
    }

    private void validateTenant(Long requestTenantId) {
        if (!Objects.equals(TenantContextHolder.getRequiredTenantId(), requestTenantId)) {
            throw exception(LOGISTICS_TENANT_MISMATCH);
        }
    }

    private void validateSourceOrder(String sourceOrderType, String sourceOrderNo) {
        if (!SOURCE_ORDER_TYPES.contains(sourceOrderType)) {
            throw exception(LOGISTICS_SOURCE_ORDER_TYPE_INVALID);
        }
        Object sourceOrder = switch (sourceOrderType) {
            case "SALE_ORDER" -> saleOrderMapper.selectByNo(sourceOrderNo);
            case "SALE_OUT" -> saleOutMapper.selectByNo(sourceOrderNo);
            case "PURCHASE_RETURN" -> purchaseReturnMapper.selectByNo(sourceOrderNo);
            case "SALE_RETURN" -> saleReturnMapper.selectByNo(sourceOrderNo);
            default -> null;
        };
        if (sourceOrder == null) {
            throw exception(LOGISTICS_SOURCE_ORDER_NOT_EXISTS);
        }
        Integer status = switch (sourceOrder) {
            case ErpSaleOrderDO order -> order.getStatus();
            case ErpSaleOutDO out -> out.getStatus();
            case ErpPurchaseReturnDO purchaseReturn -> purchaseReturn.getStatus();
            case ErpSaleReturnDO saleReturn -> saleReturn.getStatus();
            default -> null;
        };
        if (!ErpAuditStatus.APPROVE.getStatus().equals(status)) {
            throw exception(LOGISTICS_SOURCE_ORDER_NOT_APPROVED);
        }
    }

    private void validateTaskItems(List<ErpLogisticsTaskReceiveReqVO.Item> items) {
        Set<Integer> lineNos = new HashSet<>();
        if (items.stream().anyMatch(item -> !lineNos.add(item.getLineNo()))) {
            throw exception(LOGISTICS_TASK_CONFLICT);
        }
    }

    private String hash(Object request) {
        JSONObject payload = JSONUtil.parseObj(request);
        payload.remove("requestId");
        return SecureUtil.sha256(payload.toString());
    }

    private String toJson(List<ErpLogisticsAttachmentVO> attachments) {
        return CollUtil.isEmpty(attachments) ? null : JSONUtil.toJsonStr(attachments);
    }

    /**
     * 物流只保存文件元数据。通过现有文件服务校验 URL、编号和类型，避免把不可访问或伪造的附件写入业务记录。
     */
    private void validateAttachments(List<ErpLogisticsAttachmentVO> attachments) {
        if (CollUtil.isEmpty(attachments)) {
            return;
        }
        for (ErpLogisticsAttachmentVO attachment : attachments) {
            FileMetadataRespDTO file = fileApi.getMetadataByUrl(attachment.getUrl()).getCheckedData();
            if (file == null || !Objects.equals(file.getId(), attachment.getFileId())
                    || !Objects.equals(file.getUrl(), attachment.getUrl())
                    || !Objects.equals(file.getName(), attachment.getFileName())) {
                throw exception(LOGISTICS_ATTACHMENT_INVALID);
            }
            String fileType = StrUtil.blankToDefault(file.getType(), "application/octet-stream");
            if ((!fileType.startsWith("image/") && !"application/pdf".equals(fileType))
                    || (StrUtil.isNotBlank(attachment.getMimeType())
                    && !Objects.equals(fileType, attachment.getMimeType()))) {
                throw exception(LOGISTICS_ATTACHMENT_INVALID);
            }
        }
    }

    private List<ErpLogisticsAttachmentVO> parseAttachments(String json) {
        return StrUtil.isBlank(json) ? List.of() : JSONUtil.toList(json, ErpLogisticsAttachmentVO.class);
    }

    private ErpLogisticsEventRespVO recordEvent(ErpLogisticsRelationDO relation, String requestId,
                                                 String requestHash, String taskNo, String eventType,
                                                 Long eventVersion, String processStatus, String processMessage,
                                                 Object request) {
        ErpLogisticsEventLogDO event = eventLogMapper.selectByTaskAndVersionForUpdate(taskNo, eventVersion);
        if (event != null) {
            eventLogMapper.updateById(new ErpLogisticsEventLogDO().setId(event.getId())
                    .setRelationId(relation == null ? null : relation.getId())
                    .setProcessStatus(processStatus).setProcessMessage(processMessage)
                    .setRequestPayload(request == null ? null : JSONUtil.toJsonStr(request)));
            event.setRelationId(relation == null ? null : relation.getId());
            event.setProcessStatus(processStatus);
            event.setProcessMessage(processMessage);
            event.setRequestPayload(request == null ? event.getRequestPayload() : JSONUtil.toJsonStr(request));
            return buildEventResponse(event, relation);
        }
        event = new ErpLogisticsEventLogDO()
                .setRelationId(relation == null ? null : relation.getId())
                .setRequestId(requestId)
                .setRequestHash(requestHash)
                .setLogisticsTaskNo(taskNo)
                .setEventType(eventType)
                .setEventVersion(eventVersion)
                .setProcessStatus(processStatus)
                .setProcessMessage(processMessage)
                .setRequestPayload(request == null ? null : JSONUtil.toJsonStr(request));
        eventLogMapper.insert(event);
        return buildEventResponse(event, relation);
    }

    private ErpLogisticsEventRespVO recordEvent(ErpLogisticsRelationDO relation, String requestId,
                                                 String requestHash, String taskNo, String eventType,
                                                 Long eventVersion, String processStatus, String processMessage) {
        return recordEvent(relation, requestId, requestHash, taskNo, eventType, eventVersion,
                processStatus, processMessage, null);
    }

    private void addFulfillmentEvent(ErpLogisticsRelationDO relation, String eventCode, String eventName,
                                     String description, LocalDateTime occurredAt, Long eventVersion) {
        fulfillmentEventMapper.insert(new ErpLogisticsFulfillmentEventDO()
                .setRelationId(relation.getId()).setEventCode(eventCode).setEventName(eventName)
                .setEventDescription(description).setOccurredAt(occurredAt).setEventVersion(eventVersion));
    }

    private void notifySourceOwner(ErpLogisticsRelationDO relation, String templateCode) {
        notifySourceOwner(relation, templateCode, Map.of());
    }

    private void notifySourceOwner(ErpLogisticsRelationDO relation, String templateCode,
                                   Map<String, Object> extraParams) {
        if (relation == null) {
            return;
        }
        Long userId = null;
        try {
            userId = switch (relation.getSourceOrderType()) {
                case "SALE_ORDER" -> {
                    ErpSaleOrderDO order = saleOrderMapper.selectByNo(relation.getSourceOrderNo());
                    yield order == null ? null : order.getSaleUserId();
                }
                case "SALE_OUT" -> {
                    ErpSaleOutDO saleOut = saleOutMapper.selectByNo(relation.getSourceOrderNo());
                    yield saleOut == null ? null : saleOut.getSaleUserId();
                }
                case "SALE_RETURN" -> {
                    ErpSaleReturnDO saleReturn = saleReturnMapper.selectByNo(relation.getSourceOrderNo());
                    yield saleReturn == null ? null : saleReturn.getSaleUserId();
                }
                case "PURCHASE_RETURN" -> {
                    ErpPurchaseReturnDO purchaseReturn = purchaseReturnMapper.selectByNo(
                            relation.getSourceOrderNo());
                    yield purchaseReturn == null ? null : parseUserId(purchaseReturn.getCreator());
                }
                default -> null;
            };
        } catch (Exception ex) {
            log.warn("[notifySourceOwner][taskNo({}) 无法解析来源单据负责人]",
                    relation.getLogisticsTaskNo(), ex);
        }
        notificationService.sendAfterCommit(userId, templateCode, relation.getLogisticsTaskNo(),
                relation.getSourceOrderNo(), "/erp/logistics", extraParams);
    }

    private Long parseUserId(String userId) {
        return StrUtil.isBlank(userId) ? null : Long.valueOf(userId);
    }

    private ErpLogisticsEventRespVO buildEventResponse(ErpLogisticsEventLogDO event,
                                                        ErpLogisticsRelationDO relation) {
        return new ErpLogisticsEventRespVO()
                .setRequestId(event.getRequestId())
                .setLogisticsTaskNo(event.getLogisticsTaskNo())
                .setEventVersion(event.getEventVersion())
                .setProcessStatus(event.getProcessStatus())
                .setProcessMessage(event.getProcessMessage())
                .setLogisticsStatus(relation == null ? null : relation.getLogisticsStatus());
    }

    private ErpLogisticsRelationDO requireRelationForUpdate(String taskNo) {
        ErpLogisticsRelationDO relation = relationMapper.selectByTaskNoForUpdate(taskNo);
        if (relation == null) {
            throw exception(LOGISTICS_RELATION_NOT_EXISTS);
        }
        return relation;
    }

    private ErpLogisticsDetailRespVO buildDetail(ErpLogisticsRelationDO relation) {
        ErpLogisticsDetailRespVO detail = BeanUtils.toBean(relation, ErpLogisticsDetailRespVO.class);
        detail.setItems(BeanUtils.toBean(relationItemMapper.selectListByRelationId(relation.getId()),
                ErpLogisticsDetailRespVO.Item.class));
        detail.setNodes(nodeMapper.selectListByRelationId(relation.getId()).stream()
                .map(source -> BeanUtils.toBean(source, ErpLogisticsDetailRespVO.Node.class)
                        .setAttachments(parseAttachments(source.getAttachmentsJson())))
                .toList());
        detail.setReceipts(receiptMapper.selectListByRelationId(relation.getId()).stream()
                .map(source -> BeanUtils.toBean(source, ErpLogisticsDetailRespVO.Receipt.class)
                        .setAttachments(parseAttachments(source.getAttachmentsJson())))
                .toList());
        detail.setExceptions(BeanUtils.toBean(exceptionMapper.selectListByRelationId(relation.getId()),
                ErpLogisticsDetailRespVO.ExceptionRecord.class));
        detail.setFulfillmentEvents(BeanUtils.toBean(fulfillmentEventMapper.selectListByRelationId(relation.getId()),
                ErpLogisticsDetailRespVO.FulfillmentEvent.class));
        return detail;
    }

}
