package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 资质维度选项（序列 → 类别 → 等级 三级级联，页签 3 与建档表单的下拉数据源）。
 *
 * <p>规范出处：契约 §4 G4；`资质管理/02-法规标准与核验渠道.md` line 37「不得静态固化」。
 *
 * <p>为什么这样写：① **不提供固定字典**——资质类别/等级处在改革过渡期（国发〔2021〕7号"证照分离"，
 * 类别压减合并、新旧标准交替），一旦固化进字典/枚举，换版就得改代码；改为从**版本化标准库**
 * （qualification_standard）按生效区间聚合，换版只改数据；
 * ② 返回三级嵌套而不是三个平铺数组：级联下拉不必前端自行推断层级关系，也避免"选了序列后
 * 类别列表里混入其他序列的类别"这类脏数据；③ 同时返回 stdVersions，便于页面提示"当前依据 vXXXX"。
 *
 * <p>业务目标：建档/核查页面的可选维度始终与库内现行标准一致，改革换版零代码改动。
 */
@Data
public class QualificationStandardOptionsRespVO {

    @Schema(description = "库内现行标准版本号（倒序，最新在前）", example = "[\"v2026.1\",\"v2024.2\"]")
    private List<String> stdVersions;

    @Schema(description = "序列 → 类别 → 等级 三级级联选项")
    private List<SerialOption> serials;

    /** 序列层。 */
    @Data
    public static class SerialOption {

        @Schema(description = "资质序列", example = "施工")
        private String qualSerial;

        @Schema(description = "该序列下的资质类别")
        private List<CategoryOption> categories;
    }

    /** 类别层。 */
    @Data
    public static class CategoryOption {

        @Schema(description = "资质类别", example = "市政公用工程施工总承包")
        private String qualCategory;

        @Schema(description = "该类别下的资质等级", example = "[\"一级\",\"二级\"]")
        private List<String> grades;
    }
}
