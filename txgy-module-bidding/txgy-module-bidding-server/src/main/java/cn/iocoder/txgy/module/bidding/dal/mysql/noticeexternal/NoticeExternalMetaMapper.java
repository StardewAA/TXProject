package cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal;
// 规范依据：《招投标模块开发规范》§4.2（Mapper/BaseMapperX）；设计文档 v0.6 §15.1（G 轨）

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalMetaDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * bidding_notice_external_meta 元数据 Mapper（G 轨）。
 */
@Mapper
public interface NoticeExternalMetaMapper extends BaseMapperX<NoticeExternalMetaDO> {
}
