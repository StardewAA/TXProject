package cn.iocoder.txgy.module.training.service.resource;

import cn.iocoder.txgy.module.training.controller.admin.resource.vo.CategorySaveReqVO;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.CategoryTreeRespVO;

import java.util.List;

/**
 * 资源分类 Service
 *
 * @author 云培训学院课题小组
 */
public interface ResourceCategoryService {

    /**
     * 新增分类（type：doc/video/question）
     */
    Long createCategory(CategorySaveReqVO reqVO);

    /**
     * 更新分类
     */
    void updateCategory(CategorySaveReqVO reqVO);

    /**
     * 删除分类（无子分类且无资源时允许）
     */
    void deleteCategory(Long id);

    /**
     * 查询指定类型下的分类树（两级）
     */
    List<CategoryTreeRespVO> getCategoryTree(String type);

    /**
     * 校验分类存在且类型匹配，返回分类 ID；非法时抛异常
     */
    void validateCategory(Long categoryId, String type);

}
