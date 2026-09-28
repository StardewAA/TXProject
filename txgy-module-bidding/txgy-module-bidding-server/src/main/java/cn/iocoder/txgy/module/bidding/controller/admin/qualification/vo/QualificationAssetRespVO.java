package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 企业资产/设备行（要素台账 · 资产）。
 *
 * <p>规范出处：契约 §4 G5；表 15-5 qualification_asset。
 * <p>为什么这样写：{@code statYear} 单独返回——资产类要素是按统计年度取值的（净资产以年报为准），
 * 不显示年度会让"净资产 8000 万"无法判断是哪一年的口径，达标核查结论也就无法复算。
 * <p>业务目标：资产要素可按年度核对，避免口径混淆。
 */
@Data
public class QualificationAssetRespVO {

    @Schema(description = "记录 id", example = "7201")
    private Long id;

    @Schema(description = "企业主体 id", example = "1")
    private Long entId;

    @Schema(description = "资产类型（如：净资产 / 施工设备 / 检测设备）", example = "净资产")
    private String assetType;

    @Schema(description = "名称/型号", example = "沥青摊铺机")
    private String name;

    @Schema(description = "规格", example = "9m")
    private String spec;

    @Schema(description = "数量", example = "2")
    private BigDecimal quantity;

    @Schema(description = "金额/原值（元）", example = "8000000.00")
    private BigDecimal valueAmount;

    @Schema(description = "统计年度（资产按年报口径）", example = "2025")
    private Short statYear;

    @Schema(description = "佐证材料文件 id（JSON 数组字符串）", example = "[201]")
    private String evidenceFileIds;
}
