package cn.iocoder.txgy.module.bidding.dal.mysql.notice;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.notice.NoticeContentDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 公告/公示正文内容 Mapper。
 */
@Mapper
public interface NoticeContentMapper extends BaseMapperX<NoticeContentDO> {
}
