package cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 员工预警清单分页 Request VO。
 *
 * <p>规范出处：《电子证照模块-前端接口契约》§8 员工证书（到期清单） + 资质域既有形状（三域同形）。
 *
 * <p>为什么这样写（设计遵循）：
 * <ul>
 *   <li>{@code refType} 直接暴露员工侧的三个泛化类型码（PERSON_CERT / PERSON_CERT_REVIEW / PERSON_ID_CARD），
 *       因为这三类预警的**处理方式不同**（换证 / 复审 / 换身份证），前端需要能分别筛选。</li>
 *   <li>{@code entId} 可选：预警表以 refId（员工证书 id）定位，故按企业过滤由服务层先取该企业员工证书 id 集合，
 *       不传则返回本租户全部员工预警。</li>
 * </ul>
 *
 * <p>业务目标：让"证件到期看板"能列出**员工维度**的待办（含复审与身份证到期），并可只看未处理。
 *
 * @author txgy
 */
@Schema(description = "员工预警清单分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class PersonnelAlertPageReqVO extends PageParam {

    @Schema(description = "企业主体 id（空=本租户全部）", example = "1")
    private Long entId;

    @Schema(description = "泛化类型：PERSON_CERT 员工证书 / PERSON_CERT_REVIEW 复审 / PERSON_ID_CARD 身份证（空=全部）",
            example = "PERSON_CERT")
    private String refType;

    @Schema(description = "预警类型 code：EXPIRY / REVIEW / RENEWAL（空=全部）", example = "EXPIRY")
    private String alertType;

    @Schema(description = "档位 code：INFO / WARNING / CRITICAL（空=全部）", example = "CRITICAL")
    private String alertLevel;

    @Schema(description = "是否已处理（空=全部）", example = "false")
    private Boolean handled;

}
