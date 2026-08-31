package cn.iocoder.txgy.module.member.convert.tag;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.member.controller.admin.tag.vo.MemberTagCreateReqVO;
import cn.iocoder.txgy.module.member.controller.admin.tag.vo.MemberTagRespVO;
import cn.iocoder.txgy.module.member.controller.admin.tag.vo.MemberTagUpdateReqVO;
import cn.iocoder.txgy.module.member.dal.dataobject.tag.MemberTagDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 会员标签 Convert
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MemberTagConvert {

    MemberTagConvert INSTANCE = Mappers.getMapper(MemberTagConvert.class);

    MemberTagDO convert(MemberTagCreateReqVO bean);

    MemberTagDO convert(MemberTagUpdateReqVO bean);

    MemberTagRespVO convert(MemberTagDO bean);

    List<MemberTagRespVO> convertList(List<MemberTagDO> list);

    PageResult<MemberTagRespVO> convertPage(PageResult<MemberTagDO> page);

}
