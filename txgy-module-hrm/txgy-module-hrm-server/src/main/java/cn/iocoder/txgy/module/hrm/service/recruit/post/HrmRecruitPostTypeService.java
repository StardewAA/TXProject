package cn.iocoder.txgy.module.hrm.service.recruit.post;

import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.post.HrmRecruitPostTypeDO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.post.type.HrmRecruitPostTypeSaveReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.post.type.HrmRecruitPostTypeStatusReqVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertMap;

/**
 * 招聘职位类型 Service 接口
 *
 * @author OPENLAB BS
 */
public interface HrmRecruitPostTypeService {

    Long createRecruitPostType(HrmRecruitPostTypeSaveReqVO reqVO);

    void updateRecruitPostType(HrmRecruitPostTypeSaveReqVO reqVO);

    void updateRecruitPostTypeStatus(HrmRecruitPostTypeStatusReqVO reqVO);

    void deleteRecruitPostType(Long id);

    HrmRecruitPostTypeDO getRecruitPostType(Long id);

    /**
     * 校验招聘职位类型是否存在
     *
     * @param id 招聘职位类型编号
     */
    void validateRecruitPostTypeExists(Long id);

    boolean hasChildren(Long id);

    boolean isUsableLeaf(Long id);

    void validateRecruitPostTypeUsable(Long id);

    /**
     * 获得招聘职位类型列表
     *
     * @param status 状态
     * @return 招聘职位类型列表
     */
    List<HrmRecruitPostTypeDO> getRecruitPostTypeList(Integer status);

    /**
     * 获得门户按职位类型筛选时应当匹配的全部类型编号：同名类型及其所有后代。
     *
     * 类型表里同一套分类被整棵复制了多份（277 个根节点只对应约 10 个逻辑大类），
     * 而岗位挂的是三级的具体工种、门户目录只暴露到二级，所以既要跨副本又要往下一层：
     * 只按编号精确匹配，选二级永远打不中三级的岗位；只按名字匹配，又够不到下面一层。
     *
     * @param postTypeId 门户选中的类型编号
     * @return 用于 IN 查询的类型编号集合，至少包含入参本身
     */
    List<Long> getRecruitPostTypeFilterIds(Long postTypeId);

    /**
     * 获得指定编号的招聘职位类型列表
     *
     * @param ids 招聘职位类型编号集合
     * @return 招聘职位类型列表
     */
    List<HrmRecruitPostTypeDO> getRecruitPostTypeListByIds(Collection<Long> ids);

    /**
     * 获得指定编号的招聘职位类型 Map
     *
     * @param ids 招聘职位类型编号集合
     * @return 招聘职位类型 Map
     */
    default Map<Long, HrmRecruitPostTypeDO> getRecruitPostTypeMap(Collection<Long> ids) {
        return convertMap(getRecruitPostTypeListByIds(ids), HrmRecruitPostTypeDO::getId);
    }

}
