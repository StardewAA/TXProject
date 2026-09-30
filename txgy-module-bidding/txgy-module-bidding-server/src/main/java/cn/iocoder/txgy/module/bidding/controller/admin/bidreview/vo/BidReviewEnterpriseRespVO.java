package cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 个人端可选的「所属企业」。
 *
 * <p>数据来源：{@code bidding_subject_user}（企业把该用户加为经办人的授权行，enabled=1）。
 * 企业名称不在本模块冗余，前端用现成的租户列表接口按 tenantId 映射显示。
 */
@Schema(description = "个人端 - 我已被授权的企业 Response VO")
@Data
public class BidReviewEnterpriseRespVO {

    @Schema(description = "企业（租户）编号")
    private Long tenantId;
    @Schema(description = "主体类型：BIDDER 投标人 / TENDERER 招标人")
    private String subjectType;
    @Schema(description = "经办角色")
    private String userRole;
}
