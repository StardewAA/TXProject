package cn.iocoder.txgy.module.hrm.dal.mysql.personal.resume;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.dal.dataobject.personal.resume.HrmPersonalResumeDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface HrmPersonalResumeMapper extends BaseMapperX<HrmPersonalResumeDO> {

    default List<HrmPersonalResumeDO> selectListByUserId(Long userId) {
        return selectList(new LambdaQueryWrapperX<HrmPersonalResumeDO>()
                .eq(HrmPersonalResumeDO::getUserId, userId)
                .orderByDesc(HrmPersonalResumeDO::getCreateTime)
                .orderByDesc(HrmPersonalResumeDO::getId));
    }

    default HrmPersonalResumeDO selectByIdAndUserId(Long id, Long userId) {
        return selectOne(new LambdaQueryWrapperX<HrmPersonalResumeDO>()
                .eq(HrmPersonalResumeDO::getId, id)
                .eq(HrmPersonalResumeDO::getUserId, userId));
    }

    default HrmPersonalResumeDO selectLatestOnlineByUserIdAndTitle(Long userId, String title) {
        return selectList(new LambdaQueryWrapperX<HrmPersonalResumeDO>()
                .eq(HrmPersonalResumeDO::getUserId, userId)
                .eq(HrmPersonalResumeDO::getResumeType, 2)
                .eq(HrmPersonalResumeDO::getTitle, title)
                .orderByDesc(HrmPersonalResumeDO::getCreateTime)
                .orderByDesc(HrmPersonalResumeDO::getId))
                .stream().findFirst().orElse(null);
    }

    default int updateOnlineByIdAndUserId(HrmPersonalResumeDO updateObj) {
        return update(new LambdaUpdateWrapper<HrmPersonalResumeDO>()
                .eq(HrmPersonalResumeDO::getId, updateObj.getId())
                .eq(HrmPersonalResumeDO::getUserId, updateObj.getUserId())
                .eq(HrmPersonalResumeDO::getResumeType, 2)
                .set(HrmPersonalResumeDO::getTitle, updateObj.getTitle())
                .set(HrmPersonalResumeDO::getContent, updateObj.getContent())
                .set(HrmPersonalResumeDO::getRemark, updateObj.getRemark()));
    }

    default int deleteUploadByIdAndUserId(Long id, Long userId) {
        return delete(new LambdaQueryWrapperX<HrmPersonalResumeDO>()
                .eq(HrmPersonalResumeDO::getId, id)
                .eq(HrmPersonalResumeDO::getUserId, userId)
                .eq(HrmPersonalResumeDO::getResumeType, 1));
    }
}
