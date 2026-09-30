package cn.iocoder.txgy.module.bidding.service.project;

// [规范依据]《招投标模块开发规范》§3.1/3.2、§4.5（幂等：唯一键 upsert）、§5（T 轨租户隔离由拦截器自动生效）
//           + 数据规范 V2.0 数据集 ProjectVO / TenderProjectVO / BidSectionVO（必填=是与字段全集）
// [业务目标] 三档主档登记/查询/删除；登记走"按唯一编号 upsert"，重复提交幂等；删除有下级时拒绝（保追溯链）
// [为什么] 追溯以编号为键，编号重复或上级缺失会让追溯结果不可信；服务层集中做唯一性与关联校验，
//           出参统一按 @PublicLevel(SOCIAL) 裁剪，避免各接口自行拼装导致口径漂移

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.bidding.api.vo.BidSectionVO;
import cn.iocoder.txgy.module.bidding.api.vo.ProjectVO;
import cn.iocoder.txgy.module.bidding.api.vo.TenderProjectVO;
import cn.iocoder.txgy.module.bidding.controller.admin.attachment.vo.AttachmentSaveReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.project.vo.*;
import cn.iocoder.txgy.module.bidding.dal.dataobject.project.BidSectionDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.project.ProjectDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.project.TenderProjectDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.project.BidSectionMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.project.ProjectMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.project.TenderProjectMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.flow.BidSectionStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.flow.ProjectStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.flow.TenderStatusEnum;
import cn.iocoder.txgy.module.bidding.service.attachment.AttachmentService;
import cn.iocoder.txgy.module.bidding.service.biznum.BiddingBizNoGenerator;
import cn.iocoder.txgy.module.bidding.service.biznum.BiddingBizNoTypeEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;

@Slf4j
@Service
public class ProjectServiceImpl implements ProjectService {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private TenderProjectMapper tenderProjectMapper;
    @Resource
    private BidSectionMapper bidSectionMapper;
    /** 内部项目编号生成器：仅 projectCode 缺省时启用；外部项目仍登记外部权威编号 */
    @Resource
    private BiddingBizNoGenerator biddingBizNoGenerator;
    /** 通用附件服务（契约29 §2.7：四实体附件 VO 接线 —— save 一并绑定、详情出参） */
    @Resource
    private AttachmentService attachmentService;
    /** 时间驱动调度注册（投标开始/截止时间配置即注册，精确到秒） */

