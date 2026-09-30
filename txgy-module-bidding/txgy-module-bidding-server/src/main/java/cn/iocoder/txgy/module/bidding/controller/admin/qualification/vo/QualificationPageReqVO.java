package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 企业资质台账分页查询入参。
 *
 * <p>规范出处：《招投标模块开发规范》§4.1（分页统一 pageNo/pageSize、pageSize ≤ 100）、§三-1（VO 字段类型）；
 * 契约《企业资质页-页面级契约-v1.0.md》§4 G1。
 *
 * <p>为什么这样写：① entId 必填——资质按企业主体展示，缺主体会跨企业串数据；
 * ② 筛选项全部下推 SQL（命中企业/状态/有效期索引），不在内存过滤；
 * ③ expiringInDays 传空 = **不过滤**：若默认按预警窗口过滤，台账列表会"看起来缺数据"；
 *    "即将到期"是页面上的快捷筛选，由前端显式传当日预警窗口值（窗口值来自规则配置，前端不得硬编码）。
 *
 * <p>业务目标：一次查询同时满足台账列表筛选、状态角标与"即将到期"快捷视图，避免前端多次往返。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QualificationPageReqVO extends PageParam {

    @Schema(description = "企业主体 id", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    /** 当前租户企业主体 id；不传时由 Controller 从主体档案推导。 */
    private Long entId;

    @Schema(description = "资质序列 code（字典）", example = "CONSTRUCTION")
    private String qualSerial;

    @Schema(description = "资质类别 code（字典）", example = "GENERAL_CONTRACTING")
    private String qualCategory;

    @Schema(description = "资质等级 code（字典）", example = "FIRST")
    private String qualGrade;

    @Schema(description = "资质状态 code：VALID/EXPIRING_SOON/EXPIRED/PENDING_RENEW/LIMITED/REVOKED", example = "VALID")
    private String qualStatus;

    @Schema(description = "只看有效期内且距到期 ≤ N 天（空=不过滤；N 取规则配置的预警窗口）", example = "90")
    private Integer expiringInDays;

    @Schema(description = "关键字：证书编号 / 发证机关 模糊匹配", example = "D361")
    private String keyword;
}
