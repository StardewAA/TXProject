package cn.iocoder.txgy.module.hrm.service.personal.resume;

import cn.iocoder.txgy.module.hrm.controller.admin.portal.resume.vo.HrmPortalResumeVersionCreateReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.personal.resume.HrmPersonalResumeVersionDO;

import java.util.List;

/**
 * 个人简历版本服务。
 *
 * <p>负责把可编辑的在线简历草稿固化成不可变版本。版本是 AI 分析与投递的共同输入，
 * 一旦创建就不允许原地修改，从而保证历史结论与历史投递可追溯。</p>
 */
public interface HrmPersonalResumeVersionService {

    /**
     * 固化为新的个人简历版本。相同幂等键重复提交不会生成多个版本。
     *
     * @param userId 登录用户编号，由服务端会话取得，不信任前端传入
     * @param reqVO  固化请求
     * @return 版本编号
     */
    Long createVersion(Long userId, HrmPortalResumeVersionCreateReqVO reqVO);

    /**
     * 查询当前用户的版本列表，可按来源草稿过滤。
     */
    List<HrmPersonalResumeVersionDO> getVersionList(Long userId, Long sourceResumeId);

    /**
     * 按归属查询单个版本；非本人或不存在均返回异常。
     */
    HrmPersonalResumeVersionDO getVersion(Long userId, Long id);
}
