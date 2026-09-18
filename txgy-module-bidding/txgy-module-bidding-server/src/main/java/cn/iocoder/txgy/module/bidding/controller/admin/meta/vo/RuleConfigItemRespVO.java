package cn.iocoder.txgy.module.bidding.controller.admin.meta.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 规则配置项（契约 §1.2 的 data.items[] 元素）。
 *
 * <p>规范出处：契约《电子证照模块-前端接口契约-v1.0.md》§1.2；《电子资质七大模块技术方案.md》§8.1。
 * <p>为什么返回 description 与 legalFloor：阈值类配置必须让使用者看懂"这是什么"以及"能填多少"，
 * 否则前端只能自己写死提示文案与校验范围（口径分叉的根源）；regulationRef 用于"低于法定下限"时展示依据。
 * <p>业务目标：前端用同一份数据渲染配置页 + 做保存前预校验，无需硬编码任何阈值。
 */
@Schema(description = "管理后台 - 规则配置项")
@Data
public class RuleConfigItemRespVO {

    @Schema(description = "规则键（稳定标识）", requiredMode = Schema.RequiredMode.REQUIRED, example = "cert.expiring.window.days")
    private String ruleKey;

    @Schema(description = "配置值（按 valueType 解释；标量已反序列化为数字/布尔/字符串）", example = "60")
    private Object value;

    @Schema(description = "值类型：INT/DECIMAL/BOOL/STRING/JSON", example = "INT")
    private String valueType;

    @Schema(description = "人话说明（可直接作为页面提示文案）")
    private String description;

    @Schema(description = "法规/文档出处（设置页展示，如“《排污许可管理条例》第十四条”）")
    private String regulationRef;

    @Schema(description = "允许边界，如 “min”:1 与 “max”:365；为 null 表示不限制")
    private String legalFloor;

    @Schema(description = "配置版本号（建议随写请求回传，便于识别并发改配置）", example = "1")
    private Integer versionNo;

    @Schema(description = "分组 code：CERT/ALERT/PERSONNEL/…", example = "CERT")
    private String ruleGroup;

    @Schema(description = "配置来源：TENANT 租户级 / PLATFORM 平台默认 / FALLBACK 代码兜底", example = "PLATFORM")
    private String source;
}