    // ==================== 项目 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveProject(ProjectSaveReqVO reqVO) {
        // 外部项目由交易平台给 projectCode；纯内部管理项目没有外部编号时，平台按规范生成 I 段内部编号。
        // 生成后回写入参，保证主列、payload 快照、唯一性查询使用同一个编号。
        if (!StringUtils.hasText(reqVO.getProjectCode())) {
            if (reqVO.getId() != null) {
                // 编辑（带 id）未带编号：沿用库内原编号，避免每次编辑被重新编号（追溯以编号为键，改号会断链）
                ProjectDO origin = projectMapper.selectById(reqVO.getId());
                if (origin != null && StringUtils.hasText(origin.getProjectCode())) {
                    reqVO.setProjectCode(origin.getProjectCode());
                } else {
                    reqVO.setProjectCode(biddingBizNoGenerator.generate(BiddingBizNoTypeEnum.PROJECT, reqVO.getRegionCode()));
                }
            } else {
                // 新建：生成内部编号
                reqVO.setProjectCode(biddingBizNoGenerator.generate(BiddingBizNoTypeEnum.PROJECT, reqVO.getRegionCode()));
            }
        }
        LinkedHashMap<String, Object> canonical = ProjectPayloadUtils.canonicalize(ProjectVO.class, reqVO.getPayload());
        // 主列与数据集同名字段保持一致（列表/追溯走主列，fields_json 留全量；缺失的主列以 payload 回填）
        fillMainFromPayload(canonical, reqVO.getProjectCode(), "PROJECT_CODE");
        fillMainFromPayload(canonical, reqVO.getInvestProjectCode(), "INVEST_PROJECT_CODE");
        // [草稿宽松 2026-09-27 契约29 §3.4] 草稿(DRAFT)不做必填校验（新建即落库空白草稿，仅系统生成编号）；
        //   必填唯一强制点 = 状态流转事件（提交审批/发布前）。非草稿（ACTIVE/CLOSED 等）仍按规范强制
        //   PROJECT_CODE/PROJECT_NAME/INVEST_PROJECT_CODE（24 位投资代码审批通过后登记）。
        String projectState = StringUtils.hasText(reqVO.getState()) ? reqVO.getState() : "DRAFT";
        if (ProjectStatusEnum.DRAFT == ProjectStatusEnum.of(projectState)) {
            checkRequired(ProjectPayloadUtils.missingRequired(canonical, "PROJECT_CODE"), "项目草稿");
        } else {
            checkRequired(ProjectPayloadUtils.missingRequired(canonical, "PROJECT_CODE", "PROJECT_NAME", "INVEST_PROJECT_CODE"), "项目");
        }
        ProjectDO existing = selectProjectByCode(reqVO.getProjectCode());
        if (existing != null && reqVO.getId() == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PROJECT_CODE_DUPLICATE);
        }
        ProjectDO entity = BeanUtils.toBean(reqVO, ProjectDO.class);
        if (existing != null) {
            entity.setId(existing.getId()); // 幂等 upsert：同编号视为同一主档的更新
        }
        entity.setFieldsJson(ProjectPayloadUtils.writeJson(canonical));
        if (!StringUtils.hasText(entity.getState())) {
            // 规范 26：新建默认草稿（DRAFT），经审批（状态流转服务）推进；存量 ACTIVE 数据兼容
            entity.setState("DRAFT");
        }
        if (entity.getId() == null) {
            projectMapper.insert(entity);
        } else {
            projectMapper.updateById(entity);
        }
        // 附件接线（契约29 §2.7）：保存后一并绑定（幂等；fileId 为一致性依据）
        bindAttachments("PROJECT", entity.getProjectCode(), reqVO.getAttachments());
        return entity.getId();
    }

    @Override
    public PageResult<ProjectRespVO> pageProject(ProjectPageReqVO reqVO) {
        PageResult<ProjectDO> page = projectMapper.selectPage(reqVO, new LambdaQueryWrapper<ProjectDO>()
                .likeRight(StringUtils.hasText(reqVO.getProjectCode()), ProjectDO::getProjectCode, reqVO.getProjectCode())
                // [契约 B5，2026-10-02] 投资项目统一代码（项目代码）前缀匹配，与 projectCode 同风格
                .likeRight(StringUtils.hasText(reqVO.getInvestProjectCode()), ProjectDO::getInvestProjectCode,
                        reqVO.getInvestProjectCode())
                .like(StringUtils.hasText(reqVO.getProjectName()), ProjectDO::getProjectName, reqVO.getProjectName())
                .eq(StringUtils.hasText(reqVO.getRegionCode()), ProjectDO::getRegionCode, reqVO.getRegionCode())
                .eq(StringUtils.hasText(reqVO.getIndustriesType()), ProjectDO::getIndustriesType, reqVO.getIndustriesType())
                .eq(StringUtils.hasText(reqVO.getState()), ProjectDO::getState, reqVO.getState())
                .orderByDesc(ProjectDO::getId));
        return new PageResult<>(toProjectRespList(page.getList()), page.getTotal());
    }

    @Override
    public ProjectRespVO getProject(Long id) {
        return getProject(id, false);
    }

    @Override
    public ProjectRespVO getProject(Long id, boolean includeGovernment) {
        ProjectDO entity = projectMapper.selectById(id);
        if (entity == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PROJECT_NOT_EXISTS);
        }
        return toProjectResp(entity, includeGovernment, attachmentService);
    }

    @Override
    public ProjectRespVO getProjectByCode(String projectCode) {
        return getProjectByCode(projectCode, false);
    }

    @Override
    public ProjectRespVO getProjectByCode(String projectCode, boolean includeGovernment) {
        ProjectDO entity = selectProjectByCode(projectCode);
        return entity == null ? null : toProjectResp(entity, includeGovernment, attachmentService);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProject(Long id) {
        ProjectDO entity = projectMapper.selectById(id);
        if (entity == null) {
            return;
        }
        Long children = tenderProjectMapper.selectCount(new LambdaQueryWrapper<TenderProjectDO>()
                .eq(TenderProjectDO::getProjectCode, entity.getProjectCode()));
        if (children != null && children > 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PROJECT_HAS_SECTIONS);
        }
        projectMapper.deleteById(id);
    }

    // ==================== 招标项目 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveTenderProject(TenderProjectSaveReqVO reqVO) {
        // 招标项目编号缺省生成：20 位 = 所属项目编号(17) + 3 位序列（T08/用户裁定：编号系统生成，前端不输入不显示）
        if (!StringUtils.hasText(reqVO.getTenderProjectCode())) {
            if (reqVO.getId() != null) {
                TenderProjectDO origin = tenderProjectMapper.selectById(reqVO.getId());
                if (origin != null && StringUtils.hasText(origin.getTenderProjectCode())) {
                    // 编辑未带编号：沿用库内原编号，避免每次编辑被重新编号（追溯以编号为键，改号会断链）
                    reqVO.setTenderProjectCode(origin.getTenderProjectCode());
                } else {
                    reqVO.setTenderProjectCode(generateTenderProjectCode(reqVO));
                }
            } else {
                reqVO.setTenderProjectCode(generateTenderProjectCode(reqVO));
            }
        }
        LinkedHashMap<String, Object> canonical = ProjectPayloadUtils.canonicalize(TenderProjectVO.class, reqVO.getPayload());
        fillMainFromPayload(canonical, reqVO.getTenderProjectCode(), "TENDER_PROJECT_CODE");
        // [草稿宽松 2026-09-27 契约29 §3.4] 草稿(DRAFT)不做必填校验；必填强制点 = 状态流转事件。
        String tenderState = StringUtils.hasText(reqVO.getState()) ? reqVO.getState() : "DRAFT";
        if (TenderStatusEnum.DRAFT == TenderStatusEnum.of(tenderState)) {
            checkRequired(ProjectPayloadUtils.missingRequired(canonical, "TENDER_PROJECT_CODE"), "招标项目草稿");
        } else {
            checkRequired(ProjectPayloadUtils.missingRequired(canonical, "TENDER_PROJECT_CODE", "TENDER_PROJECT_NAME"), "招标项目");
        }
        TenderProjectDO existing = selectTenderProjectByCode(reqVO.getTenderProjectCode());
        if (existing != null && reqVO.getId() == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.TENDER_PROJECT_CODE_DUPLICATE);
        }
        TenderProjectDO entity = BeanUtils.toBean(reqVO, TenderProjectDO.class);
        if (existing != null) {
            entity.setId(existing.getId());
        }
        entity.setFieldsJson(ProjectPayloadUtils.writeJson(canonical));
        if (!StringUtils.hasText(entity.getState())) {
            // 规范 26：新建默认草稿（DRAFT），经审批（状态流转服务）推进；存量 ACTIVE 数据兼容
            entity.setState("DRAFT");
        }
        if (entity.getId() == null) {
            tenderProjectMapper.insert(entity);
        } else {
            tenderProjectMapper.updateById(entity);
        }
        // 附件接线（契约29 §2.7）
        bindAttachments("TENDER_PROJECT", entity.getTenderProjectCode(), reqVO.getAttachments());
        return entity.getId();
    }

    @Override
    public PageResult<TenderProjectRespVO> pageTenderProject(TenderProjectPageReqVO reqVO) {
        PageResult<TenderProjectDO> page = tenderProjectMapper.selectPage(reqVO, new LambdaQueryWrapper<TenderProjectDO>()
                .likeRight(StringUtils.hasText(reqVO.getTenderProjectCode()), TenderProjectDO::getTenderProjectCode, reqVO.getTenderProjectCode())
                .like(StringUtils.hasText(reqVO.getTenderProjectName()), TenderProjectDO::getTenderProjectName, reqVO.getTenderProjectName())
                .eq(StringUtils.hasText(reqVO.getProjectCode()), TenderProjectDO::getProjectCode, reqVO.getProjectCode())
                .like(StringUtils.hasText(reqVO.getTendererName()), TenderProjectDO::getTendererName, reqVO.getTendererName())
                .eq(StringUtils.hasText(reqVO.getTenderMode()), TenderProjectDO::getTenderMode, reqVO.getTenderMode())
                .eq(StringUtils.hasText(reqVO.getState()), TenderProjectDO::getState, reqVO.getState())
                .orderByDesc(TenderProjectDO::getId));
        return new PageResult<>(toTenderRespList(page.getList()), page.getTotal());
    }

    @Override
    public TenderProjectRespVO getTenderProject(Long id) {
        TenderProjectDO entity = tenderProjectMapper.selectById(id);
        if (entity == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.TENDER_PROJECT_NOT_EXISTS);
        }
        return toTenderResp(entity, attachmentService);
    }

    @Override
    public TenderProjectRespVO getTenderProjectByCode(String tenderProjectCode) {
        TenderProjectDO entity = selectTenderProjectByCode(tenderProjectCode);
        return entity == null ? null : toTenderResp(entity, attachmentService);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTenderProject(Long id) {
        TenderProjectDO entity = tenderProjectMapper.selectById(id);
        if (entity == null) {
            return;
        }
        Long children = bidSectionMapper.selectCount(new LambdaQueryWrapper<BidSectionDO>()
                .eq(BidSectionDO::getTenderProjectCode, entity.getTenderProjectCode()));
        if (children != null && children > 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PROJECT_HAS_SECTIONS);
        }
        tenderProjectMapper.deleteById(id);
    }

    // ==================== 标段 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveBidSection(BidSectionSaveReqVO reqVO) {
        // 追溯链完整性：标段必须挂在已登记的招标项目下（否则编号存在但上级缺失）
        TenderProjectDO parent = selectTenderProjectByCode(reqVO.getTenderProjectCode());
        if (parent == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.BID_SECTION_TENDER_PROJECT_REQUIRED);
        }
        // 标段编号缺省生成：23 位 = 所属招标项目编号(20) + 3 位序列（T08/用户裁定：编号系统生成，前端不输入不显示）
        if (!StringUtils.hasText(reqVO.getBidSectionCode())) {
            if (reqVO.getId() != null) {
                BidSectionDO origin = bidSectionMapper.selectById(reqVO.getId());
                if (origin != null && StringUtils.hasText(origin.getBidSectionCode())) {
                    // 编辑未带编号：沿用库内原编号
                    reqVO.setBidSectionCode(origin.getBidSectionCode());
                } else {
                    reqVO.setBidSectionCode(biddingBizNoGenerator.generateChild(BiddingBizNoTypeEnum.BID_SECTION, reqVO.getTenderProjectCode()));
                }
            } else {
                reqVO.setBidSectionCode(biddingBizNoGenerator.generateChild(BiddingBizNoTypeEnum.BID_SECTION, reqVO.getTenderProjectCode()));
            }
        }
        LinkedHashMap<String, Object> canonical = ProjectPayloadUtils.canonicalize(BidSectionVO.class, reqVO.getPayload());
        // 标段编号由后端生成（23 位），回填 canonical 保证必填校验与 fields_json 快照一致
        fillMainFromPayload(canonical, reqVO.getBidSectionCode(), "BID_SECTION_CODE");
        // [草稿宽松 2026-09-27 契约29 §3.4] 草稿(DRAFT)不做必填校验（上级招标项目归属仍强校验，保追溯链）；
        //   标段名称必填强制点 = 状态流转事件。
        String sectionState = StringUtils.hasText(reqVO.getState()) ? reqVO.getState() : "DRAFT";
        if (BidSectionStatusEnum.DRAFT == BidSectionStatusEnum.of(sectionState)) {
            checkRequired(ProjectPayloadUtils.missingRequired(canonical, "BID_SECTION_CODE"), "标段（包）草稿");
        } else {
            checkRequired(ProjectPayloadUtils.missingRequired(canonical, "BID_SECTION_CODE", "BID_SECTION_NAME"), "标段（包）");
        }
        BidSectionDO existing = selectBidSectionByCode(reqVO.getBidSectionCode());
        if (existing != null && reqVO.getId() == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.BID_SECTION_CODE_DUPLICATE);
        }
        BidSectionDO entity = BeanUtils.toBean(reqVO, BidSectionDO.class);
        if (existing != null) {
            entity.setId(existing.getId());
        }
        entity.setFieldsJson(ProjectPayloadUtils.writeJson(canonical));
        if (!StringUtils.hasText(entity.getState())) {
            // 规范 26：新建默认草稿（DRAFT），经审批（状态流转服务）推进；存量 ACTIVE 数据兼容
            entity.setState("DRAFT");
        }
        if (entity.getId() == null) {
            bidSectionMapper.insert(entity);
        } else {
            bidSectionMapper.updateById(entity);
        }
        // 附件接线（契约29 §2.7）
        bindAttachments("BID_SECTION", entity.getBidSectionCode(), reqVO.getAttachments());
        return entity.getId();
    }

    @Override
    public PageResult<BidSectionRespVO> pageBidSection(BidSectionPageReqVO reqVO) {
        PageResult<BidSectionDO> page = bidSectionMapper.selectPage(reqVO, new LambdaQueryWrapper<BidSectionDO>()
                .likeRight(StringUtils.hasText(reqVO.getBidSectionCode()), BidSectionDO::getBidSectionCode, reqVO.getBidSectionCode())
                .like(StringUtils.hasText(reqVO.getBidSectionName()), BidSectionDO::getBidSectionName, reqVO.getBidSectionName())
                .eq(StringUtils.hasText(reqVO.getTenderProjectCode()), BidSectionDO::getTenderProjectCode, reqVO.getTenderProjectCode())
                .eq(StringUtils.hasText(reqVO.getState()), BidSectionDO::getState, reqVO.getState())
                .orderByDesc(BidSectionDO::getId));
        return new PageResult<>(toSectionRespList(page.getList()), page.getTotal());
    }

    @Override
    public BidSectionRespVO getBidSection(Long id) {
        BidSectionDO entity = bidSectionMapper.selectById(id);
        if (entity == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.BID_SECTION_NOT_EXISTS);
        }
        return toSectionResp(entity, attachmentService);
    }

    @Override
    public BidSectionRespVO getBidSectionByCode(String bidSectionCode) {
        BidSectionDO entity = selectBidSectionByCode(bidSectionCode);
        return entity == null ? null : toSectionResp(entity, attachmentService);
    }

    // ==================== 内部工具 ====================

    /** 招标项目编号生成：须先挂接所属项目（层级编号 = 所属项目编号 + 3 位序列） */
    private String generateTenderProjectCode(TenderProjectSaveReqVO reqVO) {
        if (!StringUtils.hasText(reqVO.getProjectCode())) {
            throw ServiceExceptionUtil.invalidParamException("招标项目须挂接所属项目（projectCode）后才能生成招标项目编号");
        }
        return biddingBizNoGenerator.generateChild(BiddingBizNoTypeEnum.TENDER_PROJECT, reqVO.getProjectCode());
    }

    /** 主列与 payload 同名字段对齐：payload 有值而主列为空时回填（保证主列与全量快照一致） */
    private void fillMainFromPayload(LinkedHashMap<String, Object> canonical, String mainValue, String code) {
        if (StringUtils.hasText(mainValue)) {
            canonical.put(code, mainValue);
        }
    }

    private void checkRequired(List<String> missing, String subject) {
        if (!missing.isEmpty()) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.NOTICE_PAYLOAD_INVALID,
                    subject + "必填字段缺失（依据数据规范 V2.0 必填=是）：" + String.join("、", missing));
        }
    }

    /**
     * 附件接线（契约29 §2.7）：把 Save 入参 attachments 逐条转为 AttachmentSaveReqVO 并绑定。
     * <p>幂等由 AttachmentService.save 保证（同一 bizType+bizCode+fileId 返回原行，不重复建行）；
     * fileId 为一致性依据，url/fileName/sha256 等元数据由对象存储按 fileId 回查，不入参。
     */
    private void bindAttachments(String bizType, String bizCode, List<AttachmentBindItemVO> items) {
        if (items == null || items.isEmpty() || !StringUtils.hasText(bizCode)) {
            return;
        }
        for (AttachmentBindItemVO item : items) {
            if (item == null || item.getFileId() == null) {
                continue;
            }
            AttachmentSaveReqVO saveReq = new AttachmentSaveReqVO();
            saveReq.setBizType(bizType);
            saveReq.setBizCode(bizCode);
            saveReq.setFileId(item.getFileId());
            saveReq.setAttachmentType(item.getAttachmentType());
            saveReq.setAttachmentName(item.getAttachmentName());
            saveReq.setRemark(item.getRemark());
            attachmentService.save(saveReq);
        }
    }

    private ProjectDO selectProjectByCode(String code) {
        return StringUtils.hasText(code) ? projectMapper.selectOne(new LambdaQueryWrapper<ProjectDO>()
                .eq(ProjectDO::getProjectCode, code).last("LIMIT 1")) : null;
    }

    private TenderProjectDO selectTenderProjectByCode(String code) {
        return StringUtils.hasText(code) ? tenderProjectMapper.selectOne(new LambdaQueryWrapper<TenderProjectDO>()
                .eq(TenderProjectDO::getTenderProjectCode, code).last("LIMIT 1")) : null;
    }

    private BidSectionDO selectBidSectionByCode(String code) {
        return StringUtils.hasText(code) ? bidSectionMapper.selectOne(new LambdaQueryWrapper<BidSectionDO>()
                .eq(BidSectionDO::getBidSectionCode, code).last("LIMIT 1")) : null;
    }

    /** DO → 出参（对外口径：fields 按 @PublicLevel(SOCIAL) 裁剪；createTime 转 epoch 毫秒；附件按契约29 §2.7 出参） */
    static ProjectRespVO toProjectResp(ProjectDO entity, AttachmentService attachmentService) {
        return toProjectResp(entity, false, attachmentService);
    }

    /**
     * DO → 出参（契约《管理端出参放开政务字段》方案 A ＋ 契约29 §2.7 附件出参）。
     *
     * @param includeGovernment true=管理端填报视角，返回全部非空字段（含政务公开级）；
     *                          false=对外口径（缺省，保持大厅/门户行为不变）
     * @param attachmentService 附件域服务（出参带 attachments；为 null 时不装配）
     */
    static ProjectRespVO toProjectResp(ProjectDO entity, boolean includeGovernment, AttachmentService attachmentService) {
        ProjectRespVO vo = BeanUtils.toBean(entity, ProjectRespVO.class);
        LinkedHashMap<String, Object> canonical = parseFields(entity.getFieldsJson());
        vo.setFields(includeGovernment
                ? ProjectPayloadUtils.fullSubset(ProjectVO.class, canonical)
                : ProjectPayloadUtils.socialSubset(ProjectVO.class, canonical));
        if (attachmentService != null) {
            vo.setAttachments(attachmentService.list("PROJECT", entity.getProjectCode()));
        }
        vo.setCreateTime(epoch(entity.getCreateTime()));
        return vo;
    }

    private List<ProjectRespVO> toProjectRespList(List<ProjectDO> list) {
        return list == null ? List.of() : list.stream().map(e -> toProjectResp(e, attachmentService)).toList();
    }

    static TenderProjectRespVO toTenderResp(TenderProjectDO entity, AttachmentService attachmentService) {
        return toTenderResp(entity, false, attachmentService);
    }

    /** 招标项目 DO → 出参（includeGovernment 语义同 {@link #toProjectResp(ProjectDO, boolean, AttachmentService)}）。 */
    static TenderProjectRespVO toTenderResp(TenderProjectDO entity, boolean includeGovernment, AttachmentService attachmentService) {
        TenderProjectRespVO vo = BeanUtils.toBean(entity, TenderProjectRespVO.class);
        LinkedHashMap<String, Object> canonical = parseFields(entity.getFieldsJson());
        vo.setFields(includeGovernment
                ? ProjectPayloadUtils.fullSubset(TenderProjectVO.class, canonical)
                : ProjectPayloadUtils.socialSubset(TenderProjectVO.class, canonical));
        if (attachmentService != null) {
            vo.setAttachments(attachmentService.list("TENDER_PROJECT", entity.getTenderProjectCode()));
        }
        vo.setCreateTime(epoch(entity.getCreateTime()));
        return vo;
    }

    private List<TenderProjectRespVO> toTenderRespList(List<TenderProjectDO> list) {
        return list == null ? List.of() : list.stream().map(e -> toTenderResp(e, attachmentService)).toList();
    }

    static BidSectionRespVO toSectionResp(BidSectionDO entity, AttachmentService attachmentService) {
        return toSectionResp(entity, false, attachmentService);
    }

    /** 标段 DO → 出参（includeGovernment 语义同 {@link #toProjectResp(ProjectDO, boolean, AttachmentService)}）。 */
    static BidSectionRespVO toSectionResp(BidSectionDO entity, boolean includeGovernment, AttachmentService attachmentService) {
        BidSectionRespVO vo = BeanUtils.toBean(entity, BidSectionRespVO.class);
        LinkedHashMap<String, Object> canonical = parseFields(entity.getFieldsJson());
        vo.setFields(includeGovernment
                ? ProjectPayloadUtils.fullSubset(BidSectionVO.class, canonical)
                : ProjectPayloadUtils.socialSubset(BidSectionVO.class, canonical));
        if (attachmentService != null) {
            vo.setAttachments(attachmentService.list("BID_SECTION", entity.getBidSectionCode()));
        }
        vo.setCreateTime(epoch(entity.getCreateTime()));
        return vo;
    }

    private List<BidSectionRespVO> toSectionRespList(List<BidSectionDO> list) {
        return list == null ? List.of() : list.stream().map(e -> toSectionResp(e, attachmentService)).toList();
    }

    /** 供 BeanUtils 拷贝后使用：从 DO 取 fieldsJson 反序列化（三域共用） */
    @SuppressWarnings("unchecked")
    static LinkedHashMap<String, Object> parseFields(String fieldsJson) {
        if (!StringUtils.hasText(fieldsJson)) {
            return new LinkedHashMap<>();
        }
        try {
            return JSON.readValue(fieldsJson, LinkedHashMap.class);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    static Long epoch(java.time.LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
