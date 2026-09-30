package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 资质标准分页查询入参（页签 3「标准与要素」）。
 *
 * <p>规范出处：契约《企业资质页-页面级契约-v1.0.md》§4 G4；《电子证件全流程管理_数据库设计.md》
 * 表 15-2 qualification_standard（std_version + 生效区间版本化）；
 * `资质管理/02-法规标准与核验渠道.md` line 37：「做资质类别/等级标准库时**不得静态固化**」
 * （资质改革过渡期，新版《资质标准》尚未全面落地）。
 *
 * <p>为什么这样写：① 全部筛选项可空——标准库是"查依据"的字典式页面，默认看全量；
 * ② onlyEffective 用生效区间判定，而不是把"当前版本"写死在代码或枚举里（改革一换版即改数据不改代码）；
 * ③ 不提供"类别/等级"的固定下拉，前端用 /standard/options 从库里取动态维度。
 *
 * <p>业务目标：达标核查（§3.7）的判定依据可查、可追溯版本，且换版只动数据。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QualificationStandardPageReqVO extends PageParam {

    @Schema(description = "资质序列（如：施工 / 勘察 / 设计 / 监理 / 检测）", example = "施工")
    private String qualSerial;

    @Schema(description = "资质类别（如：市政公用工程施工总承包）", example = "市政公用工程施工总承包")
    private String qualCategory;

    @Schema(description = "资质等级（如：特级 / 一级 / 二级）", example = "一级")
    private String qualGrade;

    @Schema(description = "标准版本号（如 v2026.1）", example = "v2026.1")
    private String stdVersion;

    @Schema(description = "只看当前生效版本（按 effective_from/effective_to 判定，空=全部）", example = "true")
    private Boolean onlyEffective;
}
