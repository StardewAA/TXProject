package cn.iocoder.txgy.module.bidding.enums;

/**
 * 注销/吊销类型（cert_lifecycle_record.revoke_type 的 code 取值）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 7（cert_revoke_record：吊销/注销/暂扣/恢复）；
 * 证照状态口径对齐建办市〔2024〕51号 电子证照状态 ZZZT（注销→CANCELLED、吊销→REVOKED、暂扣→SUSPENDED）。
 * <p>为什么这样写：恢复（RESTORE）也归在本枚举——它在业务上是"吊销流程的逆操作"，
 * 复用同一张记录表与同一个回写入口，主档状态回到 VALID。
 * <p>业务目标：证件失效与恢复都可追溯，避免了资质审查"证件状态为何是吊销"时无人能答。
 */
public enum CertRevokeTypeEnum {

    /** 吊销（主管机关处罚性撤销） */
    REVOKE("REVOKED"),
    /** 注销（企业/个人主动申请注销） */
    CANCEL("CANCELLED"),
    /** 暂扣（限期暂扣，期满可恢复） */
    SUSPEND("SUSPENDED"),
    /** 恢复（暂扣期满或撤销决定被纠正，回到有效） */
    RESTORE("VALID");

    private final String targetStatus;

    CertRevokeTypeEnum(String targetStatus) {
        this.targetStatus = targetStatus;
    }

    /** 生效时回写主档的目标状态 code（证照台账/员工证书共用该状态词表）。 */
    public String getTargetStatus() {
        return targetStatus;
    }
}
