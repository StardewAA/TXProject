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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
        // 1.3 开始处理时间（startTime）编辑规则：
        //     工单已闭环（已完成 / 已关闭）时锁定、沿用旧值 —— 处理时长已经定格，
        //     事后修改会让「工单超时预警」的判定与历史统计失真；
        //     待处理 / 处理中阶段允许修正（覆盖处理人补录实际开始时间的场景）。
        //     状态本身不在本接口变更（见 updateTicketStatus）。
        Integer oldStatus = oldTicket.getStatus();
        boolean closed = CrmAfterSaleTicketStatusEnum.FINISH.getStatus().equals(oldStatus)
                || CrmAfterSaleTicketStatusEnum.CLOSED.getStatus().equals(oldStatus);
        if (closed) {
            updateReqVO.setStartTime(oldTicket.getStartTime());
        }
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
                // 若工单此前已被标记「已超时」，刷新起算时间（开始处理时间）：
                // 否则起算时间不变，超时检查会立即再次把它标回「已超时」，形成死循环
                if (Objects.equals(ticket.getStatus(), CrmAfterSaleTicketStatusEnum.OVERDUE.getStatus())) {
                    updateObj.setStartTime(now);
                }
                operateType = CrmAfterSaleTicketOperateTypeEnum.STATUS_CHANGE.getType();
                if (StrUtil.isBlank(content)) {
                    content = "退回待处理";
                }
            }
            case PROCESSING -> { // 开始处理/重新开始处理
                // 首次开始处理时记录开始时间；
                // 从「已超时」恢复处理时同样刷新起算时间，开启新一轮处理周期，
                // 避免超时检查立即再次把工单标回「已超时」（死循环）
                if (ticket.getStartTime() == null
                        || Objects.equals(ticket.getStatus(), CrmAfterSaleTicketStatusEnum.OVERDUE.getStatus())) {
                    updateObj.setStartTime(now);
                }
                updateObj.setFinishTime(null);
                operateType = CrmAfterSaleTicketOperateTypeEnum.STATUS_CHANGE.getType();
                if (StrUtil.isBlank(content)) {
                    content = "开始处理";
                }
            }
            case FINISH -> { // 处理完成
                // 完成工单必须填写「工单记录」（处理结果）：它是闭环依据，
                // 也是超时预警消除与售后追溯的凭证，留空不允许完成
                if (StrUtil.isBlank(content)) {
                    throw exception(AFTER_SALE_TICKET_FINISH_NEED_RECORD);
                }
                updateObj.setFinishTime(now);
                updateObj.setHandleResult(content);
                operateType = CrmAfterSaleTicketOperateTypeEnum.HANDLE.getType();
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

    /**
     * 懒同步的最小间隔（毫秒）：30 秒内最多触发一次超时自动标记，避免高频查询频繁写库
     */
    private static final long AUTO_MARK_INTERVAL_MS = 30_000L;

    /**
     * 上次懒同步时间戳（毫秒）；0 表示尚未同步过
     */
    private static final java.util.concurrent.atomic.AtomicLong LAST_AUTO_MARK_AT =
            new java.util.concurrent.atomic.AtomicLong(0L);

    @Override
    public PageResult<CrmAfterSaleTicketDO> getTicketPage(CrmAfterSaleTicketPageReqVO pageReqVO, Long userId) {
        // 懒同步：读取前按需把已超时的工单标记为「已超时」，保证待办与列表状态实时
        autoMarkOverdueIfNeeded();
        // 待办「工单超时预警」：查询已被标记为「已超时」的工单
        if (CrmAfterSaleTicketPageReqVO.REMIND_TYPE_OVERDUE.equals(pageReqVO.getRemindType())) {
            return ticketMapper.selectPageByOverdue(pageReqVO, resolveRemindAssigneeUserIds(userId),
                    resolveOverdueDeadline());
        }
        return ticketMapper.selectPage(pageReqVO);
    }

    @Override
    public Long getTicketOverdueCount(Long userId) {
        autoMarkOverdueIfNeeded();
        return ticketMapper.selectCountByOverdue(resolveRemindAssigneeUserIds(userId), resolveOverdueDeadline());
    }

    /**
     * 懒同步：在读取工单数据前按需触发一次超时自动标记（默认 30 秒内只执行一次）
     *
     * <p><b>为什么需要</b>：超时状态由定时任务每日凌晨统一标记，用户在「当天刚超时 /
     * 刚把开始时间改成过去 / 刚导入数据」时打开页面，会看到「已超时却仍是待处理/处理中」
     * 的滞后数据，需要人工干预 —— 与「无需手动操作即可看到最新状态」相冲突。
     *
     * <p><b>容错</b>：同步失败只记录日志，绝不影响正常的查询返回（读接口不能因为写失败而不可用）。
     */
    private void autoMarkOverdueIfNeeded() {
        long now = System.currentTimeMillis();
        long last = LAST_AUTO_MARK_AT.get();
        if (now - last < AUTO_MARK_INTERVAL_MS) {
            return;
        }
        // 竞争失败（别的线程刚同步过）时直接返回，避免并发重复写
        if (!LAST_AUTO_MARK_AT.compareAndSet(last, now)) {
            return;
        }
        try {
            int count = autoMarkOverdueTickets();
            if (count > 0) {
                log.info("[autoMarkOverdueIfNeeded][懒同步：自动标记超时工单 {} 个]", count);
            }
        } catch (Exception ex) {
            log.error("[autoMarkOverdueIfNeeded][懒同步失败，不影响查询]", ex);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int autoMarkOverdueTickets() {
        // 1. 超时截止时间：今天 - 售后设置「工单超时预警天数」的日终（未配置时按 7 天兜底）
        LocalDateTime deadline = resolveOverdueDeadline();
        // 2. 扫描未关闭（待处理 / 处理中 / 已完成）且已超时的工单
        //    起算时间 = 开始处理时间，尚未开始处理时以创建时间兜底（与 Mapper 的 COALESCE 口径一致）
        List<CrmAfterSaleTicketDO> tickets = ticketMapper.selectListByUnclosedOverdue(deadline);
        if (CollUtil.isEmpty(tickets)) {
            return 0;
        }
        // 3. 逐条标记「已超时」并写入流转留痕；单条失败只记录日志，不中断其余工单
        int count = 0;
        for (CrmAfterSaleTicketDO ticket : tickets) {
            try {
                CrmAfterSaleTicketDO updateObj = new CrmAfterSaleTicketDO();
                updateObj.setId(ticket.getId());
                updateObj.setStatus(CrmAfterSaleTicketStatusEnum.OVERDUE.getStatus());
                ticketMapper.updateById(updateObj);
                count++;
                // 3.1 留痕：记录系统自动标记的原因与起算时间（startTime 为空时以创建时间兜底）
                LocalDateTime startTime = ticket.getStartTime() != null
                        ? ticket.getStartTime() : ticket.getCreateTime();
                ticketNoteService.createTicketNote(ticket.getId(),
                        CrmAfterSaleTicketOperateTypeEnum.STATUS_CHANGE.getType(),
                        StrUtil.format("系统自动标记「已超时」：起算时间 {}（开始处理时间，未开始以创建时间兜底）"
                                        + "已超过售后设置的超时预警天数",
                                LocalDateTimeUtil.formatNormal(startTime)),
                        null);
            } catch (Exception ex) {
                log.error("[autoMarkOverdueTickets][工单({}) 超时标记失败]", ticket.getId(), ex);
            }
        }
        return count;
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
     * 状态机：已关闭不可流转；其余按 待处理-处理中-已完成/关闭 顺序流转，支持退回与重开。
     *
     * <p>「已超时」(5) 由系统超时检查任务自动标记，不开放手工设置；
     * 超时工单可完成（必须填写工单记录）、关闭、退回待处理或重开为处理中（重开会刷新开始处理时间）。
     * 完成后的工单不再参与超时标记（扫描范围仅未闭环状态），因此不会出现
     * 「完成 → 被标记超时 → 重开 → 完成」的循环。
     */
    private void validateStatusTransition(Integer currentStatus, Integer targetStatus) {
        if (Objects.equals(currentStatus, targetStatus)
                || Objects.equals(currentStatus, CrmAfterSaleTicketStatusEnum.CLOSED.getStatus())) {
            throw exception(AFTER_SALE_TICKET_UPDATE_STATUS_FAIL_INVALID);
        }
        boolean valid = switch (targetStatus) {
            case 1 -> currentStatus.equals(CrmAfterSaleTicketStatusEnum.PROCESSING.getStatus())
                    || currentStatus.equals(CrmAfterSaleTicketStatusEnum.FINISH.getStatus())
                    || currentStatus.equals(CrmAfterSaleTicketStatusEnum.OVERDUE.getStatus()); // 退回待处理（含超时工单退回）
            case 2 -> currentStatus.equals(CrmAfterSaleTicketStatusEnum.PENDING.getStatus())
                    || currentStatus.equals(CrmAfterSaleTicketStatusEnum.FINISH.getStatus())
                    || currentStatus.equals(CrmAfterSaleTicketStatusEnum.OVERDUE.getStatus()); // 开始/重开（含超时工单恢复处理）
            case 3 -> currentStatus.equals(CrmAfterSaleTicketStatusEnum.PENDING.getStatus())
                    || currentStatus.equals(CrmAfterSaleTicketStatusEnum.PROCESSING.getStatus())
                    || currentStatus.equals(CrmAfterSaleTicketStatusEnum.OVERDUE.getStatus()); // 完成（含超时工单完成，需填写工单记录）
            case 4 -> true; // 关闭：任何非关闭状态
            case 5 -> false; // 已超时：仅由系统超时检查任务自动标记，不开放手工设置
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
