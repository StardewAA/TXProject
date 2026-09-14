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
import cn.iocoder.txgy.module.bidding.controller.admin.project.vo.*;
import cn.iocoder.txgy.module.bidding.dal.dataobject.project.BidSectionDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.project.ProjectDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.project.TenderProjectDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.project.BidSectionMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.project.ProjectMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.project.TenderProjectMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
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

    // ==================== 项目 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveProject(ProjectSaveReqVO reqVO) {
        LinkedHashMap<String, Object> canonical = ProjectPayloadUtils.canonicalize(ProjectVO.class, reqVO.getPayload());
        // 主列与数据集同名字段保持一致（列表/追溯走主列，fields_json 留全量；缺失的主列以 payload 回填）
        fillMainFromPayload(canonical, reqVO.getProjectCode(), "PROJECT_CODE");
        checkRequired(ProjectPayloadUtils.missingRequired(canonical, "PROJECT_CODE", "PROJECT_NAME"), "项目");
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
            entity.setState("ACTIVE");
        }
        if (entity.getId() == null) {
            projectMapper.insert(entity);
        } else {
            projectMapper.updateById(entity);
        }
        return entity.getId();
    }

    @Override
    public PageResult<ProjectRespVO> pageProject(ProjectPageReqVO reqVO) {
        PageResult<ProjectDO> page = projectMapper.selectPage(reqVO, new LambdaQueryWrapper<ProjectDO>()
                .likeRight(StringUtils.hasText(reqVO.getProjectCode()), ProjectDO::getProjectCode, reqVO.getProjectCode())
                .like(StringUtils.hasText(reqVO.getProjectName()), ProjectDO::getProjectName, reqVO.getProjectName())
                .eq(StringUtils.hasText(reqVO.getRegionCode()), ProjectDO::getRegionCode, reqVO.getRegionCode())
                .eq(StringUtils.hasText(reqVO.getIndustriesType()), ProjectDO::getIndustriesType, reqVO.getIndustriesType())
                .eq(StringUtils.hasText(reqVO.getState()), ProjectDO::getState, reqVO.getState())
                .orderByDesc(ProjectDO::getId));
        return new PageResult<>(toProjectRespList(page.getList()), page.getTotal());
    }

    @Override
    public ProjectRespVO getProject(Long id) {
        ProjectDO entity = projectMapper.selectById(id);
        if (entity == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PROJECT_NOT_EXISTS);
        }
        return toProjectResp(entity);
    }

    @Override
    public ProjectRespVO getProjectByCode(String projectCode) {
        ProjectDO entity = selectProjectByCode(projectCode);
        return entity == null ? null : toProjectResp(entity);
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
        LinkedHashMap<String, Object> canonical = ProjectPayloadUtils.canonicalize(TenderProjectVO.class, reqVO.getPayload());
        fillMainFromPayload(canonical, reqVO.getTenderProjectCode(), "TENDER_PROJECT_CODE");
        checkRequired(ProjectPayloadUtils.missingRequired(canonical, "TENDER_PROJECT_CODE", "TENDER_PROJECT_NAME"), "招标项目");
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
            entity.setState("ACTIVE");
        }
        if (entity.getId() == null) {
            tenderProjectMapper.insert(entity);
        } else {
            tenderProjectMapper.updateById(entity);
        }
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
        return toTenderResp(entity);
    }

    @Override
    public TenderProjectRespVO getTenderProjectByCode(String tenderProjectCode) {
        TenderProjectDO entity = selectTenderProjectByCode(tenderProjectCode);
        return entity == null ? null : toTenderResp(entity);
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
        LinkedHashMap<String, Object> canonical = ProjectPayloadUtils.canonicalize(BidSectionVO.class, reqVO.getPayload());
        checkRequired(ProjectPayloadUtils.missingRequired(canonical, "BID_SECTION_CODE", "BID_SECTION_NAME"), "标段（包）");
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
            entity.setState("ACTIVE");
        }
        if (entity.getId() == null) {
            bidSectionMapper.insert(entity);
        } else {
            bidSectionMapper.updateById(entity);
        }
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
        return toSectionResp(entity);
    }

    @Override
    public BidSectionRespVO getBidSectionByCode(String bidSectionCode) {
        BidSectionDO entity = selectBidSectionByCode(bidSectionCode);
        return entity == null ? null : toSectionResp(entity);
    }

    // ==================== 内部工具 ====================

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

    /** DO → 出参（fields 取库内全量快照并按 @PublicLevel(SOCIAL) 裁剪；createTime 转 epoch 毫秒） */
    static ProjectRespVO toProjectResp(ProjectDO entity) {
        ProjectRespVO vo = BeanUtils.toBean(entity, ProjectRespVO.class);
        vo.setFields(ProjectPayloadUtils.socialSubset(ProjectVO.class, parseFields(entity.getFieldsJson())));
        vo.setCreateTime(epoch(entity.getCreateTime()));
        return vo;
    }

    private List<ProjectRespVO> toProjectRespList(List<ProjectDO> list) {
        return list == null ? List.of() : list.stream().map(ProjectServiceImpl::toProjectResp).toList();
    }

    static TenderProjectRespVO toTenderResp(TenderProjectDO entity) {
        TenderProjectRespVO vo = BeanUtils.toBean(entity, TenderProjectRespVO.class);
        vo.setFields(ProjectPayloadUtils.socialSubset(TenderProjectVO.class, parseFields(entity.getFieldsJson())));
        vo.setCreateTime(epoch(entity.getCreateTime()));
        return vo;
    }

    private List<TenderProjectRespVO> toTenderRespList(List<TenderProjectDO> list) {
        return list == null ? List.of() : list.stream().map(ProjectServiceImpl::toTenderResp).toList();
    }

    static BidSectionRespVO toSectionResp(BidSectionDO entity) {
        BidSectionRespVO vo = BeanUtils.toBean(entity, BidSectionRespVO.class);
        vo.setFields(ProjectPayloadUtils.socialSubset(BidSectionVO.class, parseFields(entity.getFieldsJson())));
        vo.setCreateTime(epoch(entity.getCreateTime()));
        return vo;
    }

    private List<BidSectionRespVO> toSectionRespList(List<BidSectionDO> list) {
        return list == null ? List.of() : list.stream().map(ProjectServiceImpl::toSectionResp).toList();
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
