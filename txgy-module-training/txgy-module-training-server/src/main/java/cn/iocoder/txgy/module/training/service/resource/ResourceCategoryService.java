package cn.iocoder.txgy.module.training.service.resource;

import cn.iocoder.txgy.module.training.controller.admin.resource.vo.CategorySaveReqVO;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.CategoryTreeRespVO;

import java.util.List;

/**
 * 资源分类 Service
 *
 * <p>doc / video / question 三类分类各自存储一份数据，通过 code 保持同步，
 * 对任一类型的增删改会联动更新另外两类。
 *
 * @author 云培训学院课题小组
 */
public interface ResourceCategoryService {

    /**
     * 新增分类（type：doc/video/question），会同步到另外两类
     */
    Long createCategory(CategorySaveReqVO reqVO);

    /**
     * 更新分类，会同步到另外两类
     */
    void updateCategory(CategorySaveReqVO reqVO);

    /**
     * 删除分类（无子分类且无资源时允许），会同步到另外两类
     */
    void deleteCategory(Long id);

    /**
     * 查询指定类型下的分类树（三级）
     */
    List<CategoryTreeRespVO> getCategoryTree(String type);

    /**
     * 校验分类存在且类型匹配，返回分类 ID；非法时抛异常
     */
    void validateCategory(Long categoryId, String type);

}
