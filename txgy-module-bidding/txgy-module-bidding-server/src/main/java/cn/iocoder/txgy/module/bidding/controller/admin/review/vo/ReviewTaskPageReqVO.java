package cn.iocoder.txgy.module.bidding.controller.admin.review.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 平台审查任务分页 Request VO。
 *
 * <p>规范出处：《后端变更请求-平台审查任务中心-2026-09-20》§5 接口 1（keyword/bizType/status/beginTime/endTime）。
 *
 * <p>为什么这样写（设计遵循）：
 * <ul>
 *   <li>`keyword` 同时匹配**企业名或统一社会信用代码**（审查者手上通常只有其一）；</li>
 *   <li>**不做"是否只看本企业"的开关**：租户与超管的可见范围由后端按登录身份决定（租户强制加 ent_id 过滤），
 *       若做成前端参数就能被绕过（越权读取他企业任务）。</li>
 * </ul>
 *
 * <p>业务目标：超管可按企业/类型/状态/时间筛选待审任务；租户只读查看本企业任务。
 *
 * @author txgy
 */
@Schema(description = "平台审查任务分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ReviewTaskPageReqVO extends PageParam {

    @Schema(description = "企业关键字（企业名称或统一社会信用代码，模糊匹配）", example = "示例建设")
    private String keyword;

    @Schema(description = "业务类型：QUALIFICATION / CERTIFICATE / PERSON_CERT（空=全部）", example = "CERTIFICATE")
    private String bizType;

    @Schema(description = "状态：PENDING / APPROVED / REJECTED（空=全部）", example = "PENDING")
    private String status;

    @Schema(description = "提交时间起（含）")
    private LocalDateTime beginTime;

    @Schema(description = "提交时间止（含）")
    private LocalDateTime endTime;

}
