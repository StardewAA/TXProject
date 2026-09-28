package cn.iocoder.txgy.module.bidding.service.flow;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.common.util.servlet.ServletUtils;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.bidding.controller.admin.flow.vo.FlowLogRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.flow.vo.FlowNextActionRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.flow.vo.FlowTransitionReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.flow.vo.FlowTransitionRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.flow.FlowLogDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.project.BidSectionDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.project.ProjectDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.project.TenderProjectDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.flow.FlowLogMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.project.BidSectionMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.project.ProjectMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.project.TenderProjectMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.flow.BidSectionStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.flow.FlowBizTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.flow.FlowTransitionEventEnum;
import cn.iocoder.txgy.module.bidding.enums.flow.ProjectStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.flow.TenderStatusEnum;
import cn.iocoder.txgy.module.bidding.infra.config.BiddingFlowProperties;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class FlowTransitionServiceImpl implements FlowTransitionService {

    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private TenderProjectMapper tenderProjectMapper;
    @Resource
    private BidSectionMapper bidSectionMapper;
    @Resource
    private FlowLogMapper flowLogMapper;
    @Resource
    private BiddingFlowProperties flowProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowTransitionRespVO transition(FlowTransitionReqVO reqVO) {
        return doTransition(reqVO, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowTransitionRespVO transitionByTimer(FlowTransitionReqVO reqVO) {
        return doTransition(reqVO, true);
    }

    /**
     * 状态流转核心：systemTriggered=true 表示由调度器（时间驱动）触发，跳过 manual-advance 开关拦截，
     * 但状态机校验（事件映射 + canTransit 双保险）与留痕照常执行。
     */
    private FlowTransitionRespVO doTransition(FlowTransitionReqVO reqVO, boolean systemTriggered) {
        FlowBizTypeEnum bizType = FlowBizTypeEnum.of(reqVO.getBizType());
        if (bizType == null || bizType == FlowBizTypeEnum.ANNOUNCEMENT) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.FLOW_BIZ_TYPE_NOT_SUPPORTED, reqVO.getBizType());
        }
        // 时间轴事件（投标开始/截止、公示过期）：手动入口受 manual-advance-enabled 开关约束；调度器入口不受限
        FlowTransitionEventEnum eventEnum = FlowTransitionEventEnum.of(reqVO.getEvent());
        if (!systemTriggered && eventEnum != null && eventEnum.isTimeDriven() && !flowProperties.isManualAdvanceEnabled()) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.FLOW_MANUAL_ADVANCE_DISABLED, reqVO.getEvent());
        }
        String fromState = null;
        String toState = null;
        switch (bizType) {
            case PROJECT: {
                ProjectDO entity = requireProject(reqVO.getBizCode());
                fromState = entity.getState();
                toState = resolveTarget(FlowBizTypeEnum.PROJECT.name(), fromState, reqVO.getEvent());
                assertEvent(bizType, fromState, reqVO.getEvent(), toState);
                entity.setState(toState);
                projectMapper.updateById(entity);
                break;
            }
            case TENDER_PROJECT: {
                TenderProjectDO entity = requireTender(reqVO.getBizCode());
                fromState = entity.getState();
                toState = resolveTarget(FlowBizTypeEnum.TENDER_PROJECT.name(), fromState, reqVO.getEvent());
                assertEvent(bizType, fromState, reqVO.getEvent(), toState);
                entity.setState(toState);
                tenderProjectMapper.updateById(entity);
                // 公告发布联动：招标公告发布（→ANNOUNCED）时，下属 DRAFT 标段自动进入招标（BIDDING）
                if ("ANNOUNCED".equals(toState)) {
                    autoAdvanceSections(entity.getTenderProjectCode(), reqVO);
                }
                // 项目完结联动：招标项目归档/终止后，若该项目下属招标项目全部终态且项目在 ACTIVE，自动完结
                if ("COMPLETED".equals(toState) || "TERMINATED".equals(toState)) {
                    checkAndCompleteProject(entity.getProjectCode(), reqVO);
                }
                break;
            }
            case BID_SECTION: {
                BidSectionDO entity = requireSection(reqVO.getBizCode());
                fromState = entity.getState();
                toState = resolveTarget(FlowBizTypeEnum.BID_SECTION.name(), fromState, reqVO.getEvent());
                assertEvent(bizType, fromState, reqVO.getEvent(), toState);
                entity.setState(toState);
                bidSectionMapper.updateById(entity);
                break;
            }
            default:
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.FLOW_BIZ_TYPE_NOT_SUPPORTED, reqVO.getBizType());
        }
        saveLog(bizType, reqVO.getBizCode(), reqVO.getEvent(), fromState, toState, reqVO.getRemark());
        FlowTransitionRespVO resp = new FlowTransitionRespVO();
        resp.setBizType(bizType.name());
        resp.setBizCode(reqVO.getBizCode());
        resp.setFromState(fromState);
        resp.setToState(toState);
        resp.setEvent(reqVO.getEvent());
        return resp;
    }

    @Override
    public List<FlowNextActionRespVO> nextActions(String bizType, String bizCode) {
        FlowBizTypeEnum type = FlowBizTypeEnum.of(bizType);
        if (type == null || type == FlowBizTypeEnum.ANNOUNCEMENT) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.FLOW_BIZ_TYPE_NOT_SUPPORTED, bizType);
        }
        String from = requireState(type, bizCode);
        boolean manualAdvance = flowProperties.isManualAdvanceEnabled();
        List<FlowNextActionRespVO> actions = new ArrayList<>();
        for (FlowTransitionEventEnum event : FlowTransitionEventEnum.values()) {
            // 手动流转关闭时，时间轴事件不出现在「下一步」候选（前端按钮自然不渲染）
            if (event.isTimeDriven() && !manualAdvance) {
                continue;
            }
            String to = resolveTarget(type.name(), from, event.name());
            if (to != null && canTransit(type, from, to)) {
                FlowNextActionRespVO vo = new FlowNextActionRespVO();
                vo.setEvent(event.name());
                vo.setLabel(event.getLabel());
                vo.setToState(to);
                actions.add(vo);
            }
        }
        return actions;
    }

    @Override
    public List<FlowLogRespVO> logs(String bizType, String bizCode) {
        List<FlowLogDO> list = flowLogMapper.selectListByBiz(bizType, bizCode);
        return BeanUtils.toBean(list, FlowLogRespVO.class);
    }

    // ==================== 事件 → 目标状态映射（纯函数，供单测） ====================

    /**
     * 按（实体类型, 当前状态, 事件）解析目标状态；不合法返回 null。
     * 映射与各状态枚举 canTransit 保持一致（双保险）。
     */
    public static String resolveTarget(String bizType, String fromState, String event) {
        if (bizType == null || fromState == null || event == null) {
            return null;
        }
        if (FlowBizTypeEnum.PROJECT.name().equals(bizType)) {
            return resolveProjectTarget(fromState, event);
        }
        if (FlowBizTypeEnum.TENDER_PROJECT.name().equals(bizType)) {
            return resolveTenderTarget(fromState, event);
        }
        if (FlowBizTypeEnum.BID_SECTION.name().equals(bizType)) {
            return resolveSectionTarget(fromState, event);
        }
        return null;
    }

    private static String resolveProjectTarget(String from, String event) {
        // 任意非终态可取消
        if ("CANCEL".equals(event) && !"COMPLETED".equals(from) && !"CANCELLED".equals(from)) {
            return "CANCELLED";
        }
        switch (from) {
            case "DRAFT":
                return "SUBMIT_APPROVAL".equals(event) ? "APPROVING" : null;
            case "APPROVING":
                if ("APPROVE_PASS".equals(event)) return "ACTIVE";
                if ("APPROVE_REJECT".equals(event)) return "DRAFT";
                return null;
            case "ACTIVE":
                // 项目完结：所有下属招标项目均已归档（测试模式手动触发；PROD 由联动判定）
                return "CONTRACT_SIGNED".equals(event) ? "COMPLETED" : null;
            default:
                return null;
        }
    }

    private static String resolveTenderTarget(String from, String event) {
        // 任意非终态可终止（招标方主动终止/全部流标）；已归档不可终止
        if ("TERMINATE".equals(event) && !"COMPLETED".equals(from) && !"TERMINATED".equals(from)) {
            return "TERMINATED";
        }
        switch (from) {
            case "DRAFT":
                return "SUBMIT_APPROVAL".equals(event) ? "APPROVING" : null;
            case "APPROVING":
                if ("APPROVE_PASS".equals(event)) return "PREPARING";
                if ("APPROVE_REJECT".equals(event)) return "DRAFT";
                return null;
            case "PREPARING":
                return "PUBLISH_TENDER_NOTICE".equals(event) ? "ANNOUNCED" : null;
            case "ANNOUNCED":
                return "BIDDING_STARTED".equals(event) ? "BIDDING" : null;
            case "BIDDING":
                return "BIDDING_ENDED".equals(event) ? "PENDING_OPEN" : null;
            case "PENDING_OPEN":
                return "BID_OPENED".equals(event) ? "EVALUATING" : null;
            case "EVALUATING":
                return "EVALUATION_FINISHED".equals(event) ? "PUBLICIZING" : null;
            case "PUBLICIZING":
                if ("PUBLISH_RESULT_NOTICE".equals(event)) return "AWARDED";
                if ("OBJECTION_RECONSIDER".equals(event)) return "EVALUATING";
                return null;
            case "AWARDED":
                return "ISSUE_AWARD_NOTICE".equals(event) ? "CONTRACTING" : null;
            case "CONTRACTING":
                return "CONTRACT_SIGNED".equals(event) ? "COMPLETED" : null;
            default:
                return null;
        }
    }

    private static String resolveSectionTarget(String from, String event) {
        // 流标：投标不足 3 家或全部废标（BIDDING/EVALUATING 可 FAILED）
        if ("TERMINATE".equals(event) && ("BIDDING".equals(from) || "EVALUATING".equals(from))) {
            return "FAILED";
        }
        switch (from) {
            case "DRAFT":
                // 随招标公告发布进入招标
                return "PUBLISH_TENDER_NOTICE".equals(event) ? "BIDDING" : null;
            case "BIDDING":
                return "BID_OPENED".equals(event) ? "EVALUATING" : null;
            case "EVALUATING":
                return "PUBLISH_RESULT_NOTICE".equals(event) ? "AWARDED" : null;
            case "AWARDED":
                return "CONTRACT_SIGNED".equals(event) ? "COMPLETED" : null;
            default:
                return null;
        }
    }

    private static boolean canTransit(FlowBizTypeEnum type, String from, String to) {
        switch (type) {
            case PROJECT:
                return ProjectStatusEnum.canTransit(ProjectStatusEnum.of(from), ProjectStatusEnum.of(to));
            case TENDER_PROJECT:
                return TenderStatusEnum.canTransit(TenderStatusEnum.of(from), TenderStatusEnum.of(to));
            case BID_SECTION:
                return BidSectionStatusEnum.canTransit(BidSectionStatusEnum.of(from), BidSectionStatusEnum.of(to));
            default:
                return false;
        }
    }

    private void assertEvent(FlowBizTypeEnum type, String from, String event, String to) {
        if (to == null || !canTransit(type, from, to)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.FLOW_EVENT_INVALID, event, from);
        }
    }

    // ==================== 实体定位 ====================

    private ProjectDO requireProject(String bizCode) {
        ProjectDO entity = projectMapper.selectOne(new LambdaQueryWrapperX<ProjectDO>()
                .eq(ProjectDO::getProjectCode, bizCode).last("LIMIT 1"));
        if (entity == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.FLOW_ENTITY_NOT_FOUND, "PROJECT", bizCode);
        }
        return entity;
    }

    private TenderProjectDO requireTender(String bizCode) {
        TenderProjectDO entity = tenderProjectMapper.selectOne(new LambdaQueryWrapperX<TenderProjectDO>()
                .eq(TenderProjectDO::getTenderProjectCode, bizCode).last("LIMIT 1"));
        if (entity == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.FLOW_ENTITY_NOT_FOUND, "TENDER_PROJECT", bizCode);
        }
        return entity;
    }

    private BidSectionDO requireSection(String bizCode) {
        BidSectionDO entity = bidSectionMapper.selectOne(new LambdaQueryWrapperX<BidSectionDO>()
                .eq(BidSectionDO::getBidSectionCode, bizCode).last("LIMIT 1"));
        if (entity == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.FLOW_ENTITY_NOT_FOUND, "BID_SECTION", bizCode);
        }
        return entity;
    }

    @Override
    public String getCurrentState(String bizType, String bizCode) {
        try {
            FlowBizTypeEnum type = FlowBizTypeEnum.of(bizType);
            if (type == null) {
                return null;
            }
            return requireState(type, bizCode);
        } catch (Exception ex) {
            // 实体不存在/状态未知 → 返回 null，由调度器按「已过时」SKIP
            return null;
        }
    }

    /**
     * 时间事件是否已过时（调度器 SKIP 判定，纯函数供单测）：
     * 当前状态处于事件前置状态之前（未就绪）→ false（保留 WAITING 继续等）；
     * 当前状态已越过事件目标（或终态/未知）→ true（SKIPPED，不再重试）。
     */
    public static boolean isEventPassed(String event, String currentState) {
        if (currentState == null) {
            return true;
        }
        switch (event) {
            case "BIDDING_STARTED":
                return isTenderStateAfter(currentState, 3); // ANNOUNCED(3) 之后：BIDDING(4) 及以上/终态
            case "BIDDING_ENDED":
                return isTenderStateAfter(currentState, 4); // BIDDING(4) 之后：PENDING_OPEN(5) 及以上/终态
            default:
                return true; // 未知事件不重试
        }
    }

    private static final Map<String, Integer> TENDER_ORDER = Map.ofEntries(
            Map.entry("DRAFT", 0), Map.entry("APPROVING", 1), Map.entry("PREPARING", 2),
            Map.entry("ANNOUNCED", 3), Map.entry("BIDDING", 4), Map.entry("PENDING_OPEN", 5),
            Map.entry("EVALUATING", 6), Map.entry("PUBLICIZING", 7), Map.entry("AWARDED", 8),
            Map.entry("CONTRACTING", 9), Map.entry("COMPLETED", 10));

    private static boolean isTenderStateAfter(String state, int refOrder) {
        if ("TERMINATED".equals(state)) {
            return true;
        }
        Integer order = TENDER_ORDER.get(state);
        return order == null || order > refOrder;
    }

    private String requireState(FlowBizTypeEnum type, String bizCode) {
        switch (type) {
            case PROJECT:
                return requireProject(bizCode).getState();
            case TENDER_PROJECT:
                return requireTender(bizCode).getState();
            case BID_SECTION:
                return requireSection(bizCode).getState();
            default:
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.FLOW_BIZ_TYPE_NOT_SUPPORTED, type.name());
        }
    }

    // ==================== 实体间联动（大状态） ====================

    /**
     * 公告发布联动：招标公告发布（招标项目 → ANNOUNCED）时，下属 DRAFT 标段自动进入招标（BIDDING）。
     * 事件仍记 PUBLISH_TENDER_NOTICE，remark 标注「随招标公告发布自动联动」，操作人沿用发起者。
     */
    private void autoAdvanceSections(String tenderProjectCode, FlowTransitionReqVO reqVO) {
        List<BidSectionDO> sections = bidSectionMapper.selectList(new LambdaQueryWrapperX<BidSectionDO>()
                .eq(BidSectionDO::getTenderProjectCode, tenderProjectCode)
                .eq(BidSectionDO::getState, "DRAFT"));
        for (BidSectionDO section : sections) {
            String to = resolveTarget(FlowBizTypeEnum.BID_SECTION.name(), "DRAFT", "PUBLISH_TENDER_NOTICE");
            if (to != null && canTransit(FlowBizTypeEnum.BID_SECTION, "DRAFT", to)) {
                section.setState(to);
                bidSectionMapper.updateById(section);
                saveLog(FlowBizTypeEnum.BID_SECTION, section.getBidSectionCode(), "PUBLISH_TENDER_NOTICE",
                        "DRAFT", to, "随招标公告发布自动联动");
            }
        }
    }

    /**
     * 项目完结联动：招标项目进入终态（归档/终止）后，若所属项目全部招标项目已终态且项目在 ACTIVE，
     * 自动推进项目完结（COMPLETED）。留痕事件沿用 CONTRACT_SIGNED（项目完结触发器）。
     */
    private void checkAndCompleteProject(String projectCode, FlowTransitionReqVO reqVO) {
        if (projectCode == null || !allTenderProjectsTerminal(tenderStatesOf(projectCode))) {
            return;
        }
        ProjectDO project = requireProject(projectCode);
        if (!"ACTIVE".equals(project.getState())) {
            return;
        }
        project.setState("COMPLETED");
        projectMapper.updateById(project);
        saveLog(FlowBizTypeEnum.PROJECT, projectCode, "CONTRACT_SIGNED", "ACTIVE", "COMPLETED",
                "全部招标项目归档，项目自动完结");
    }

    private List<String> tenderStatesOf(String projectCode) {
        List<TenderProjectDO> list = tenderProjectMapper.selectList(new LambdaQueryWrapperX<TenderProjectDO>()
                .eq(TenderProjectDO::getProjectCode, projectCode));
        List<String> states = new ArrayList<>();
        for (TenderProjectDO tender : list) {
            states.add(tender.getState());
        }
        return states;
    }

    /**
     * 全部招标项目是否已终态（供单测的纯函数）：非空且全部 COMPLETED/TERMINATED。
     */
    public static boolean allTenderProjectsTerminal(List<String> tenderStates) {
        if (tenderStates == null || tenderStates.isEmpty()) {
            return false;
        }
        for (String state : tenderStates) {
            if (!"COMPLETED".equals(state) && !"TERMINATED".equals(state)) {
                return false;
            }
        }
        return true;
    }

    // ==================== 留痕 ====================

    private void saveLog(FlowBizTypeEnum bizType, String bizCode, String event, String fromState, String toState, String remark) {
        FlowLogDO log = new FlowLogDO();
        log.setBizType(bizType.name());
        log.setBizCode(bizCode);
        log.setFromState(fromState);
        log.setToState(toState);
        log.setEvent(event);
        log.setOperator(currentUser());
        log.setOperatorIp(clientIp());
        log.setRemark(remark);
        flowLogMapper.insert(log);
    }

    private String currentUser() {
        try {
            Long uid = SecurityFrameworkUtils.getLoginUserId();
            return uid == null ? "system" : String.valueOf(uid);
        } catch (Exception e) {
            return "system";
        }
    }

    private String clientIp() {
        try {
            return ServletUtils.getClientIP();
        } catch (Exception e) {
            return "";
        }
    }
}
