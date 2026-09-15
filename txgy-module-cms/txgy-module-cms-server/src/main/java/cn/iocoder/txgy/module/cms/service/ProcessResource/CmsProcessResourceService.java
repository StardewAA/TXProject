package cn.iocoder.txgy.module.cms.service.ProcessResource;

import cn.iocoder.txgy.module.cms.controller.admin.ProcessResource.vo.*;
import cn.iocoder.txgy.module.cms.dal.dataobject.ProcessResource.CmsProcessResourceDO;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import jakarta.validation.Valid;

/**
 * 工艺工法与案例库 Service 接口
 *
 * @author yuting_feng
 */
public interface CmsProcessResourceService {

    Long createProcessResource(@Valid CmsProcessResourceSaveReqVO createReqVO);

    void updateProcessResource(@Valid CmsProcessResourceSaveReqVO updateReqVO);

    void deleteProcessResource(Long resourceId);

    CmsProcessResourceDO getProcessResource(Long resourceId);

    PageResult<CmsProcessResourceDO> getProcessResourcePage(CmsProcessResourcePageReqVO pageReqVO);

    java.util.List<CmsProcessResourceDO> getProcessResourceList();
}
