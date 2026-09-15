package cn.iocoder.txgy.module.cms.api.workmanship;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.cms.dto.common.CmsLibrarySearchReqDTO;
import cn.iocoder.txgy.module.cms.dto.projectcase.CmsProjectCaseDTO;
import cn.iocoder.txgy.module.cms.dto.workmanship.CmsWorkmanshipDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 工艺工法与案例库 Java 契约，未暴露实际接口。 */
public interface CmsWorkmanshipApi {
    PageResult<CmsWorkmanshipDTO> searchMethods(@NotNull @Valid CmsLibrarySearchReqDTO request);
    CmsWorkmanshipDTO getMethod(@NotNull @Positive Long id);
    PageResult<CmsProjectCaseDTO> searchCases(@NotNull @Valid CmsLibrarySearchReqDTO request);
    CmsProjectCaseDTO getCase(@NotNull @Positive Long id);
}
