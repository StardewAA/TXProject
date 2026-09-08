package cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo;
// [依据] 解析器测试功能：解析器插件清单行（code 与 bidding_fetch_source.parser_code_default 对应）。

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 爬虫解析器插件信息 Response")
@Data
public class CrawlParserInfoRespVO {

    @Schema(description = "解析器 code（源配置 parser_code_default / 测试选择用）")
    private String code;
    @Schema(description = "解析器名称")
    private String name;
    @Schema(description = "适用说明（站点/场景/规则版本）")
    private String description;
    @Schema(description = "支持的载荷类型：HTML/JSON")
    private List<String> supportedTypes;

}
