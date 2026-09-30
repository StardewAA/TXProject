package cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 个人端「一键生成」入参（门户自助）。
 *
 * <p>设计遵循：<b>不收 userId</b>——归属人一律由后端 {@code getLoginUserId()} 决定（防越权）。
 */
@Data
public class BidReviewPortalCreateReqVO {

    @Schema(description = "投标文件（标书）file_id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请先上传投标文件")
    private Long bidFileId;
    @Schema(description = "标书访问地址（上传接口返回的 url，用于解析取流）")
    private String bidFileUrl;
    @Schema(description = "标书文件名")
    private String bidFileName;
    @Schema(description = "招标文件 file_id（可选：传了则按本项目要求逐条比对）")
    private Long tenderFileId;
    @Schema(description = "招标文件访问地址")
    private String tenderFileUrl;
    @Schema(description = "招标文件名")
    private String tenderFileName;
    @Schema(description = "关联投标项目 id（可选）")
    private Long projectId;
    @Schema(description = "关联招标项目 id（可选）")
    private Long tenderProjectId;
    /**
     * 归属企业（租户编号，2026-09-20 新增，可选）。
     *
     * <p>为什么需要：个人端账号（尤其"个人端注册"产生的账号）常属于平台默认租户，
     * 按登录租户落库时记录只对本人可见、<b>企业端看不到</b>。
     * 个人端在上传页选择"所属企业"后传本字段；后端会校验该用户确实被该企业授权为经办人，
     * 再把任务写到该企业租户下（问题项/报告同租户），企业端按租户过滤即可看到。
     * 不传时保持原行为（落到当前登录租户）。
     */
    @Schema(description = "归属企业（租户编号，可选；仅限本人已被授权的企业）")
    private Long enterpriseTenantId;
    @Schema(description = "备注")
    private String remark;
}
