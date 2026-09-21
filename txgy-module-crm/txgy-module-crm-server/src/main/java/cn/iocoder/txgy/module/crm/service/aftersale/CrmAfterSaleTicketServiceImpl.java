package cn.iocoder.txgy.module.crm.service.aftersale;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.ticket.CrmAfterSaleTicketPageReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.ticket.CrmAfterSaleTicketSaveReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleTicketDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleVisitRecordDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleWarrantyDO;
import cn.iocoder.txgy.module.crm.dal.mysql.aftersale.CrmAfterSaleTicketMapper;
import cn.iocoder.txgy.module.crm.dal.mysql.aftersale.CrmAfterSaleVisitRecordMapper;
import cn.iocoder.txgy.module.crm.dal.redis.no.CrmNoRedisDAO;
import cn.iocoder.txgy.module.crm.enums.aftersale.CrmAfterSaleTicketOperateTypeEnum;
import cn.iocoder.txgy.module.crm.enums.aftersale.CrmAfterSaleTicketPriorityEnum;
import cn.iocoder.txgy.module.crm.enums.aftersale.CrmAfterSaleTicketSourceEnum;
import cn.iocoder.txgy.module.crm.enums.aftersale.CrmAfterSaleTicketStatusEnum;
import cn.iocoder.txgy.module.crm.service.customer.CrmCustomerService;
import cn.iocoder.txgy.module.crm.util.CrmPermissionUtils;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.txgy.module.crm.enums.ErrorCodeConstants.*;

/**
 * 售后问题工单 Service 实现类
 *
 * @author txgycloud
 */
@Service
@Validated
public class CrmAfterSaleTicketServiceImpl implements CrmAfterSaleTicketService {

    @Resource
    private CrmAfterSaleTicketMapper ticketMapper;
    @Resource
    private CrmAfterSaleTicketNoteService ticketNoteService;
    @Resource
    private CrmAfterSaleVisitRecordService visitRecordService;
    @Resource
    private CrmAfterSaleVisitRecordMapper visitRecordMapper;
    @Resource
    private CrmAfterSaleWarrantyService warrantyService;
    @Resource
    private CrmAfterSaleMessageService messageService;
    @Resource
    private CrmCustomerService customerService;
    @Resource
    private CrmNoRedisDAO noRedisDAO;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private CrmAfterSaleConfigService configService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTicket(CrmAfterSaleTicketSaveReqVO createReqVO, Long userId) {
        // 1. 归属带出：来源回访记录 > 质保档案 > 直接指定客户
        CrmAfterSaleTicketDO ticket = BeanUtils.toBean(createReqVO, CrmAfterSaleTicketDO.class);
        if (createReqVO.getVisitRecordId() != null) {
            CrmAfterSaleVisitRecordDO record = visitRecordService.validateVisitRecord(createReqVO.getVisitRecordId());
            ticket.setWarrantyId(record.getWarrantyId());
            ticket.setCustomerId(record.getCustomerId());
            ticket.setContractId(record.getContractId());
            ticket.setSource(CrmAfterSaleTicketSourceEnum.VISIT.getSource());
        } else if (ticket.getWarrantyId() != null) {
            CrmAfterSaleWarrantyDO warranty = warrantyService.validateWarranty(ticket.getWarrantyId());
            if (ticket.getCustomerId() == null) {
                ticket.setCustomerId(warranty.getCustomerId());
            }
            if (ticket.getContractId() == null) {
                ticket.setContractId(warranty.getContractId());
            }
        }
        // 1.1 客户必填校验
        if (ticket.getCustomerId() == null) {
            throw exception(AFTER_SALE_TICKET_CREATE_FAIL_CUSTOMER_NOT_EXISTS);
        }
        customerService.validateCustomer(ticket.getCustomerId());
        // 1.2 补齐默认值
        if (ticket.getSource() == null) {
            ticket.setSource(CrmAfterSaleTicketSourceEnum.CUSTOMER.getSource());
        }
        if (ticket.getPriority() == null) {
            ticket.setPriority(CrmAfterSaleTicketPriorityEnum.MEDIUM.getPriority());
        }
        ticket.setStatus(CrmAfterSaleTicketStatusEnum.PENDING.getStatus());
        ticket.setNo(generateTicketNo());

        // 2. 插入工单
        ticketMapper.insert(ticket);
        // 2.1 回写回访记录 ticketId
        if (ticket.getVisitRecordId() != null) {
            CrmAfterSaleVisitRecordDO record = visitRecordMapper.selectById(ticket.getVisitRecordId());
            if (record != null) {
                CrmAfterSaleVisitRecordDO recordUpdate = new CrmAfterSaleVisitRecordDO();
                recordUpdate.setId(record.getId());
                recordUpdate.setTicketId(ticket.getId());
                visitRecordMapper.updateById(recordUpdate);
            }
        }
        // 2.2 创建时已指定处理人则发送站内信通知
        if (ticket.getAssigneeUserId() != null) {
            messageService.sendTicketAssignNotify(ticket.getAssigneeUserId(), ticket);
        }
        return ticket.getId();
    }

