package cn.iocoder.txgy.module.member.dal.mysql.user;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.txgy.module.member.controller.admin.user.vo.MemberUserPageReqVO;
import cn.iocoder.txgy.module.member.dal.dataobject.user.MemberUserDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 会员 User Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MemberUserMapper extends BaseMapperX<MemberUserDO> {

    default MemberUserDO selectByLoginName(String loginName) {
        return selectOne(MemberUserDO::getLoginName, loginName);
    }

    /** 必须在积分流水所在事务中调用；按会员行串行化积分变更，不能使用快照读替代。 */
    @Select("SELECT id, tenant_id, point FROM member_user "
            + "WHERE id = #{userId} AND tenant_id = #{tenantId} AND deleted = FALSE FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    MemberUserDO selectPointUserForUpdate(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    /** 行锁内仍保留租户、余额下限/上限条件，更新失败必须让整个积分事务回滚。 */
    @Update("UPDATE member_user SET point = COALESCE(point, 0) + #{point} "
            + "WHERE id = #{userId} AND tenant_id = #{tenantId} AND deleted = FALSE "
            + "AND COALESCE(point, 0) + #{point} BETWEEN 0 AND 2147483647")
    int updatePointForTenant(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                             @Param("point") Integer point);

    default MemberUserDO selectByMobile(String mobile) {
        return selectOne(MemberUserDO::getMobile, mobile);
    }

    default MemberUserDO selectByEmail(String email) {
        return selectOne(MemberUserDO::getEmail, email);
    }

    default List<MemberUserDO> selectListByNicknameLike(String nickname) {
        return selectList(new LambdaQueryWrapperX<MemberUserDO>()
                .likeIfPresent(MemberUserDO::getNickname, nickname));
    }

    default PageResult<MemberUserDO> selectPage(MemberUserPageReqVO reqVO) {
        // 处理 tagIds 过滤条件
        String tagIdSql = "";
        Object[] tagIdParams = new Object[0];
        if (CollUtil.isNotEmpty(reqVO.getTagIds())) {
            tagIdSql = IntStream.range(0, reqVO.getTagIds().size())
                    .mapToObj(index -> MyBatisUtils.findInSetWithParamIndex("tag_ids", index))
                    .collect(Collectors.joining(" OR "));
            tagIdParams = reqVO.getTagIds().toArray();
        }
        // 分页查询
        return selectPage(reqVO, new LambdaQueryWrapperX<MemberUserDO>()
                .likeIfPresent(MemberUserDO::getMobile, reqVO.getMobile())
                .likeIfPresent(MemberUserDO::getEmail, reqVO.getEmail())
                .betweenIfPresent(MemberUserDO::getLoginDate, reqVO.getLoginDate())
                .likeIfPresent(MemberUserDO::getNickname, reqVO.getNickname())
                .betweenIfPresent(MemberUserDO::getCreateTime, reqVO.getCreateTime())
                .eqIfPresent(MemberUserDO::getLevelId, reqVO.getLevelId())
                .eqIfPresent(MemberUserDO::getGroupId, reqVO.getGroupId())
                .apply(CollUtil.isNotEmpty(reqVO.getTagIds()), tagIdSql, tagIdParams)
                .orderByDesc(MemberUserDO::getId));
    }

    default Long selectCountByGroupId(Long groupId) {
        return selectCount(MemberUserDO::getGroupId, groupId);
    }

    default Long selectCountByLevelId(Long levelId) {
        return selectCount(MemberUserDO::getLevelId, levelId);
    }

    default Long selectCountByTagId(Long tagId) {
        return selectCount(new LambdaQueryWrapperX<MemberUserDO>()
                .apply(MyBatisUtils.findInSet("tag_ids"), tagId));
    }

    /**
     * 更新用户积分（增加）
     *
     * @param id        用户编号
     * @param incrCount 增加积分（正数）
     */
    default void updatePointIncr(Long id, Integer incrCount) {
        Assert.isTrue(incrCount > 0);
        LambdaUpdateWrapper<MemberUserDO> lambdaUpdateWrapper = new LambdaUpdateWrapper<MemberUserDO>()
                .setSql(" point = point + " + incrCount)
                .eq(MemberUserDO::getId, id);
        update(null, lambdaUpdateWrapper);
    }

    /**
     * 更新用户积分（减少）
     *
     * @param id        用户编号
     * @param incrCount 增加积分（负数）
     * @return 更新行数
     */
    default int updatePointDecr(Long id, Integer incrCount) {
        Assert.isTrue(incrCount < 0);
        LambdaUpdateWrapper<MemberUserDO> lambdaUpdateWrapper = new LambdaUpdateWrapper<MemberUserDO>()
                .setSql(" point = point + " + incrCount) // 负数，所以使用 + 号
                .eq(MemberUserDO::getId, id);
        return update(null, lambdaUpdateWrapper);
    }

}
