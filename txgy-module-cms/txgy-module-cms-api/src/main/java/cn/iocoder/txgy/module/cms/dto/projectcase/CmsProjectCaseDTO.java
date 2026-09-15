package cn.iocoder.txgy.module.cms.dto.projectcase;

import cn.iocoder.txgy.module.cms.dto.common.CmsResourceDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/** 工程案例只读模型。图文、视频使用继承的 attachmentIds，下载需鉴权。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CmsProjectCaseDTO extends CmsResourceDTO {
    private List<String> scenarioCodes = List.of();
    private String description;
    private List<Long> workmanshipIds = List.of();
    private List<Long> materialIds = List.of();
    private List<Long> standardVersionIds = List.of();
    private String problems;
    private String remediation;
    private String outcome;
}
