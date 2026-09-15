package cn.iocoder.txgy.module.cms.service.material;

import cn.iocoder.txgy.module.cms.controller.admin.Material.vo.*;
import cn.iocoder.txgy.module.cms.dal.dataobject.Material.CmsMaterialDO;
import cn.iocoder.txgy.module.cms.dal.mysql.Material.CmsMaterialMapper;
import cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.*;

/**
 * 材料参数数据库 Service 实现类
 *
 * @author yuting_feng
 */
@Service
@Validated
public class CmsMaterialServiceImpl implements CmsMaterialService {

    @Resource
    private CmsMaterialMapper materialMapper;

    @Override
    public Long createMaterial(CmsMaterialSaveReqVO createReqVO) {
        CmsMaterialDO record = BeanUtils.toBean(createReqVO, CmsMaterialDO.class);
        materialMapper.insert(record);
        return record.getMaterialId();
    }

    @Override
    public void updateMaterial(CmsMaterialSaveReqVO updateReqVO) {
        validateMaterialExists(updateReqVO.getMaterialId());
        CmsMaterialDO updateObj = BeanUtils.toBean(updateReqVO, CmsMaterialDO.class);
        materialMapper.updateById(updateObj);
    }

    @Override
    public void deleteMaterial(Long materialId) {
        validateMaterialExists(materialId);
        materialMapper.deleteById(materialId);
    }

    private void validateMaterialExists(Long materialId) {
        if (materialMapper.selectById(materialId) == null) {
            throw exception(MATERIAL_NOT_EXISTS);
        }
    }

    @Override
    public CmsMaterialDO getMaterial(Long materialId) {
        return materialMapper.selectById(materialId);
    }

    @Override
    public PageResult<CmsMaterialDO> getMaterialPage(CmsMaterialPageReqVO pageReqVO) {
        return materialMapper.selectPage(pageReqVO);
    }

    @Override
    public java.util.List<CmsMaterialDO> getMaterialList() {
        return materialMapper.selectList(new LambdaQueryWrapperX<CmsMaterialDO>()
                .orderByDesc(CmsMaterialDO::getMaterialId));
    }
}
