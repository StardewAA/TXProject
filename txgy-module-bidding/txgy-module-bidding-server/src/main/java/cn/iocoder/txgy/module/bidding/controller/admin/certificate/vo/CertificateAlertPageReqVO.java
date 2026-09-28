package cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 证照到期预警清单分页 Request VO。
 *
 * <p>规范出处：《电子证照模块-前端接口契约》§2 台账 + 资质域既有形状
 * （{@code QualificationAlertPageReqVO}：refType / alertLevel / handled 三件套），三域保持一致，
 * 前端不必为"证照预警"另学一套参数。
 *
 * <p>为什么这样写（设计遵循）：
 * <ul>
 *   <li>{@code entId} **可选**：预警表本身不带企业字段（{@code cert_alert_records} 用 cert_id 定位证照），
 *       故按企业过滤时由服务层先取该企业的证照 id 集合再 {@code IN} 过滤；不传则返回本租户全部证照预警。</li>
 *   <li>不提供"按证书名模糊搜"：名称在 {@code refName}（可能为空，见 RespVO 说明），
 *       避免用 LIKE 查询把索引打穿；要按名字找请先查证照台账。</li>
 * </ul>
 *
 * <p>业务目标：让"到期预警看板"能列出**证照维度的预警**（此前只有资质域有清单接口，
 * 证照只能发通知、页面上看不到），并支持"只看未处理"。
 *
 * @author txgy
 */
@Schema(description = "证照到期预警清单分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CertificateAlertPageReqVO extends PageParam {

    @Schema(description = "企业主体 id（空=本租户全部；按企业过滤时先取该企业证照 id 集合）", example = "1")
    private Long entId;

    @Schema(description = "证照 id（空=全部；查看某张证照的预警历史时传）", example = "1024")
    private Long certId;

    @Schema(description = "预警类型 code：EXPIRY 到期 / REVIEW 复审 / RENEWAL 换证（空=全部）", example = "EXPIRY")
    private String alertType;

    @Schema(description = "档位 code：INFO / WARNING / CRITICAL（空=全部）", example = "WARNING")
    private String alertLevel;

    @Schema(description = "是否已处理（空=全部；true=只看已处理，false=只看待处理）", example = "false")
    private Boolean handled;

}
