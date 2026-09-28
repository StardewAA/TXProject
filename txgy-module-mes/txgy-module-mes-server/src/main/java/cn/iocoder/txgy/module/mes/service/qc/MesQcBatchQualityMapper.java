package cn.iocoder.txgy.module.mes.service.qc;

import cn.iocoder.txgy.module.mes.enums.qc.MesQcCheckResultEnum;
import cn.iocoder.txgy.module.mes.enums.qc.MesQcTypeEnum;
import cn.iocoder.txgy.module.mes.enums.wm.MesWmQualityStatusEnum;

import java.util.Objects;

/**
 * 检验结论 → 库存批次质量状态 映射（R5）
 *
 * <p>检验通过/不通过后，按检验类型把检测结论映射为 {@link MesWmQualityStatusEnum}，
 * 用于 finish 时回写 {@code mes_wm_batch.quality_status}，驱动 R4 质量门禁。
 *
 * <p>映射规则（与 R4 门禁语义一致）：
 * <ul>
 *     <li>IQC（来料）：合格 → 合格(PASS)；不合格 → 隔离(QUARANTINE，待质量判定)</li>
 *     <li>OQC（出货）：合格 → 合格(PASS)；不合格 → 隔离(QUARANTINE，禁止发货)</li>
 *     <li>RQC（退货复检）：合格 → 让步(CONCESSION，复检放行)；不合格 → 冻结(FROZEN，禁止流转)</li>
 * </ul>
 */
public final class MesQcBatchQualityMapper {

    private MesQcBatchQualityMapper() {
    }

    /**
     * 根据检验类型与检测结论解析应回写的库存批次质量状态
     *
     * @param qcType      检验类型（{@link MesQcTypeEnum}）
     * @param checkResult 检测结论（{@link MesQcCheckResultEnum}）
     * @return 库存批次质量状态值（{@link MesWmQualityStatusEnum}）
     */
    public static Integer resolveQualityStatus(MesQcTypeEnum qcType, Integer checkResult) {
        boolean pass = Objects.equals(checkResult, MesQcCheckResultEnum.PASS.getType());
        if (qcType == MesQcTypeEnum.RQC) {
            return pass ? MesWmQualityStatusEnum.CONCESSION.getStatus()
                    : MesWmQualityStatusEnum.FROZEN.getStatus();
        }
        // IQC / OQC：来料与出货，合格放行、不合格隔离待判定
        return pass ? MesWmQualityStatusEnum.PASS.getStatus()
                : MesWmQualityStatusEnum.QUARANTINE.getStatus();
    }

}
