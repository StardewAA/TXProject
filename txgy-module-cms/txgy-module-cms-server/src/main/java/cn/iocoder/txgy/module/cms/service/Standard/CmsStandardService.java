package cn.iocoder.txgy.module.cms.service.Standard;

import cn.iocoder.txgy.module.cms.controller.admin.Standard.vo.*;
import cn.iocoder.txgy.module.cms.dal.dataobject.Standard.CmsStandardDO;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import jakarta.validation.Valid;

/**
 * 行业标准规范库 Service 接口
 *
 * @author yuting_feng
 */
public interface CmsStandardService {

    Long createStandard(@Valid CmsStandardSaveReqVO createReqVO);

    void updateStandard(@Valid CmsStandardSaveReqVO updateReqVO);

    void deleteStandard(Long standardId);

    CmsStandardDO getStandard(Long standardId);

    PageResult<CmsStandardDO> getStandardPage(CmsStandardPageReqVO pageReqVO);

    java.util.List<CmsStandardDO> getStandardList();
}
