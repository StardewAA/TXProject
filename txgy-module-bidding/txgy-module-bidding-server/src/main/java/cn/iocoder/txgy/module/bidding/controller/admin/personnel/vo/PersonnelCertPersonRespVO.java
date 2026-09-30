package cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 员工证书「按人维度」分页出参（五页改造缺接口契约 Q9，2026-09-18）。
 *
 * <p>规范出处：Q9 契约——出参 personId/personName/personNo/deptName/certCount/nearestValidTo/certs[]；
 * certs = 该人在同筛选下的证书（字段同 PersonnelCertRespVO）。
 * <p>口径：certCount 与 certs 同源（同筛选下的证书数），nearestValidTo = 该人同筛选证书中最近到期日
 * （null=全部长期/无有效证书）；保证"同一人的证书在同一页内完整"（验收 §7.3）。
 */
@Schema(description = "管理后台 - 员工证书按人分页出参")
@Data
public class PersonnelCertPersonRespVO {

    @Schema(description = "员工档案主键", example = "1024")
    private Long personId;

    @Schema(description = "员工姓名（L3 脱敏展示）", example = "张三")
    private String personName;

    @Schema(description = "员工编号（企业内部唯一）", example = "EMP001")
    private String personNo;

    @Schema(description = "部门名称", example = "工程部")
    private String deptName;

    @Schema(description = "同筛选下证书数", example = "3")
    private Long certCount;

    @Schema(description = "最近到期日（同筛选证书中最小 valid_to；null=全部长期）", example = "2026-12-31")
    private LocalDate nearestValidTo;

    @Schema(description = "同筛选下证书列表（字段同 PersonnelCertRespVO）")
    private List<PersonnelCertRespVO> certs;
}
