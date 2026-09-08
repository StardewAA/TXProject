package cn.iocoder.txgy.module.hrm.service.recruit.post;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.post.HrmRecruitPostPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.post.HrmRecruitPostSaveReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.post.HrmRecruitPostStatusReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.post.HrmRecruitPostDO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertMap;

/**
 * 招聘职位 Service 接口
 *
 * @author OPENLAB BS
 */
public interface HrmRecruitPostService {

    /**
     * 创建招聘职位
     *
     * @param createReqVO 招聘职位信息
     * @return 招聘职位编号
     */
    Long createRecruitPost(HrmRecruitPostSaveReqVO createReqVO);

    /**
     * 更新招聘职位
     *
     * @param updateReqVO 招聘职位信息
     */
    void updateRecruitPost(HrmRecruitPostSaveReqVO updateReqVO);

    /**
     * 获得招聘职位
     *
     * @param id 招聘职位编号
     * @return 招聘职位
     */
    HrmRecruitPostDO getRecruitPost(Long id);

    /**
     * 校验招聘职位是否存在
     *
     * @param id 招聘职位编号
     * @return 招聘职位
     */
    HrmRecruitPostDO validateRecruitPostExists(Long id);

    /**
     * 校验招聘职位已发布，可被候选人关联。
     *
     * @param id 招聘职位编号
     * @return 招聘职位
     */
    HrmRecruitPostDO validateRecruitPostPublished(Long id);

    /**
     * 获得招聘职位列表
     *
     * @param ids 招聘职位编号集合
     * @return 招聘职位列表
     */
    List<HrmRecruitPostDO> getRecruitPostList(Collection<Long> ids);

    /**
     * 获得招聘职位 Map
     *
     * @param ids 招聘职位编号集合
     * @return 招聘职位 Map
     */
    default Map<Long, HrmRecruitPostDO> getRecruitPostMap(Collection<Long> ids) {
        return convertMap(getRecruitPostList(ids), HrmRecruitPostDO::getId);
    }

    /**
     * 获得招聘职位分页列表
     *
     * @param pageReqVO 分页查询
     * @return 招聘职位分页列表
     */
    PageResult<HrmRecruitPostDO> getRecruitPostPage(HrmRecruitPostPageReqVO pageReqVO);

    /**
     * 获得门户公开的招聘职位分页列表。门户岗位对所有租户可见，查询不受当前租户限制。
     *
     * @param pageReqVO 分页查询
     * @return 所有租户已发布职位
     */
    PageResult<HrmRecruitPostDO> getPortalRecruitPostPage(HrmRecruitPostPageReqVO pageReqVO);

    /**
     * 获得门户公开的已发布职位详情。
     *
     * @param id 职位编号
     * @return 已发布职位
     */
    HrmRecruitPostDO getPortalRecruitPostPublished(Long id);

    /**
     * 获得招聘职位精简列表
     *
     * @return 招聘职位精简列表
     */
    List<HrmRecruitPostDO> getRecruitPostSimpleList();

    /**
     * 更新招聘职位状态
     *
     * @param reqVO 招聘职位状态信息
     */
    void updateRecruitPostStatus(HrmRecruitPostStatusReqVO reqVO);

    /**
     * 获得招聘职位状态统计列表
     *
     * @param pageReqVO 分页查询
     * @return 状态与数量的映射
     */
    Map<Integer, Long> getRecruitPostStatusCount(HrmRecruitPostPageReqVO pageReqVO);

}
