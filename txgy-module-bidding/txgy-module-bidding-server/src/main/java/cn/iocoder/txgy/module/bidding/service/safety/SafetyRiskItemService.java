package cn.iocoder.txgy.module.bidding.service.safety;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyRiskItemAssignReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyRiskItemAlertBatchReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyRiskItemExportVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyRiskItemHandleReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyRiskItemPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyRiskItemRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyRiskItemStatsRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationSafetyPermitDO;

/**
 * 安许风险条目工单 Service（五页改造缺接口契约 Q18/Q19，2026-09-28）。
 *
 * <p>业务目标：风险从"快照结论"升级为"可指派/可处理/可统计的工单"，闭环管理；
 * 大盘（high/medium/low/handled/total）支撑安许风险联动页首页概览（Q19）。
 */
public interface SafetyRiskItemService {

    /** 分页查询工单（Q18：riskLevel/status/assigneeId/keyword 过滤；出参含 entName 解析）。 */
    PageResult<SafetyRiskItemRespVO> pageRiskItem(SafetyRiskItemPageReqVO reqVO);

    /** 工单详情（Q18 GET /risk/get?id=）。 */
    SafetyRiskItemRespVO getRiskItem(Long id);

    /**
     * 指派责任人（Q18 POST /risk/assign）：PENDING → ASSIGNED；终态（HANDLED/IGNORED）不可指派。
     * 入参 version 可选：传了做 CAS（防基于过期视图指派），不传只靠 @Version 拦并发。
     */
    void assign(SafetyRiskItemAssignReqVO reqVO, Long operatorId);

    /**
     * 处理工单（Q18 POST /risk/handle）：按 SafetyRiskItemStatusEnum 迁移表流转
     * （PENDING→ASSIGNED/HANDLING/HANDLED/IGNORED、ASSIGNED→HANDLING/HANDLED/IGNORED、HANDLING→HANDLED/IGNORED），
     * 同值幂等放行、脏码拒绝，乐观锁 CAS；终态回写 handledAt。
     */
    void handle(SafetyRiskItemHandleReqVO reqVO, Long operatorId);

    /** 风险大盘统计（Q19：high/medium/low/handled/total）。 */
    SafetyRiskItemStatsRespVO stats();

    /**
     * 扫描落库后同步工单（Q20 口径：重新扫描复用 /risk/recheck，本方法由 evaluateOne 调起）。
     * 风险非空档时：同 permit 无未闭环工单 → 新建；已有未闭环 → 刷新等级/描述（点亮）。
     * 风险回 NORMAL 时：自动闭环**待处理**工单（PENDING→HANDLED，备注"扫描自动关闭"）；
     * ASSIGNED/HANDLING 已有责任人，留给经办人自行收尾。
     */
    void syncFromEvaluation(QualificationSafetyPermitDO permit, String riskLevel, String flag, String certRef);

    /** 导出风险条目（Q21：范围=当前筛选，xlsx；复用分页过滤口径，不做分页截断）。 */
    java.util.List<SafetyRiskItemExportVO> exportRiskItems(SafetyRiskItemPageReqVO reqVO);

    /** 一键预警（Q22：对所选风险条目批量派发 ALERT 通知；同日同条幂等）。返回实际派发数。 */
    int alertBatch(java.util.List<Long> ids, Long operatorId);
}