    @Override
    public void updateTicket(CrmAfterSaleTicketSaveReqVO updateReqVO, Long userId) {
        // 1. 校验存在与状态
        CrmAfterSaleTicketDO oldTicket = validateTicket(updateReqVO.getId());
        if (isFinishedOrClosed(oldTicket)) {
            throw exception(AFTER_SALE_TICKET_UPDATE_FAIL_FINISH);
        }
        // 1.1 权限校验
        if (!canOperate(oldTicket, userId)) {
            throw exception(AFTER_SALE_TICKET_OPERATE_DENIED);
        }
        // 1.2 锁定主数据：工单编号、客户/质保/合同/来源、处理人（状态用专属接口变更）
        updateReqVO.setWarrantyId(oldTicket.getWarrantyId())
                .setCustomerId(oldTicket.getCustomerId()).setContractId(oldTicket.getContractId())
                .setVisitRecordId(oldTicket.getVisitRecordId()).setSource(oldTicket.getSource())
                .setAssigneeUserId(oldTicket.getAssigneeUserId());
        // 2. 更新（no/status 等字段不在 SaveReqVO 中，不受影响）
        CrmAfterSaleTicketDO updateObj = BeanUtils.toBean(updateReqVO, CrmAfterSaleTicketDO.class);
        updateObj.setStatus(oldTicket.getStatus());
        ticketMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTicket(Long id, Long userId) {
        // 1. 校验存在与状态
        CrmAfterSaleTicketDO ticket = validateTicket(id);
        if (!Objects.equals(ticket.getStatus(), CrmAfterSaleTicketStatusEnum.PENDING.getStatus())
                || ticket.getAssigneeUserId() != null) {
            throw exception(AFTER_SALE_TICKET_DELETE_DENIED);
        }
        if (!canOperate(ticket, userId)) {
            throw exception(AFTER_SALE_TICKET_OPERATE_DENIED);
        }
        // 2. 删除工单，并回清关联回访记录的 ticketId
        ticketMapper.deleteById(id);
        if (ticket.getVisitRecordId() != null) {
            CrmAfterSaleVisitRecordDO record = visitRecordMapper.selectById(ticket.getVisitRecordId());
            if (record != null && Objects.equals(record.getTicketId(), id)) {
                CrmAfterSaleVisitRecordDO recordUpdate = new CrmAfterSaleVisitRecordDO();
                recordUpdate.setId(record.getId());
                recordUpdate.setTicketId(null);
                visitRecordMapper.updateById(recordUpdate);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignTicket(Long id, Long assigneeUserId, Long userId) {
        // 1. 校验工单与权限
        CrmAfterSaleTicketDO ticket = validateTicket(id);
        if (!canOperate(ticket, userId)) {
            throw exception(AFTER_SALE_TICKET_ASSIGN_FAIL_DENIED);
        }
        if (isFinishedOrClosed(ticket)) {
            throw exception(AFTER_SALE_TICKET_UPDATE_FAIL_FINISH);
        }
        if (Objects.equals(ticket.getAssigneeUserId(), assigneeUserId)) {
            return; // 处理人未变化
        }
        // 2. 更新处理人
        CrmAfterSaleTicketDO updateObj = new CrmAfterSaleTicketDO();
        updateObj.setId(id);
        updateObj.setAssigneeUserId(assigneeUserId);
        ticketMapper.updateById(updateObj);
        // 3. 发送站内信通知新处理人
        ticket.setAssigneeUserId(assigneeUserId);
        messageService.sendTicketAssignNotify(assigneeUserId, ticket);
        // 4. 留痕
        String nickname = getNickname(assigneeUserId);
        ticketNoteService.createTicketNote(id, CrmAfterSaleTicketOperateTypeEnum.ASSIGN.getType(),
                StrUtil.format("指派处理人：{}（{}）", nickname, assigneeUserId), userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTicketStatus(Long id, Integer status, String content, Long userId) {
        // 1. 校验工单与权限
        CrmAfterSaleTicketDO ticket = validateTicket(id);
        if (!canOperate(ticket, userId)) {
            throw exception(AFTER_SALE_TICKET_OPERATE_DENIED);
        }
        // 2. 状态机校验
        CrmAfterSaleTicketStatusEnum targetStatus = parseStatus(status);
        if (targetStatus == null) {
            throw exception(AFTER_SALE_TICKET_UPDATE_STATUS_FAIL_INVALID);
        }
        validateStatusTransition(ticket.getStatus(), targetStatus.getStatus());

        // 3. 更新状态与时间戳
        LocalDateTime now = LocalDateTime.now();
        CrmAfterSaleTicketDO updateObj = new CrmAfterSaleTicketDO();
        updateObj.setId(id);
        updateObj.setStatus(targetStatus.getStatus());
        Integer operateType;
        switch (targetStatus) {
            case PENDING -> { // 退回待处理
                operateType = CrmAfterSaleTicketOperateTypeEnum.STATUS_CHANGE.getType();
                if (StrUtil.isBlank(content)) {
                    content = "退回待处理";
                }
            }
            case PROCESSING -> { // 开始处理/重新开始处理
                if (ticket.getStartTime() == null) {
                    updateObj.setStartTime(now);
                }
                updateObj.setFinishTime(null);
                operateType = CrmAfterSaleTicketOperateTypeEnum.STATUS_CHANGE.getType();
                if (StrUtil.isBlank(content)) {
                    content = "开始处理";
                }
            }
            case FINISH -> { // 处理完成
                updateObj.setFinishTime(now);
                if (StrUtil.isNotBlank(content)) {
                    updateObj.setHandleResult(content);
                }
                operateType = CrmAfterSaleTicketOperateTypeEnum.HANDLE.getType();
                if (StrUtil.isBlank(content)) {
                    content = "处理完成";
                }
            }
            default -> { // 关闭
                updateObj.setCloseTime(now);
                updateObj.setCloseUserId(userId);
                operateType = CrmAfterSaleTicketOperateTypeEnum.CLOSE.getType();
                if (StrUtil.isBlank(content)) {
                    content = "关闭工单";
                }
            }
        }
        ticketMapper.updateById(updateObj);
        // 4. 留痕
        ticketNoteService.createTicketNote(id, operateType, content, userId);
    }

    @Override
    public CrmAfterSaleTicketDO getTicket(Long id) {
        return ticketMapper.selectById(id);
    }

    @Override
    public CrmAfterSaleTicketDO validateTicket(Long id) {
        CrmAfterSaleTicketDO ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw exception(AFTER_SALE_TICKET_NOT_EXISTS);
        }
        return ticket;
    }

    @Override
    public PageResult<CrmAfterSaleTicketDO> getTicketPage(CrmAfterSaleTicketPageReqVO pageReqVO, Long userId) {
        // 待办「工单超时预警」：实时按售后设置判定，不预生成任何待办数据
        if (CrmAfterSaleTicketPageReqVO.REMIND_TYPE_OVERDUE.equals(pageReqVO.getRemindType())) {
            return ticketMapper.selectPageByOverdue(pageReqVO, resolveRemindAssigneeUserIds(userId),
                    resolveOverdueDeadline());
        }
        return ticketMapper.selectPage(pageReqVO);
    }

    @Override
    public Long getTicketOverdueCount(Long userId) {
        return ticketMapper.selectCountByOverdue(resolveRemindAssigneeUserIds(userId), resolveOverdueDeadline());
    }

    /**
     * 解析待办归属范围：本人 + 下属（与待办现有的「归属过滤」口径一致）
     *
     * <p>说明：售后工单不在 CRM 的 crm_permission 数据权限体系内，
     * 因此沿用工单的「处理人」(assigneeUserId) 作为归属人。
     */
    private Set<Long> resolveRemindAssigneeUserIds(Long userId) {
        Set<Long> userIds = new HashSet<>();
        if (userId == null) {
            return userIds;
        }
        userIds.add(userId);
        List<AdminUserRespDTO> subordinates = adminUserApi.getUserListBySubordinate(userId).getCheckedData();
        if (CollUtil.isNotEmpty(subordinates)) {
            userIds.addAll(convertSet(subordinates, AdminUserRespDTO::getId));
        }
        return userIds;
    }

    /**
     * 计算超时截止时间：今天 - 售后设置「工单超时预警天数」的日终
     *
     * <p>即「当前日期 - 工单创建日期 ≥ 超时预警天数」；未配置时按售后设置默认值 7 天兜底。
     */
    private LocalDateTime resolveOverdueDeadline() {
        Integer overdueDays = configService.getConfig().getTicketOverdueDays();
        int days = overdueDays == null || overdueDays < 0 ? 7 : overdueDays;
        return LocalDateTimeUtil.endOfDay(LocalDate.now().minusDays(days));
    }

    // ========== 私有方法 ==========

    private String generateTicketNo() {
        String no = noRedisDAO.generate("AT"); // AT + 日期 + 6 位自增
        if (ticketMapper.selectByNo(no) != null) {
            throw exception(AFTER_SALE_TICKET_NO_EXISTS);
        }
        return no;
    }

    private CrmAfterSaleTicketStatusEnum parseStatus(Integer status) {
        for (CrmAfterSaleTicketStatusEnum item : CrmAfterSaleTicketStatusEnum.values()) {
            if (Objects.equals(item.getStatus(), status)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 状态机：已关闭不可流转；其余按 待处理-处理中-已完成/关闭 顺序流转，支持退回与重开
     */
    private void validateStatusTransition(Integer currentStatus, Integer targetStatus) {
        if (Objects.equals(currentStatus, targetStatus)
                || Objects.equals(currentStatus, CrmAfterSaleTicketStatusEnum.CLOSED.getStatus())) {
            throw exception(AFTER_SALE_TICKET_UPDATE_STATUS_FAIL_INVALID);
        }
        boolean valid = switch (targetStatus) {
            case 1 -> currentStatus.equals(CrmAfterSaleTicketStatusEnum.PROCESSING.getStatus())
                    || currentStatus.equals(CrmAfterSaleTicketStatusEnum.FINISH.getStatus()); // 退回待处理
            case 2 -> currentStatus.equals(CrmAfterSaleTicketStatusEnum.PENDING.getStatus())
                    || currentStatus.equals(CrmAfterSaleTicketStatusEnum.FINISH.getStatus()); // 开始/重开
            case 3 -> currentStatus.equals(CrmAfterSaleTicketStatusEnum.PENDING.getStatus())
                    || currentStatus.equals(CrmAfterSaleTicketStatusEnum.PROCESSING.getStatus()); // 完成
            case 4 -> true; // 关闭：任何非关闭状态
            default -> false;
        };
        if (!valid) {
            throw exception(AFTER_SALE_TICKET_UPDATE_STATUS_FAIL_INVALID);
        }
    }

    private boolean isFinishedOrClosed(CrmAfterSaleTicketDO ticket) {
        return Objects.equals(ticket.getStatus(), CrmAfterSaleTicketStatusEnum.FINISH.getStatus())
                || Objects.equals(ticket.getStatus(), CrmAfterSaleTicketStatusEnum.CLOSED.getStatus());
    }

    private boolean canOperate(CrmAfterSaleTicketDO ticket, Long userId) {
        if (CrmPermissionUtils.isAdminUser(userId)) {
            return true;
        }
        if (StrUtil.equals(StrUtil.toString(userId), ticket.getCreator())) {
            return true;
        }
        return Objects.equals(ticket.getAssigneeUserId(), userId);
    }

    private String getNickname(Long userId) {
        if (userId == null) {
            return "";
        }
        AdminUserRespDTO user = adminUserApi.getUser(userId).getCheckedData();
        return user != null ? user.getNickname() : "";
    }

}
