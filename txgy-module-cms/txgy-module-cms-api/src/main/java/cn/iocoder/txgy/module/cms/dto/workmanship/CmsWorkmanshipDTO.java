package cn.iocoder.txgy.module.cms.dto.workmanship;

import cn.iocoder.txgy.module.cms.dto.common.CmsResourceDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/** 工法记录与工程案例分开；引用标准必须指向具体版本。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CmsWorkmanshipDTO extends CmsResourceDTO {
    private List<String> scenarioCodes = List.of();
    private String applicableConditions;
    private List<Step> steps = List.of();
    private List<Long> standardVersionIds = List.of();
    private List<Long> materialIds = List.of();
    private List<DefectTreatment> defectTreatments = List.of();

    public record Step(int order, String name, String instructions, String qualityChecks) { }
    public record DefectTreatment(String defect, String cause, String prevention, String remediation) { }
}
