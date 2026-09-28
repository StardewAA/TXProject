package cn.iocoder.txgy.module.hrm.service.recruit.resume;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.resume.HrmRecruitResumeSaveReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.resume.HrmRecruitResumeSnapshotReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.candidate.HrmRecruitCandidateDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.resume.HrmRecruitResumeDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.resume.HrmRecruitResumeMapper;
import cn.iocoder.txgy.module.hrm.service.recruit.candidate.HrmRecruitCandidateService;
import cn.iocoder.txgy.module.hrm.service.common.HrmResumeFileValidator;
import cn.iocoder.txgy.module.hrm.service.recruit.file.HrmRecruitLocalFileService;
import cn.iocoder.txgy.module.infra.api.file.FileApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.RECRUIT_RESUME_NOT_EXISTS;

@Service
@Validated
public class HrmRecruitResumeServiceImpl implements HrmRecruitResumeService {

    private static final int FILE_URL_EXPIRATION_SECONDS = 600;

    @Resource
    private HrmRecruitResumeMapper recruitResumeMapper;
    @Resource
    private HrmRecruitCandidateService recruitCandidateService;
    @Resource
    private FileApi fileApi;
    @Resource
    private HrmResumeFileValidator resumeFileValidator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRecruitResume(HrmRecruitResumeSaveReqVO reqVO) {
        // 服务端兜底校验简历附件类型与大小，避免绕过前端直接上传可执行文件/超大文件
        resumeFileValidator.validate(reqVO.getFileName(), reqVO.getFileUrl());
        return insertNewVersion(BeanUtils.toBean(reqVO, HrmRecruitResumeDO.class), reqVO.getCandidateId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRecruitResumeSnapshot(HrmRecruitResumeSnapshotReqVO reqVO) {
        // 投递快照：内容原样固化，不做任何后续同步，保证历史应聘不受求职者后续修改影响。
        HrmRecruitResumeDO snapshot = new HrmRecruitResumeDO()
                .setFileName(reqVO.getFileName())
                .setFileUrl(reqVO.getFileUrl())
                .setContent(reqVO.getContent())
                .setContentHash(reqVO.getContentHash())
                .setSourcePersonalVersionId(reqVO.getSourcePersonalVersionId())
                .setPostId(reqVO.getPostId())
                .setPostFingerprint(reqVO.getPostFingerprint())
                .setRemark(reqVO.getRemark());
        return insertNewVersion(snapshot, reqVO.getCandidateId());
    }

    /**
     * 在候选人维度分配新版本号并写入一条简历记录。
     *
     * <p>锁候选人行，避免并发投递或上传分配到相同版本号。</p>
     */
    private Long insertNewVersion(HrmRecruitResumeDO resume, Long candidateId) {
        HrmRecruitCandidateDO candidate = recruitCandidateService
                .validateRecruitCandidateExistsForUpdate(candidateId);
        List<HrmRecruitResumeDO> existing = recruitResumeMapper.selectListByCandidateId(candidateId);
        int nextVersion = existing.stream().map(HrmRecruitResumeDO::getVersionNo)
                .filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).max().orElse(0) + 1;
        recruitResumeMapper.markReplacedByCandidateId(candidateId);
        resume.setVersionNo(nextVersion).setStatus(0);
        // 与候选人、应聘记录保持同一企业租户归属。
        Long candidateTenantId = candidate == null ? null : candidate.getTenantId();
        resume.setTenantId(candidateTenantId != null ? candidateTenantId : TenantContextHolder.getTenantId());
        recruitResumeMapper.insert(resume);
        return resume.getId();
    }

    @Override
    public List<HrmRecruitResumeDO> getRecruitResumeList(Long candidateId) {
        if (candidateId == null) return Collections.emptyList();
        HrmRecruitCandidateDO candidate = recruitCandidateService.validateRecruitCandidateExists(candidateId);
        List<HrmRecruitResumeDO> resumes = recruitResumeMapper.selectListByCandidateId(candidateId);
        // 忽略租户执行上下文（例如门户聚合查询）下仍不能混入错误租户的简历记录。
        Long candidateTenantId = candidate == null ? null : candidate.getTenantId();
        Long currentTenantId = TenantContextHolder.getTenantId();
        if (candidateTenantId != null) {
            resumes.removeIf(item -> !Objects.equals(candidateTenantId, item.getTenantId()));
        } else if (!TenantContextHolder.isIgnore() && currentTenantId != null) {
            resumes.removeIf(item -> !Objects.equals(currentTenantId, item.getTenantId()));
        }
        if (candidate == null || CollUtil.isNotEmpty(resumes) || CollUtil.isEmpty(candidate.getResumeUrls())) {
            return resumes;
        }
        // 兼容第一组遗留的 resume_urls 字段，统一走本服务的授权预览/下载接口。
        return buildLegacyResumeList(candidate);
    }

    @Override
    public HrmRecruitResumeDO getAuthorizedRecruitResume(Long id) {
        HrmRecruitResumeDO resume = recruitResumeMapper.selectById(id);
        if (resume == null) throw exception(RECRUIT_RESUME_NOT_EXISTS);
        HrmRecruitCandidateDO candidate = recruitCandidateService
                .validateRecruitCandidateExists(resume.getCandidateId());
        validateResumeTenant(resume, candidate);
        // 在线结构化简历快照没有附件地址，此时直接返回内容，不做文件授权
        if (StrUtil.isBlank(resume.getFileUrl())) {
            if (StrUtil.isBlank(resume.getContent())) throw exception(RECRUIT_RESUME_NOT_EXISTS);
            return resume;
        }
        String authorizedUrl = HrmRecruitLocalFileService.isLocalFileUrl(resume.getFileUrl())
                ? resume.getFileUrl()
                : fileApi.presignGetUrl(resume.getFileUrl(), FILE_URL_EXPIRATION_SECONDS).getCheckedData();
        return BeanUtils.toBean(resume, HrmRecruitResumeDO.class)
                .setFileUrl(authorizedUrl);
    }

    @Override
    public HrmRecruitResumeDO getAuthorizedLegacyRecruitResume(Long candidateId, Integer legacyIndex) {
        if (candidateId == null || legacyIndex == null || legacyIndex < 0) {
            throw exception(RECRUIT_RESUME_NOT_EXISTS);
        }
        HrmRecruitCandidateDO candidate = recruitCandidateService.validateRecruitCandidateExists(candidateId);
        if (candidate == null) throw exception(RECRUIT_RESUME_NOT_EXISTS);
        List<String> urls = candidate.getResumeUrls();
        if (CollUtil.isEmpty(urls) || legacyIndex >= urls.size()) {
            throw exception(RECRUIT_RESUME_NOT_EXISTS);
        }
        String fileUrl = urls.get(legacyIndex);
        if (StrUtil.isBlank(fileUrl)) throw exception(RECRUIT_RESUME_NOT_EXISTS);
        String authorizedUrl = HrmRecruitLocalFileService.isLocalFileUrl(fileUrl)
                ? fileUrl
                : fileApi.presignGetUrl(fileUrl, FILE_URL_EXPIRATION_SECONDS).getCheckedData();
        HrmRecruitResumeDO resume = new HrmRecruitResumeDO().setCandidateId(candidateId)
                .setFileName(extractFileName(fileUrl)).setFileUrl(authorizedUrl)
                .setVersionNo(legacyIndex + 1).setStatus(0).setLegacyIndex(legacyIndex);
        resume.setTenantId(candidate.getTenantId());
        return resume;
    }

    private List<HrmRecruitResumeDO> buildLegacyResumeList(HrmRecruitCandidateDO candidate) {
        List<HrmRecruitResumeDO> result = new ArrayList<>();
        List<String> urls = candidate.getResumeUrls();
        for (int i = 0; i < urls.size(); i++) {
            String fileUrl = urls.get(i);
            if (StrUtil.isBlank(fileUrl)) continue;
            result.add(new HrmRecruitResumeDO().setCandidateId(candidate.getId())
                    .setFileName(extractFileName(fileUrl)).setVersionNo(i + 1).setStatus(0)
                    .setLegacyIndex(i));
        }
        return result;
    }

    private void validateResumeTenant(HrmRecruitResumeDO resume, HrmRecruitCandidateDO candidate) {
        if (candidate != null && candidate.getTenantId() != null
                && !Objects.equals(candidate.getTenantId(), resume.getTenantId())) {
            throw exception(RECRUIT_RESUME_NOT_EXISTS);
        }
        Long currentTenantId = TenantContextHolder.getTenantId();
        if (!TenantContextHolder.isIgnore() && currentTenantId != null
                && !Objects.equals(currentTenantId, resume.getTenantId())) {
            throw exception(RECRUIT_RESUME_NOT_EXISTS);
        }
    }

    private String extractFileName(String fileUrl) {
        String path = fileUrl;
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) path = path.substring(0, queryIndex);
        int slashIndex = path.lastIndexOf('/');
        String fileName = slashIndex >= 0 ? path.substring(slashIndex + 1) : path;
        if (StrUtil.isBlank(fileName)) return "简历附件";
        try {
            return java.net.URLDecoder.decode(fileName, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return fileName;
        }
    }
}
