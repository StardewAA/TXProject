package cn.iocoder.txgy.module.crm.service.product;

import cn.iocoder.txgy.module.crm.controller.admin.product.vo.category.CrmProductCategoryCreateReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.product.vo.category.CrmProductCategoryListReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.product.CrmProductCategoryDO;
import jakarta.validation.Valid;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertMap;

/**
 * CRM 产品分类 Service 接口
 *
 * @author ZanGe丶
 */
public interface CrmProductCategoryService {

    /**
     * 创建产品分类
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createProductCategory(@Valid CrmProductCategoryCreateReqVO createReqVO);

    /**
     * 更新产品分类
     *
     * @param updateReqVO 更新信息
     */
    void updateProductCategory(@Valid CrmProductCategoryCreateReqVO updateReqVO);

    /**
     * 删除产品分类
     *
     * @param id 编号
     */
    void deleteProductCategory(Long id);

    /**
     * 获得产品分类
     *
     * @param id 编号
     * @return 产品分类
     */
    CrmProductCategoryDO getProductCategory(Long id);

    /**
     * 获得根分类编号，不存在时创建
     *
     * <p>供 WMS 产品同步等系统流程使用，避免同步功能因为没有分类而不可用。</p>
     *
     * @param name 根分类名称
     * @return 分类编号
     */
    Long getOrCreateRootCategoryId(String name);

    /**
     * 获得产品分类列表
     *
     * @param listReqVO 列表请求
     * @return 产品分类列表
     */
    List<CrmProductCategoryDO> getProductCategoryList(CrmProductCategoryListReqVO listReqVO);

    /**
     * 获得产品分类列表
     *
     * @param ids 编号数组
     * @return 产品分类列表
     */
    List<CrmProductCategoryDO> getProductCategoryList(Collection<Long> ids);

    /**
     * 获得产品分类 Map
     *
     * @param ids 编号数组
     * @return 产品分类 Map
     */
    default Map<Long, CrmProductCategoryDO> getProductCategoryMap(Collection<Long> ids) {
        return convertMap(getProductCategoryList(ids), CrmProductCategoryDO::getId);
    }

}
