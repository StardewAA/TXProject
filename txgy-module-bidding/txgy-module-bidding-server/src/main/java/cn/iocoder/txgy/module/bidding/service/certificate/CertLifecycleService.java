package cn.iocoder.txgy.module.bidding.service.certificate;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertLifecycleApplyReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertLifecyclePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertLifecycleRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.CertLifecycleRecordDO;

import java.util.List;

/**
 * 证件/资质全生命周期服务（变更 / 续期 / 注销吊销 / 年检 —— 含"生效回写主档"与版本管理）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 3（审查，含年检 ANNUAL）、表 5（续期/补换证）、
 * 表 6（变更：change_group_id 归组、字段级 old/new 留痕）、表 7（吊销/注销/暂扣/恢复）；
 * 用户范围口径（2026-09-13 确认）：做到"证照全生命周期管理（中级）"——变更、注销、年检、到期提醒、
 * 版本管理，**不做签发**；《招投标模块开发规范》§4.5（状态机 + 乐观锁 CAS + 同事务回写）。
 * <p>为什么是"一套流程引擎 + 域适配"：四类流程共用"申请 → 审核 → 生效 → 回写"骨架，差异只在回写哪些字段
 * 与回写哪张主表；因此用 lifecycleType 区分流程、bizDomain 定位主表（CERT/PERSON_CERT/ENTERPRISE_QUAL/
 * SAFETY_PERMIT），把"状态机与留痕"写一次，把"字段回写"收敛到各域适配方法里。
 * <p>为什么这样写：四类流程的差异只在"回写哪些字段、回写哪张主表"，骨架完全一致；做成"一套流程引擎 + 域适配"
 * 可以避免四套近似流程各自演化，把状态口径分叉的风险挡在结构层面。
 * <p>业务目标：每一次变更、换证、注销、年检都有申请单、有审批人、有新旧值对照，生效即自动同步台账——
 * 让"这张证现在是什么状态、为什么是这个状态"永远说得清。
 */
public interface CertLifecycleService {

    /**
     * 发起生命周期流程（初始状态 PENDING）。
     *
     * @return 记录 id（变更流程返回变更组内首行 id）
     */
    Long apply(CertLifecycleApplyReqVO reqVO);

    /** 审核流转：受理/审核中 → 通过 / 驳回（非法流转抛错，终态不可再流转）。 */
    void audit(Long id, String targetStatus, Integer version, String remark);

    /** 生效：回写主档 + 记录生效时间（变更流程按组整体生效，含多字段）。 */
    void effect(Long id, Integer version, String remark);

    /** 记录详情。 */
    CertLifecycleRecordDO getRecord(Long id);

    /** 分页检索（类型/状态/业务对象）。 */
    PageResult<CertLifecycleRecordDO> pageRecord(CertLifecyclePageReqVO reqVO);

    /** 某业务对象的完整生命周期轨迹（按申请时间倒序）。 */
    List<CertLifecycleRecordDO> listByBiz(String bizDomain, Long bizId);

    /**
     * DO → 出参 VO（含存储形态转换：evidence_file_ids 的 JSON 串 ↔ List&lt;Long&gt;）。
     *
     * <p>规范出处：《招投标模块开发规范》§二（"自建 VO ↔ 全量 VO 转换都在 Service"、
     * Controller "只做参数校验、权限注解、Swagger，不写业务"）；§三-1（VO 用常规类型含 List）。
     * <p>为什么放在 Service 而不是 Controller：JSON 串 ↔ List 的转换是**存储形态与接口形态的边界**，
     * 放 Controller 会让每个读接口各自实现一遍解析，迟早出现"有的接口返回数组、有的返回字符串"。
     */
    CertLifecycleRespVO toRespVO(CertLifecycleRecordDO record);
}
