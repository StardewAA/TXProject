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

    /** 获得所有租户中同名的启用职位类型编号，用于门户跨企业筛选。 */
    List<Long> getRecruitPostTypeIdsByName(String name);

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
