package cn.iocoder.txgy.module.bidding.controller.admin.notice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.txgy.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 公开同步入参（05 接口设计文档 §2.2/§3.2：BID-SUP-GATHER-02）。
 *
 * <p>方向：企业后台（公告/公示）→ 归集 → 大厅检索文档。
 * 文档内容/标题/类型代码等一律以公告实体为准（权威数据集 payload 已按数据集规整），
 * 本接口只做"白名单校验 + 触发同步"：仅 PUBLISHED、非内部、SOCIAL 的公告可同步；
 * 更正/撤销走公告自身发布-撤下（撤下即从检索下线），以追加文档方式处理。
 */
@Schema(description = "公开同步入参（BID-SUP-GATHER-02）")
@Data
public class NoticePublicSyncReqVO {

    @Schema(description = "公告内部编号（bizNo，幂等键组成部分）", example = "BN123456789012")
    @NotBlank(message = "公告编号不能为空")
    private String bizNo;

    @Schema(description = "data_timestamp（幂等键组成部分：bizNo+dataTimestamp，05 公共规则）")
    @NotNull(message = "dataTimestamp 不能为空")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime dataTimestamp;

}
