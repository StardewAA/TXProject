package cn.iocoder.txgy.module.hrm.service.personal.resume;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.resume.vo.HrmPortalResumeCreateReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.resume.vo.HrmPortalResumeUploadReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.personal.resume.HrmPersonalResumeDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.application.HrmRecruitApplicationMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.candidate.HrmRecruitCandidateMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.chat.HrmRecruitChatConversationMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.personal.resume.HrmPersonalResumeMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Validated
public class HrmPersonalResumeServiceImpl implements HrmPersonalResumeService {

    private static final int UPLOAD_RESUME_TYPE = 1;
    private static final int ONLINE_RESUME_TYPE = 2;
    private static final String RECRUIT_PROFILE_TITLE = "求职资料（系统）";

    @Resource
    private HrmPersonalResumeMapper resumeMapper;
    @Resource
    private HrmRecruitApplicationMapper applicationMapper;
    @Resource
    private HrmRecruitCandidateMapper candidateMapper;
    @Resource
    private HrmRecruitChatConversationMapper chatConversationMapper;

    @Override
    public Long createUploadResume(Long userId, HrmPortalResumeUploadReqVO reqVO) {
        HrmPersonalResumeDO resume = BeanUtils.toBean(reqVO, HrmPersonalResumeDO.class)
                .setUserId(userId).setResumeType(UPLOAD_RESUME_TYPE);
        return insertInCurrentTenant(resume);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOnlineResume(Long userId, HrmPortalResumeCreateReqVO reqVO) {
        HrmPersonalResumeDO resume = BeanUtils.toBean(reqVO, HrmPersonalResumeDO.class)
                .setUserId(userId).setResumeType(ONLINE_RESUME_TYPE);
        HrmPersonalResumeDO existing = userId == null ? null
                : TenantUtils.executeIgnore(() -> resumeMapper
                .selectLatestOnlineByUserIdAndTitle(userId, reqVO.getTitle()));
        Long resumeId;
        if (existing == null) {
            resumeId = insertInCurrentTenant(resume);
        } else {
            resume.setId(existing.getId()).setTenantId(existing.getTenantId());
            TenantUtils.executeIgnore(() -> resumeMapper.updateOnlineByIdAndUserId(resume));
            resumeId = existing.getId();
        }
        resume.setId(resumeId);
        syncRecruitCandidates(userId, resume);
        return resumeId;
    }

    @Override
    public List<HrmPersonalResumeDO> getResumeList(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return TenantUtils.executeIgnore(() -> resumeMapper.selectListByUserId(userId));
    }

    @Override
    public HrmPersonalResumeDO getResume(Long userId, Long id) {
        if (userId == null || id == null) {
            return null;
        }
        return TenantUtils.executeIgnore(() -> resumeMapper.selectByIdAndUserId(id, userId));
    }

    @Override
    public boolean deleteUploadResume(Long userId, Long id) {
        if (userId == null || id == null) {
            return false;
        }
        return TenantUtils.executeIgnore(() -> resumeMapper.deleteUploadByIdAndUserId(id, userId) > 0);
    }

    private Long insertInCurrentTenant(HrmPersonalResumeDO resume) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            tenantId = 0L;
        }
        Long targetTenantId = tenantId;
        resume.setTenantId(targetTenantId);
        final Long[] id = new Long[1];
        TenantUtils.execute(targetTenantId, () -> {
            resumeMapper.insert(resume);
            id[0] = resume.getId();
        });
        return id[0];
    }

    private void syncRecruitCandidates(Long userId, HrmPersonalResumeDO resume) {
        if (userId == null || ONLINE_RESUME_TYPE != resume.getResumeType()
                || !RECRUIT_PROFILE_TITLE.equals(resume.getTitle())
                || StrUtil.isBlank(resume.getContent())) {
            return;
        }
        JSONObject profile;
        try {
            profile = JSONUtil.parseObj(resume.getContent());
        } catch (RuntimeException ex) {
            return;
        }
        String name = trimToNull(profile.getStr("name"));
        String mobile = trimToNull(profile.getStr("mobile"));
        if (name == null || mobile == null) {
            return;
        }
        Set<Long> candidateIds = TenantUtils.executeIgnore(() -> {
            Set<Long> ids = new HashSet<>();
            applicationMapper.selectListByApplicantUserId(userId).stream()
                    .map(application -> application.getCandidateId())
                    .filter(candidateId -> candidateId != null)
                    .forEach(ids::add);
            chatConversationMapper.selectListByCandidateUserId(userId).stream()
                    .map(conversation -> conversation.getCandidateId())
                    .filter(candidateId -> candidateId != null)
                    .forEach(ids::add);
            return ids;
        });
        if (candidateIds.isEmpty()) {
            return;
        }
        Integer sex = profile.getInt("gender");
        if (sex == null) {
            sex = profile.getInt("sex", 1);
        }
        Integer education = resolveEducation(profile.getStr("education"));
        Integer workTime = profile.getInt("workTime");
        if (workTime == null) {
            workTime = calculateWorkTime(profile.getStr("jobStart"), profile.getStr("jobEnd"));
        }
        Integer age = calculateAge(profile.getStr("birthMonth"));
        String email = trimToNull(profile.getStr("email"));
        String graduateSchool = trimToNull(profile.getStr("school"));
        String latestWorkPlace = trimToNull(profile.getStr("company"));
        Integer syncSex = sex;
        Integer syncWorkTime = workTime;
        TenantUtils.executeIgnore(() -> candidateMapper.updateFromPersonalProfile(candidateIds, name, mobile,
                syncSex, age, email, syncWorkTime, education, graduateSchool, latestWorkPlace));
    }

    private Integer resolveEducation(String education) {
        if (StrUtil.isBlank(education)) {
            return 5;
        }
        String normalized = education.replaceAll("[\\s/]+", "");
        if (normalized.contains("博士")) return 7;
        if (normalized.contains("硕士")) return 6;
        if (normalized.contains("本科")) return 5;
        if (normalized.contains("大专")) return 4;
        if (normalized.contains("高中") || normalized.contains("中专") || normalized.contains("初中")) return 3;
        return 5;
    }

    private Integer calculateAge(String birthMonth) {
        if (StrUtil.isBlank(birthMonth)) {
            return null;
        }
        try {
            return Period.between(YearMonth.parse(birthMonth).atDay(1), LocalDate.now()).getYears();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Integer calculateWorkTime(String workStart, String workEnd) {
        if (StrUtil.isBlank(workStart)) {
            return null;
        }
        try {
            YearMonth start = YearMonth.parse(workStart);
            YearMonth end = StrUtil.isBlank(workEnd) ? YearMonth.now() : YearMonth.parse(workEnd);
            if (end.isBefore(start)) {
                return null;
            }
            return Period.between(start.atDay(1), end.atDay(1)).getYears();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String trimToNull(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }
}
