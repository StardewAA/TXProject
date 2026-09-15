package cn.iocoder.txgy.module.bidding.enums;

/**
 * 证件/资质生命周期类型（cert_lifecycle_record.lifecycle_type 的 code 取值）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 3（资质审查 review_type，含 ANNUAL 年检）、
 * 表 5（cert_renewal_record 续期/补换证）、表 6（cert_change_record 变更）、表 7（cert_revoke_record 吊销/注销）；
 * 用户范围口径（2026-09-13 确认）：做到"证照全生命周期管理"——变更、注销、年检、到期提醒、版本管理，
 * 签发流程不做。
 * <p>为什么这样写：四类流程在业务上共用"申请 → 审核 → 生效 → 回写主档"骨架，差异只在回写字段，
 * 故用同一张表 + 本枚举区分（理由与差异已登记在 sql/2026-09-14-cert-lifecycle.sql 头部与设计说明文档）。
 * <p>业务目标：每一次证件状态变化都有据可查，形成"谁在什么时候、依据什么、把哪张证改成了什么"的完整证据链。
 */
public enum CertLifecycleTypeEnum {

    /** 变更：主体/名称/等级/范围/有效期等字段变更（字段级留痕） */
    CHANGE,
    /** 续期/补换证：有效期延续，办结回写主档并驱动下一轮预警 */
    RENEW,
    /** 注销/吊销/暂扣/恢复：证照失效与恢复 */
    REVOKE,
    /** 年检/年度审查：更新下次年检日与年检结论 */
    ANNUAL
}
