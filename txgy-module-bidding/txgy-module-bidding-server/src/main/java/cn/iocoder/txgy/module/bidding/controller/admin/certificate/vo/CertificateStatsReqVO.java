package cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 证照看板统计请求（五页改造缺接口契约 Q3，2026-09-28：看板聚合取数，支持台账状态多选）。
 *
 * <p>规范出处：《电子证照模块-前端接口契约》五页改造缺接口 Q3（P2 优化项）——
 * 过滤条件与 {@link CertificatePageReqVO} 同口径（同条件同结果），看板才能与列表筛选联动。
 * <p>为什么这样写：原前端方案用 `certificate/page` 的 pageSize=1 并发计数取 total（5 次请求），
 * 且 `certStatus` 只支持单个 code（不能 in 多个），暂扣/吊销/注销无法进卡片；
 * 这里把筛选收敛为一次请求，并允许 `certStatuses` 多选。
 * <p>业务目标：证照台账看板一次取全量口径计数（含暂扣/吊销/注销），并与页面筛选保持一致。
 */
@Schema(description = "管理后台 - 证照看板统计请求")
@Data
public class CertificateStatsReqVO {

    @Schema(description = "证照名称（模糊）", example = "建筑业企业资质证书")
    private String certName;

    @Schema(description = "证书编号（精确）", example = "D123456789")
    private String certNo;

    @Schema(description = "证照类型 code（字典 cert_type，如 ADMIN_LICENSE 营业执照）", example = "ADMIN_LICENSE")
    private String certType;

    @Schema(description = "证照大类 code（字典 cert_category）；由后端按 cert_type_category 展开为 certType in (...)，"
            + "口径与 page 完全一致（Q3：page 与 stats 同过滤口径）", example = "IDENTITY")
    private String certCategory;

    @Schema(description = "台账状态 code 多选（ACTIVE/EXPIRING_SOON/EXPIRED/SUSPENDED/REVOKED/CANCELLED）；与 certStatus 同传时以本字段为准", example = "ACTIVE")
    private List<String> certStatuses;

    @Schema(description = "台账状态 code 单选（兼容旧口径；多选字段为空时生效）", example = "ACTIVE")
    private String certStatus;

    @Schema(description = "持证主体（模糊）", example = "某某建设集团有限公司")
    private String holderName;

    @Schema(description = "验真状态 code 多选（UNVERIFIED/VERIFYING/VERIFIED/INVALID）；与 verificationStatus 同传时以本字段为准", example = "UNVERIFIED")
    private List<String> verificationStatuses;

    @Schema(description = "验真状态 code 单选（兼容旧口径；多选字段为空时生效）", example = "UNVERIFIED")
    private String verificationStatus;
}
