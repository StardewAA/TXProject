package cn.iocoder.txgy.module.bidding.dal.mysql.personnel;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.SafetyPermitRiskSnapshotDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 安许风险快照 Mapper。
 *
 * <p>规范出处：《电子资质七大模块技术方案.md》§1.1(c)/§1.5；DDL safety_permit_risk_snapshot。
 * <p>设计遵循/针对：只提供查询与插入（不暴露 update/delete 语义方法），从 Mapper 层杜绝对审计快照的改写；
 * 取链尾用 id 倒序 + LIMIT 1（等价最后一条），命中 idx_risk_permit。
 * <p>业务目标：快照只增不改，风险判定过程可追溯。
 */
@Mapper
public interface SafetyPermitRiskSnapshotMapper extends BaseMapperX<SafetyPermitRiskSnapshotDO> {

    /** 取某安许证最近一次快照（哈希链链尾 + 上一档位来源）。 */
    default SafetyPermitRiskSnapshotDO selectLatest(Long permitId) {
        return selectOne(new LambdaQueryWrapper<SafetyPermitRiskSnapshotDO>()
                .eq(SafetyPermitRiskSnapshotDO::getPermitId, permitId)
                .orderByDesc(SafetyPermitRiskSnapshotDO::getId)
                .last("LIMIT 1"));
    }

    /** 统计窗口内的"预警次数"（非 NORMAL 的快照条数，作为"一年预警 ≥N 次"的判定依据）。 */
    default long countWarnings(Long permitId, java.time.LocalDateTime windowStart) {
        return selectCount(new LambdaQueryWrapper<SafetyPermitRiskSnapshotDO>()
                .eq(SafetyPermitRiskSnapshotDO::getPermitId, permitId)
                .ge(SafetyPermitRiskSnapshotDO::getEvaluatedAt, windowStart)
                .in(SafetyPermitRiskSnapshotDO::getRiskFlag, "YELLOW", "RED", "INVALID", "FREQUENT"));
    }

    /**
     * 取"三类全为 0"首次出现的时刻（用于判断是否已过整改宽限期 → 列失效）。
     *
     * <p>为什么以首次全 0 时刻为基准而不是"当前是否全 0"：失效是"持续不整改"的后果，
     * 只有记住起点才能算出"已经拖了多久"，否则每次评估都会重新计时，永远不会列失效。
     */
    default java.time.LocalDateTime selectFirstAllZeroAt(Long permitId) {
        SafetyPermitRiskSnapshotDO first = selectOne(new LambdaQueryWrapper<SafetyPermitRiskSnapshotDO>()
                .eq(SafetyPermitRiskSnapshotDO::getPermitId, permitId)
                .eq(SafetyPermitRiskSnapshotDO::getACount, 0)
                .eq(SafetyPermitRiskSnapshotDO::getBCount, 0)
                .eq(SafetyPermitRiskSnapshotDO::getCCount, 0)
                .orderByAsc(SafetyPermitRiskSnapshotDO::getEvaluatedAt)
                .last("LIMIT 1"));
        return first == null ? null : first.getEvaluatedAt();
    }

    /** 判断某基准日是否已评估过（Job 幂等：同 permit + 同 asOf 只评估一次）。 */
    default boolean existsEvaluated(Long permitId, java.time.LocalDate asOf) {
        return selectCount(new LambdaQueryWrapper<SafetyPermitRiskSnapshotDO>()
                .eq(SafetyPermitRiskSnapshotDO::getPermitId, permitId)
                .apply("DATE(evaluated_at) = {0}", asOf)) > 0;
    }
}
