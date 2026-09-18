package cn.iocoder.txgy.module.hrm.service.personal.resume;

import cn.iocoder.txgy.module.hrm.controller.admin.portal.resume.vo.HrmPortalResumeCreateReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.resume.vo.HrmPortalResumeUploadReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.personal.resume.HrmPersonalResumeDO;

import java.util.List;

public interface HrmPersonalResumeService {

    Long createUploadResume(Long userId, HrmPortalResumeUploadReqVO reqVO);

    Long createOnlineResume(Long userId, HrmPortalResumeCreateReqVO reqVO);

    List<HrmPersonalResumeDO> getResumeList(Long userId);

    HrmPersonalResumeDO getResume(Long userId, Long id);

    boolean deleteUploadResume(Long userId, Long id);
}
