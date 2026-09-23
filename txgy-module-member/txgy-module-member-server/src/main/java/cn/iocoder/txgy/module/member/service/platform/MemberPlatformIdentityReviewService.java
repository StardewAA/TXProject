package cn.iocoder.txgy.module.member.service.platform;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.member.controller.admin.platform.vo.MemberPlatformAccountLinkPageReqVO;
import cn.iocoder.txgy.module.member.controller.admin.platform.vo.MemberPlatformAccountLinkRespVO;
import cn.iocoder.txgy.module.member.controller.admin.platform.vo.MemberPlatformAccountMergeReqVO;
import cn.iocoder.txgy.module.member.controller.admin.platform.vo.MemberPlatformAccountMergeRespVO;

public interface MemberPlatformIdentityReviewService {

    PageResult<MemberPlatformAccountLinkRespVO> getLinkPage(MemberPlatformAccountLinkPageReqVO request);

    MemberPlatformAccountMergeRespVO merge(MemberPlatformAccountMergeReqVO request, Long operatorUserId);
}
