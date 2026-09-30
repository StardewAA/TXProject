package cn.iocoder.txgy.module.bidding.service.safety;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
// [修正] 缺 import（exportRiskItems 使用 SafetyRiskItemExportVO，原代码未编译通过）
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyRiskItemExportVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyRiskItemAssignReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyRiskItemHandleReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyRiskItemPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyRiskItemRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyRiskItemStatsRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.safety.SafetyRiskItemDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationSafetyPermitDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.subject.SubjectProfileDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.safety.SafetyRiskItemMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.subject.SubjectProfileMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.SafetyRiskFlagEnum;
import cn.iocoder.txgy.module.bidding.enums.SafetyRiskItemStatusEnum;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// [修正] 缺 import（alertBatch 使用 LocalDate，原代码未编译通过）
import java.time.LocalDate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 安许风险条目工单 Service 实现（五页改造缺接口契约 Q18/Q19，2026-09-28）。
 *
 * <p>规范出处：Q18（工单分页/详情/指派/处理）+ Q19（大盘统计）；Q20 口径：重新扫描复用 /risk/recheck，
 * evaluateOne 落库后调 syncFromEvaluation 生成/点亮/闭环工单。
 * <p>设计遵循：《招投标模块开发规范》§4.5 写操作走乐观锁 CAS（handle 校验 version）；
 * assigneeName/entName 落库快照 + 出参批量解析（展示免联查，改名不影响历史）。
 */
@Slf4j
@Service
public class SafetyRiskItemServiceImpl implements SafetyRiskItemService {

    @Resource
    private SafetyRiskItemMapper safetyRiskItemMapper;
    @Resource
    private SubjectProfileMapper subjectProfileMapper;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertNotifyOutboxMapper certNotifyOutboxMapper;

