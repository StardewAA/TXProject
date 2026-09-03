package cn.iocoder.txgy.module.bidding.dal.mysql.notice;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.notice.NoticeDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 公告/公示主档 Mapper。
 */
@Mapper
public interface NoticeMapper extends BaseMapperX<NoticeDO> {
}
