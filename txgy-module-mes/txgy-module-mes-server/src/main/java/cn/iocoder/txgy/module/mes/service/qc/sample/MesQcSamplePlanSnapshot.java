package cn.iocoder.txgy.module.mes.service.qc.sample;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * MES 抽样方案快照 DTO
 *
 * 建单时固化的抽样依据，序列化后保存到 mes_qc_iqc / mes_qc_ipqc 的 sample_plan_snapshot 字段。
 *
 * @author OPENLAB BS
 */
@Schema(description = "MES 抽样方案快照")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MesQcSamplePlanSnapshot {

    @Schema(description = "抽样方式：1=全检 2=固定数量 3=按比例", example = "3")
    private Integer sampleType;

    @Schema(description = "抽样方式名称", example = "按比例")
    private String sampleTypeName;

    @Schema(description = "固定抽样数量（抽样方式=2 时生效）", example = "5")
    private BigDecimal fixedQuantity;

    @Schema(description = "抽样比例 %（抽样方式=3 时生效）", example = "10")
    private BigDecimal ratio;

    @Schema(description = "抽样基数（来源单据数量）", example = "100")
    private BigDecimal baseQuantity;

    @Schema(description = "最终抽样数量（写入检验单 check_quantity）", example = "10")
    private BigDecimal checkQuantity;

    @Schema(description = "检验模板-产品关联 ID（方案来源）", example = "1")
    private Long templateItemId;

    @Schema(description = "快照生成时间（毫秒时间戳）", example = "1758528000000")
    private Long snapshotTime;

}
