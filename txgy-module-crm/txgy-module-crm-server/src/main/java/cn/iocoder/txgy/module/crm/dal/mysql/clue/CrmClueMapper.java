package cn.iocoder.txgy.module.crm.dal.mysql.clue;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.MPJLambdaWrapperX;
import cn.iocoder.txgy.module.crm.controller.admin.clue.vo.CrmCluePageReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.clue.CrmClueDO;
import cn.iocoder.txgy.module.crm.enums.common.CrmBizTypeEnum;
import cn.iocoder.txgy.module.crm.enums.common.CrmSceneTypeEnum;
import cn.iocoder.txgy.module.crm.util.CrmPermissionUtils;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collections;
import java.util.List;

/**
 * 线索 Mapper
 *
 * @author Wanwan
 */
@Mapper
public interface CrmClueMapper extends BaseMapperX<CrmClueDO> {

    /**
     * 变更累计跟进次数（delta 为 +1 表示新增一次跟进，-1 表示删除一条跟进；结果不会小于 0）
     *
     * @param id    线索编号
     * @param delta 变更量
     * @return 影响行数
     */
    default int updateFollowUpCountById(Long id, int delta) {
        return update(null, new LambdaUpdateWrapper<CrmClueDO>()
                .eq(CrmClueDO::getId, id)
                .setSql("follow_up_count = GREATEST(follow_up_count + (" + delta + "), 0)"));
    }

    default PageResult<CrmClueDO> selectPage(CrmCluePageReqVO pageReqVO, Long userId) {
        MPJLambdaWrapperX<CrmClueDO> query = new MPJLambdaWrapperX<>();
        // 拼接数据权限的查询条件
        CrmPermissionUtils.appendPermissionCondition(query, CrmBizTypeEnum.CRM_CLUE.getType(),
                CrmClueDO::getId, userId, pageReqVO.getSceneType());
        // 拼接自身的查询条件
        query.selectAll(CrmClueDO.class)
                .likeIfPresent(CrmClueDO::getName, pageReqVO.getName())
                .eqIfPresent(CrmClueDO::getTransformStatus, pageReqVO.getTransformStatus())
                .likeIfPresent(CrmClueDO::getTelephone, pageReqVO.getTelephone())
                .likeIfPresent(CrmClueDO::getMobile, pageReqVO.getMobile())
                .eqIfPresent(CrmClueDO::getIndustryId, pageReqVO.getIndustryId())
                .eqIfPresent(CrmClueDO::getLevel, pageReqVO.getLevel())
                .eqIfPresent(CrmClueDO::getSource, pageReqVO.getSource())
                .eqIfPresent(CrmClueDO::getFollowUpStatus, pageReqVO.getFollowUpStatus())
                .betweenIfPresent(CrmClueDO::getCreateTime, pageReqVO.getCreateTime())
                .orderByDesc(CrmClueDO::getId);
        return selectJoinPage(pageReqVO, CrmClueDO.class, query);
    }

    default Long selectCountByFollow(Long userId) {
        MPJLambdaWrapperX<CrmClueDO> query = new MPJLambdaWrapperX<>();
        // 主管/租户管理员统计下属负责的；普通销售统计自己负责的
        if (CrmPermissionUtils.isCrmOrTenantAdmin() || CrmPermissionUtils.hasSubordinate(userId)) {
            CrmPermissionUtils.appendPermissionCondition(query, CrmBizTypeEnum.CRM_CLUE.getType(),
                    CrmClueDO::getId, userId, CrmSceneTypeEnum.SUBORDINATE.getType());
        } else {
            CrmPermissionUtils.appendPermissionCondition(query, CrmBizTypeEnum.CRM_CLUE.getType(),
                    CrmClueDO::getId, userId, CrmSceneTypeEnum.OWNER.getType());
        }
        // 未跟进 + 未转化
        query.eq(CrmClueDO::getFollowUpStatus, false)
                .eq(CrmClueDO::getTransformStatus, false);
        return selectCount(query);
    }

    /**
     * 查重：根据手机号、线索名称查询匹配的线索
     *
     * @param mobile 手机号，精确匹配
     * @param name   线索名称，精确匹配
     * @return 线索列表，最多 5 条
     */
    default List<CrmClueDO> selectListByDuplicate(String mobile, String name) {
        LambdaQueryWrapperX<CrmClueDO> query = new LambdaQueryWrapperX<>();
        if (StrUtil.isNotBlank(mobile) && StrUtil.isNotBlank(name)) {
            query.and(w -> w.eq(CrmClueDO::getMobile, mobile).or().eq(CrmClueDO::getName, name));
        } else if (StrUtil.isNotBlank(mobile)) {
            query.eq(CrmClueDO::getMobile, mobile);
        } else if (StrUtil.isNotBlank(name)) {
            query.eq(CrmClueDO::getName, name);
        } else {
            return Collections.emptyList();
        }
        query.orderByDesc(CrmClueDO::getId).last("LIMIT 5");
        return selectList(query);
    }

}
