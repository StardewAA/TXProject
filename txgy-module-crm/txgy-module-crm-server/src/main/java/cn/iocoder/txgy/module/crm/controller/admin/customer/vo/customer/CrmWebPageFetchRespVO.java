package cn.iocoder.txgy.module.crm.controller.admin.customer.vo.customer;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 网页抓取 Response VO
 *
 * 用于「一键识别 - 网页链接」标签页：后端代理抓取网页正文，前端再交给大模型提取工商信息。
 */
@Schema(description = "管理后台 - 网页抓取 Response VO")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CrmWebPageFetchRespVO {

    @Schema(description = "网页地址", requiredMode = Schema.RequiredMode.REQUIRED, example = "https://www.example.com")
    private String url;

    @Schema(description = "网页标题", example = "天信管业科技有限公司")
    private String title;

    @Schema(description = "网页正文文本")
    private String content;

}
