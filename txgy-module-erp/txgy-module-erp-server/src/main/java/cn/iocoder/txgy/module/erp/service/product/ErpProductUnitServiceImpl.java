package cn.iocoder.txgy.module.erp.service.product;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.erp.controller.admin.product.vo.unit.ErpProductUnitPageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.product.vo.unit.ErpProductUnitSaveReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.product.ErpProductUnitDO;
import cn.iocoder.txgy.module.erp.dal.mysql.product.ErpProductUnitMapper;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.*;

/**
 * ERP 产品单位 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class ErpProductUnitServiceImpl implements ErpProductUnitService {

    @Resource
    private ErpProductUnitMapper productUnitMapper;

    @Resource
    @Lazy // 延迟加载，避免循环依赖
    private ErpProductService productService;

    @Override
    public Long createProductUnit(ErpProductUnitSaveReqVO createReqVO) {
        // 1. 校验名字唯一
        validateProductUnitNameUnique(null, createReqVO.getName());
        validateProductUnitCodeUnique(null, createReqVO.getCode());
        // 2. 插入
        ErpProductUnitDO unit = BeanUtils.toBean(createReqVO, ErpProductUnitDO.class);
        productUnitMapper.insert(unit);
        return unit.getId();
    }

    @Override
    public void updateProductUnit(ErpProductUnitSaveReqVO updateReqVO) {
        // 1.1 校验存在
        ErpProductUnitDO oldUnit = validateProductUnitExists(updateReqVO.getId());
        if (!oldUnit.getCode().equals(updateReqVO.getCode())) {
            throw exception(PRODUCT_UNIT_CODE_IMMUTABLE);
        }
        // 1.2 校验名字唯一
        validateProductUnitNameUnique(updateReqVO.getId(), updateReqVO.getName());
        validateProductUnitCodeUnique(updateReqVO.getId(), updateReqVO.getCode());
        // 2. 更新
        ErpProductUnitDO updateObj = BeanUtils.toBean(updateReqVO, ErpProductUnitDO.class);
        productUnitMapper.updateById(updateObj);
    }

    @VisibleForTesting
    void validateProductUnitNameUnique(Long id, String name) {
        ErpProductUnitDO unit = productUnitMapper.selectByName(name);
        if (unit == null) {
            return;
        }
        // 如果 id 为空，说明不用比较是否为相同 id 的字典类型
        if (id == null) {
            throw exception(PRODUCT_UNIT_NAME_DUPLICATE);
        }
        if (!unit.getId().equals(id)) {
            throw exception(PRODUCT_UNIT_NAME_DUPLICATE);
        }
    }

    @VisibleForTesting
    void validateProductUnitCodeUnique(Long id, String code) {
        ErpProductUnitDO unit = productUnitMapper.selectByCode(code);
        if (unit != null && (id == null || !unit.getId().equals(id))) {
            throw exception(PRODUCT_UNIT_CODE_DUPLICATE, code);
        }
    }

    @Override
    public void deleteProductUnit(Long id) {
        // 1.1 校验存在
        validateProductUnitExists(id);
        // 1.2 校验产品是否使用
        if (productService.getProductCountByUnitId(id) > 0) {
            throw exception(PRODUCT_UNIT_EXITS_PRODUCT);
        }
        // 2. 删除
        productUnitMapper.deleteById(id);
    }

    private ErpProductUnitDO validateProductUnitExists(Long id) {
        ErpProductUnitDO unit = productUnitMapper.selectById(id);
        if (unit == null) {
            throw exception(PRODUCT_UNIT_NOT_EXISTS);
        }
        return unit;
    }

    @Override
    public ErpProductUnitDO getProductUnit(Long id) {
        return productUnitMapper.selectById(id);
    }

    @Override
    public PageResult<ErpProductUnitDO> getProductUnitPage(ErpProductUnitPageReqVO pageReqVO) {
        return productUnitMapper.selectPage(pageReqVO);
    }

    @Override
    public List<ErpProductUnitDO> getProductUnitListByStatus(Integer status) {
        return productUnitMapper.selectListByStatus(status);
    }

    @Override
    public List<ErpProductUnitDO> getProductUnitList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        return productUnitMapper.selectByIds(ids);
    }

}