    @Override
    public PageResult<SafetyRiskItemRespVO> pageRiskItem(SafetyRiskItemPageReqVO reqVO) {
        Page<SafetyRiskItemDO> page = safetyRiskItemMapper.selectPage(
                new Page<>(reqVO.getPageNo(), reqVO.getPageSize()), buildQuery(reqVO));
        List<SafetyRiskItemRespVO> list = new ArrayList<>();
        for (SafetyRiskItemDO item : page.getRecords()) {
            list.add(toRespVO(item));
        }
        fillNames(list);
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public SafetyRiskItemRespVO getRiskItem(Long id) {
        SafetyRiskItemDO item = validateExists(id);
        SafetyRiskItemRespVO resp = toRespVO(item);
        List<SafetyRiskItemRespVO> one = List.of(resp);
        fillNames(one);
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assign(SafetyRiskItemAssignReqVO reqVO, Long operatorId) {
        SafetyRiskItemDO item = validateExists(reqVO.getId());
        // 终态（已处理/已忽略）不允许再指派；PENDING 指派后进入 ASSIGNED（前端 risk_item_status 五态之一）。
        if (SafetyRiskItemStatusEnum.isTerminal(item.getStatus())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SAFETY_RISK_ITEM_STATUS_INVALID);
        }
        // 前端（RiskAssignReq）会回传 version：传了就做 CAS，避免"我看到的是旧责任人/旧状态"被覆盖；
        // 不传则只依赖 @Version 拦并发（向后兼容早期不带 version 的调用）。
        if (reqVO.getVersion() != null && !Objects.equals(reqVO.getVersion(), item.getVersion())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SAFETY_RISK_ITEM_DATA_MODIFIED);
        }
        item.setAssigneeId(reqVO.getAssigneeId());
        item.setAssigneeName(resolveUserName(reqVO.getAssigneeId()));
        item.setHandleRemark(StrUtil.isBlank(reqVO.getRemark()) ? item.getHandleRemark() : reqVO.getRemark());
        if (SafetyRiskItemStatusEnum.PENDING.name().equals(item.getStatus())) {
            item.setStatus(SafetyRiskItemStatusEnum.ASSIGNED.name());
        }
        // assign 改责任人与状态（版本号由 @Version 自动 +1，供后续 handle 做 CAS）
        if (safetyRiskItemMapper.updateById(item) == 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SAFETY_RISK_ITEM_DATA_MODIFIED);
        }
        log.info("[safety-risk-item][assign] id={} assignee={} operator={}",
                item.getId(), reqVO.getAssigneeId(), operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(SafetyRiskItemHandleReqVO reqVO, Long operatorId) {
        SafetyRiskItemDO item = validateExists(reqVO.getId());
        if (!Objects.equals(reqVO.getVersion(), item.getVersion())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SAFETY_RISK_ITEM_DATA_MODIFIED);
        }
        String from = item.getStatus();
        String to = reqVO.getStatus();
        // 流转合法：PENDING→ASSIGNED/HANDLING/HANDLED/IGNORED、ASSIGNED→HANDLING/HANDLED/IGNORED、
        // HANDLING→HANDLED/IGNORED；HANDLED/IGNORED 终态不可回退。非法码值一律拒绝（防脏码落库）。
        if (!SafetyRiskItemStatusEnum.isKnown(to) || !SafetyRiskItemStatusEnum.canTransit(from, to)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SAFETY_RISK_ITEM_STATUS_INVALID);
        }
        item.setStatus(to);
        if (StrUtil.isNotBlank(reqVO.getRemark())) {
            item.setHandleRemark(reqVO.getRemark());
        }
        if (SafetyRiskItemStatusEnum.isTerminal(to)) {
            item.setHandledAt(LocalDateTime.now());
        }
        if (safetyRiskItemMapper.updateById(item) == 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SAFETY_RISK_ITEM_DATA_MODIFIED);
        }
        log.info("[safety-risk-item][handle] id={} {} → {} operator={}",
                item.getId(), from, to, operatorId);
    }

    @Override
    public SafetyRiskItemStatsRespVO stats() {
        SafetyRiskItemStatsRespVO resp = new SafetyRiskItemStatsRespVO();
        // Q19 口径：high=HIGH+MAJOR；medium=MEDIUM；low=LOW；handled=HANDLED；total=全部未删除
        resp.setHigh(countBy(w -> w.in(SafetyRiskItemDO::getRiskLevel, "HIGH", "MAJOR")));
        resp.setMedium(countBy(w -> w.eq(SafetyRiskItemDO::getRiskLevel, "MEDIUM")));
        resp.setLow(countBy(w -> w.eq(SafetyRiskItemDO::getRiskLevel, "LOW")));
        resp.setHandled(countBy(w -> w.eq(SafetyRiskItemDO::getStatus, SafetyRiskItemStatusEnum.HANDLED.name())));
        resp.setTotal(countBy(w -> w));
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncFromEvaluation(QualificationSafetyPermitDO permit, String riskLevel, String flag, String certRef) {
        if (permit == null) {
            return;
        }
        // 风险回正常：自动闭环"待处理"（PENDING，无人认领）工单；
        // ASSIGNED/HANDLING 已有责任人，留给经办人自行收尾（避免扫描替人关单）
        if (SafetyRiskFlagEnum.NORMAL.name().equals(flag) || StrUtil.isBlank(riskLevel)) {
            List<SafetyRiskItemDO> open = safetyRiskItemMapper.selectList(
                    new LambdaQueryWrapper<SafetyRiskItemDO>()
                            .eq(SafetyRiskItemDO::getPermitId, permit.getId())
                            .eq(SafetyRiskItemDO::getStatus, SafetyRiskItemStatusEnum.PENDING.name()));
            for (SafetyRiskItemDO item : open) {
                item.setStatus(SafetyRiskItemStatusEnum.HANDLED.name());
                item.setHandledAt(LocalDateTime.now());
                item.setHandleRemark("扫描确认风险解除，自动关闭");
                safetyRiskItemMapper.updateById(item);
            }
            if (!open.isEmpty()) {
                log.info("[safety-risk-item][sync] permitId={} 风险解除，自动闭环 {} 单", permit.getId(), open.size());
            }
            return;
        }
        // 风险非空档：无未闭环工单 → 新建；已有 → 刷新等级/描述（点亮）
        List<SafetyRiskItemDO> open = safetyRiskItemMapper.selectOpenByPermit(permit.getId());
        String desc = buildRiskDesc(flag, riskLevel);
        if (open.isEmpty()) {
            SafetyRiskItemDO item = new SafetyRiskItemDO();
            item.setEntId(permit.getEntId());
            item.setPermitId(permit.getId());
            item.setRiskLevel(riskLevel);
            item.setRiskDesc(desc);
            item.setCertRef(certRef);
            item.setStatus(SafetyRiskItemStatusEnum.PENDING.name());
            item.setFoundAt(LocalDateTime.now());
            item.setVersion(0);
            safetyRiskItemMapper.insert(item);
            log.info("[safety-risk-item][sync] permitId={} 新开工单 level={}", permit.getId(), riskLevel);
        } else {
            SafetyRiskItemDO item = open.get(0);
            item.setRiskLevel(riskLevel);
            item.setRiskDesc(desc);
            item.setCertRef(certRef);
            safetyRiskItemMapper.updateById(item);
        }
    }

    /** 分页/导出共用的过滤条件（Q21 导出范围=当前筛选，与 page 同口径）。 */
    private LambdaQueryWrapper<SafetyRiskItemDO> buildQuery(SafetyRiskItemPageReqVO reqVO) {
        return new LambdaQueryWrapper<SafetyRiskItemDO>()
                .eq(reqVO.getEntId() != null, SafetyRiskItemDO::getEntId, reqVO.getEntId())
                .eq(StrUtil.isNotBlank(reqVO.getRiskLevel()), SafetyRiskItemDO::getRiskLevel, reqVO.getRiskLevel())
                .eq(StrUtil.isNotBlank(reqVO.getStatus()), SafetyRiskItemDO::getStatus, reqVO.getStatus())
                .eq(reqVO.getAssigneeId() != null, SafetyRiskItemDO::getAssigneeId, reqVO.getAssigneeId())
                .and(StrUtil.isNotBlank(reqVO.getKeyword()), w -> w
                        .like(SafetyRiskItemDO::getRiskDesc, reqVO.getKeyword())
                        .or().like(SafetyRiskItemDO::getCertRef, reqVO.getKeyword()))
                .orderByDesc(SafetyRiskItemDO::getFoundAt)
                .orderByDesc(SafetyRiskItemDO::getId);
    }

    @Override
    public List<SafetyRiskItemExportVO> exportRiskItems(SafetyRiskItemPageReqVO reqVO) {
        // 规范出处：契约 Q21（2026-09-28）——导出范围=当前筛选；不做分页截断（导出即全量）。
        List<SafetyRiskItemDO> rows = safetyRiskItemMapper.selectList(buildQuery(reqVO));
        List<SafetyRiskItemRespVO> list = new ArrayList<>();
        for (SafetyRiskItemDO row : rows) {
            list.add(toRespVO(row));
        }
        fillNames(list);
        List<SafetyRiskItemExportVO> result = new ArrayList<>();
        for (SafetyRiskItemRespVO vo : list) {
            SafetyRiskItemExportVO out = new SafetyRiskItemExportVO();
            out.setRiskLevel(vo.getRiskLevel());
            out.setRiskDesc(vo.getRiskDesc());
            out.setCertRef(vo.getCertRef());
            out.setEntName(vo.getEntName());
            out.setAssigneeName(vo.getAssigneeName());
            out.setStatus(vo.getStatus());
            out.setFoundAt(vo.getFoundAt());
            out.setHandledAt(vo.getHandledAt());
            result.add(out);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int alertBatch(List<Long> ids, Long operatorId) {
        // 规范出处：契约 Q22（2026-09-28）——一键预警：对所选风险条目批量派发 ALERT 通知；
        // 幂等口径：同日同条不重复推送（与 Q15 催办/Q12 到期提醒一致）。
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        int sent = 0;
        for (Long id : ids) {
            SafetyRiskItemDO item = safetyRiskItemMapper.selectById(id);
            if (item == null) {
                continue; // 选中了已删除/不存在的条目：跳过，不阻塞其余
            }
            Long count = certNotifyOutboxMapper.selectCount(new LambdaQueryWrapper<cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertNotifyOutboxDO>()
                    .eq(cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertNotifyOutboxDO::getBizType,
                            cn.iocoder.txgy.module.bidding.enums.CertNotifyBizTypeEnum.ALERT.name())
                    .eq(cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertNotifyOutboxDO::getBizId, id)
                    .ge(cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertNotifyOutboxDO::getCreateTime, todayStart));
            if (count != null && count > 0) {
                continue; // 同日已预警，幂等跳过
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("bizType", cn.iocoder.txgy.module.bidding.enums.CertNotifyBizTypeEnum.ALERT.name());
            payload.put("bizId", id);
            payload.put("riskLevel", item.getRiskLevel());
            payload.put("riskDesc", item.getRiskDesc());
            payload.put("certRef", item.getCertRef());
            payload.put("entId", item.getEntId());
            payload.put("alertedBy", operatorId);
            payload.put("alertedAt", LocalDateTime.now().toString());
            cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertNotifyOutboxDO outbox =
                    new cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertNotifyOutboxDO();
            outbox.setBizType(cn.iocoder.txgy.module.bidding.enums.CertNotifyBizTypeEnum.ALERT.name());
            outbox.setBizId(id);
            outbox.setChannel("IN_APP");
            outbox.setReceiver("TENANT_ADMIN");
            outbox.setPayload(cn.iocoder.txgy.framework.common.util.json.JsonUtils.toJsonString(payload));
            outbox.setSendStatus(cn.iocoder.txgy.module.bidding.enums.CertNotifySendStatusEnum.PENDING.name());
            outbox.setRetryCount(0);
            outbox.setMaxRetry(5);
            certNotifyOutboxMapper.insert(outbox);
            sent++;
        }
        log.info("[safety-risk-item][alert-batch] 派发 {} 条预警，operator={}", sent, operatorId);
        return sent;
    }
    /** 存在性校验（Q18 公共前提；不存在抛 NOT_EXISTS）。 */
    private SafetyRiskItemDO validateExists(Long id) {
        SafetyRiskItemDO item = safetyRiskItemMapper.selectById(id);
        if (item == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SAFETY_RISK_ITEM_NOT_EXISTS);
        }
        return item;
    }

    /** DO → 出参（快照字段直接透传，名称留待 fillNames 批量解析）。 */
    private SafetyRiskItemRespVO toRespVO(SafetyRiskItemDO item) {
        SafetyRiskItemRespVO resp = new SafetyRiskItemRespVO();
        resp.setId(item.getId());
        resp.setEntId(item.getEntId());
        resp.setPermitId(item.getPermitId());
        resp.setRiskLevel(item.getRiskLevel());
        resp.setRiskDesc(item.getRiskDesc());
        resp.setCertRef(item.getCertRef());
        resp.setAssigneeId(item.getAssigneeId());
        resp.setAssigneeName(item.getAssigneeName());
        resp.setStatus(item.getStatus());
        resp.setFoundAt(item.getFoundAt());
        resp.setHandledAt(item.getHandledAt());
        resp.setHandleRemark(item.getHandleRemark());
        // [契约 e2e P1 方案A 2026-10-02] 前端逐字字段别名：remark=handleRemark；riskType 由来源推导
        resp.setRemark(item.getHandleRemark());
        resp.setRiskType(item.getPermitId() == null ? null : "SAFETY_PERMIT");
        resp.setVersion(item.getVersion());
        return resp;
    }

    /** 批量补 entName（主体档案）与 assigneeName（用户昵称）；解析失败仅留空，不影响列表。 */
    private void fillNames(List<SafetyRiskItemRespVO> list) {
        List<Long> entIds = list.stream().map(SafetyRiskItemRespVO::getEntId).filter(Objects::nonNull).distinct().toList();
        if (!entIds.isEmpty()) {
            Map<Long, String> entNames = new HashMap<>();
            for (SubjectProfileDO profile : subjectProfileMapper.selectBatchIds(entIds)) {
                entNames.put(profile.getId(), StrUtil.nullToEmpty(profile.getSubjectName()));
            }
            list.forEach(resp -> resp.setEntName(entNames.getOrDefault(resp.getEntId(), "")));
        }
        List<Long> assigneeIds = list.stream().map(SafetyRiskItemRespVO::getAssigneeId)
                .filter(Objects::nonNull).distinct().toList();
        if (assigneeIds.isEmpty()) {
            return;
        }
        try {
            Map<Long, String> nameMap = new HashMap<>();
            for (AdminUserRespDTO user : adminUserApi.getUserList(assigneeIds).getCheckedData()) {
                nameMap.put(user.getId(), StrUtil.nullToEmpty(user.getNickname()));
            }
            list.forEach(resp -> {
                if (resp.getAssigneeId() != null) {
                    resp.setAssigneeName(nameMap.getOrDefault(resp.getAssigneeId(), resp.getAssigneeName()));
                }
            });
        } catch (Exception e) {
            log.warn("[safety-risk-item][fillNames] 责任人昵称解析失败，assigneeIds={}", assigneeIds, e);
        }
    }

    /** 单责任人昵称解析（指派时落快照）；用户服务不可用留空。 */
    private String resolveUserName(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            for (AdminUserRespDTO user : adminUserApi.getUserList(List.of(userId)).getCheckedData()) {
                if (Objects.equals(user.getId(), userId)) {
                    return StrUtil.nullToEmpty(user.getNickname());
                }
            }
        } catch (Exception e) {
            log.warn("[safety-risk-item][resolveUserName] 解析失败 userId={}", userId, e);
        }
        return null;
    }

    /** 统计辅助：按条件计数（Tenant 拦截器自动拼 tenant_id，deleted=0 自动过滤）。 */
    private Long countBy(java.util.function.Function<LambdaQueryWrapper<SafetyRiskItemDO>, LambdaQueryWrapper<SafetyRiskItemDO>> extra) {
        return safetyRiskItemMapper.selectCount(extra.apply(new LambdaQueryWrapper<SafetyRiskItemDO>()));
    }

    /** 风险描述拼接（人话可读：档位 + 等级）。 */
    private String buildRiskDesc(String flag, String riskLevel) {
        return "安许风险档位 " + StrUtil.nullToDefault(flag, "")
                + "（等级 " + StrUtil.nullToDefault(riskLevel, "") + "）";
    }
}
