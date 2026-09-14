package cn.iocoder.txgy.module.bidding.dal.mysql.noticefollow;
// 规范依据：《招投标模块开发规范》§4.2（Mapper/BaseMapperX）；设计文档 v0.6 §15.1（T 轨(拦截器自动租户过滤)）

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticefollow.FollowItemDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * bidding_follow_item 关注项目列表 Mapper（T 轨(拦截器自动租户过滤)）。
 */
@Mapper
public interface FollowItemMapper extends BaseMapperX<FollowItemDO> {
}
