package cn.iocoder.txgy.module.bidding.dal.mysql.attachment;
// 规范依据：《招投标模块开发规范》§4.2（Mapper/BaseMapperX）

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.attachment.AttachmentDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * bidding 通用附件 Mapper（一对 N：biz_type + biz_code）。
 */
@Mapper
public interface AttachmentMapper extends BaseMapperX<AttachmentDO> {
}