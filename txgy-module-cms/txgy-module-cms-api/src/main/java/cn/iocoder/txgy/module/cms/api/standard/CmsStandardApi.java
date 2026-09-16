package cn.iocoder.txgy.module.cms.api.standard;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.cms.dto.standard.CmsStandardDTO;
import cn.iocoder.txgy.module.cms.dto.standard.CmsStandardSearchReqDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 仅 Java 契约，未注册 HTTP/Feign；实现必须执行服务端租户、权限和发布状态校验。 */
public interface CmsStandardApi {
    PageResult<CmsStandardDTO> search(@NotNull @Valid CmsStandardSearchReqDTO request);
    CmsStandardDTO get(@NotNull @Positive Long id);
}
