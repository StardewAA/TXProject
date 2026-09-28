package cn.iocoder.txgy.module.cms.api.material;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.cms.dto.material.CmsMaterialDTO;
import cn.iocoder.txgy.module.cms.dto.material.CmsMaterialSearchReqDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 仅 Java 契约；返回候选材料，不代表已完成安全或适用性认证。 */
public interface CmsMaterialApi {
    PageResult<CmsMaterialDTO> search(@NotNull @Valid CmsMaterialSearchReqDTO request);
    CmsMaterialDTO get(@NotNull @Positive Long id);
}
