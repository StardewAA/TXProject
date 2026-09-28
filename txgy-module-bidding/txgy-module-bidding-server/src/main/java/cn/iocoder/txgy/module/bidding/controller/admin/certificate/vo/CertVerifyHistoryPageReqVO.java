package cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 验真历史分页查询入参（管理端；租户隔离由平台拦截器实施）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §17.5（验真历史表 cert_verify_history）；
 * GB/T 36906—2018 §6.4（verifyinfo 调用留痕）；《招投标模块开发规范》§4.2（分页 PageParam）。
 *
 * <p>为什么需要这个查询：`/verify` 与 `/verify-file` 每次都返回"验真历史主键"，但此前**没有任何接口能把它读回来**
 * ——留痕只进不出，主键等于一个再也打不开的句柄；事后追溯"谁在什么时候验了哪张证、结论是什么"只能查库。
 * 本查询补上读出口。
 *
 * <p>为什么不按时间区间过滤：主表已按 verifiedAt 倒序返回，且"追溯某张证"用 certId 定位即可；
 * 时间区间的入参约定（LocalDateTime[] 绑定）在本模块尚无先例，不在此处首创，需要时另开。
 */
@Schema(description = "管理后台 - 验真历史分页查询入参")
@Data
@EqualsAndHashCode(callSuper = true)
public class CertVerifyHistoryPageReqVO extends PageParam {

    @Schema(description = "被验真证照 ID（精确；为空则查全部）", example = "1")
    private Long certId;

    @Schema(description = "验真渠道 code（FOUR_PLATFORM/MINISTRY/PROVINCE/THIRD_PARTY）", example = "FOUR_PLATFORM")
    private String verifyChannel;

    @Schema(description = "验真结论 code（MATCH/MISMATCH/UNAVAILABLE）", example = "MATCH")
    private String verifyResult;

}
