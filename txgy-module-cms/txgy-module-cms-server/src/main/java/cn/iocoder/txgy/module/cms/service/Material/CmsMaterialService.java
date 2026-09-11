package cn.iocoder.txgy.module.cms.service.Material;

import cn.iocoder.txgy.module.cms.controller.admin.Material.vo.*;
import cn.iocoder.txgy.module.cms.dal.dataobject.Material.CmsMaterialDO;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import jakarta.validation.Valid;

/**
 * 材料参数数据库 Service 接口
 *
 * @author yuting_feng
 */
public interface CmsMaterialService {

    Long createMaterial(@Valid CmsMaterialSaveReqVO createReqVO);

    void updateMaterial(@Valid CmsMaterialSaveReqVO updateReqVO);

    void deleteMaterial(Long materialId);

    CmsMaterialDO getMaterial(Long materialId);

    PageResult<CmsMaterialDO> getMaterialPage(CmsMaterialPageReqVO pageReqVO);

    java.util.List<CmsMaterialDO> getMaterialList();
}
