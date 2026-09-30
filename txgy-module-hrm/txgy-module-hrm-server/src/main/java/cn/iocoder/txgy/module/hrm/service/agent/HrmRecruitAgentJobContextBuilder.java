package cn.iocoder.txgy.module.hrm.service.agent;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.dict.core.DictFrameworkUtils;
import cn.iocoder.txgy.framework.ip.core.utils.AreaUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo.HrmPortalRecruitAgentChatJobVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.post.HrmRecruitPostPageReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.personal.resume.HrmPersonalResumeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.company.HrmRecruitCompanyProfileDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.post.HrmRecruitPostDO;
import cn.iocoder.txgy.module.hrm.enums.DictTypeConstants;
import cn.iocoder.txgy.module.hrm.service.personal.resume.HrmPersonalResumeService;
import cn.iocoder.txgy.module.hrm.service.recruit.company.HrmRecruitCompanyProfileService;
import cn.iocoder.txgy.module.hrm.service.recruit.post.HrmRecruitPostService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 求职助理的职位上下文。
 *
 * <p>首页那轮自由对话原先只把用户那句话发给模型，服务端一条职位都没查，
 * 于是模型只会回「我这边看不到任何在招职位」—— 它说的是实话，
 * 但一个求职助理找不到工作，这个入口就是残的。</p>
 *
 * <p>这里在调模型之前先查一次职位表，按本人的求职意向预筛，拼成一段纯文本
 * 连同用户原话一起发过去。查询走门户职位分页那条既有链路
 * （{@link HrmRecruitPostService#getPortalRecruitPostPage}），
 * 它内部已经处理了两件不能绕过的事：只公开已发布且由 HR 账号发布的职位，
 * 以及把二级分类展开成挂在它下面的三级工种 —— 求职资料里存的期望职位是二级分类，
 * 不展开的话一个岗位都匹配不到。</p>
 *
 * <p>不做工具调用：模型适配层目前只支持「系统提示 + 一段用户内容」，
 * 加工具是另一件事；预筛一次的成本只是一条普通分页查询。</p>
 */
@Slf4j
@Component
public class HrmRecruitAgentJobContextBuilder {

    /**
     * 清单条数上限。
     *
     * <p>给多了 token 涨、回答变啰嗦；给少了「陕西的岗位」可能刚好不在前几条里。
     * 所以提示词里必须写明这是「最相关的若干条，不是全部」，
     * 否则模型会把「清单里没有」说成「没有这个岗位」。</p>
     */
    private static final int MAX_JOBS = 10;

    /** 与在线简历页写入的标题一致，见 HrmPersonalResumeServiceImpl */
    private static final String RECRUIT_PROFILE_TITLE = "求职资料（系统）";

    @Resource
    private HrmRecruitPostService postService;
    @Resource
    private HrmPersonalResumeService personalResumeService;
    @Resource
    private HrmRecruitCompanyProfileService companyProfileService;

    /**
     * 清单文本 + 同一批岗位的结构化形式。
     *
     * <p>文本是给模型看的 —— 它没法读 JSON 结构去理解「这是什么」；
     * 结构化那批是给前端渲染卡片的。两者必须来自同一次查询：
     * 分两次查会飘，模型嘴里说的岗位和卡片上列的对不上。</p>
     */
    public record JobContext(String text, List<HrmPortalRecruitAgentChatJobVO> jobs) {
    }

    /**
     * 拼一段给模型看的职位上下文，同时把结构化的岗位带回去。
     *
     * <p>文本永远非空：没有匹配时写「（没有匹配到职位）」，
     * 让模型看到的结构保持一致，避免它自己去猜是「查不到」还是「没给」。</p>
     */
    public JobContext build(Long userId) {
        JSONObject profile = readProfile(userId);
        Long postTypeId = readPostTypeId(profile);
        Integer areaId = readFirstCityAreaId(profile);

        HrmRecruitPostPageReqVO reqVO = new HrmRecruitPostPageReqVO();
        reqVO.setPageNo(1);
        reqVO.setPageSize(MAX_JOBS);
        reqVO.setPostTypeId(postTypeId);
        reqVO.setAreaId(areaId);

        List<HrmRecruitPostDO> posts;
        try {
            PageResult<HrmRecruitPostDO> page = postService.getPortalRecruitPostPage(reqVO);
            posts = page.getList();
        } catch (RuntimeException ex) {
            // 一次查询失败不该把整轮对话打挂：按空清单继续，模型会如实说没找到
            log.warn("[hrm][agent] 求职助理取职位清单失败，按空清单继续", ex);
            posts = List.of();
        }

        StringBuilder sb = new StringBuilder();
        appendPreference(sb, profile);
        // 同一批职位基本同属一家公司，按租户缓存一次，省掉每条一次企业档案查询；
        // 文本和卡片共用这份缓存，两边显示的公司名必然一致
        Map<Long, String> companyNames = new HashMap<>();
        appendJobs(sb, posts, companyNames);
        return new JobContext(sb.toString(), convertJobs(posts, companyNames));
    }

    private List<HrmPortalRecruitAgentChatJobVO> convertJobs(
            List<HrmRecruitPostDO> posts, Map<Long, String> companyNames) {
        List<HrmPortalRecruitAgentChatJobVO> jobs = new ArrayList<>(posts.size());
        for (HrmRecruitPostDO post : posts) {
            jobs.add(new HrmPortalRecruitAgentChatJobVO()
                    .setId(post.getId())
                    .setPostName(post.getPostName())
                    .setAreaName(post.getAreaId() == null ? null : AreaUtils.format(post.getAreaId()))
                    .setCompanyName(companyNames.computeIfAbsent(post.getTenantId(), this::readCompanyName))
                    .setEducation(DictFrameworkUtils.parseDictDataLabel(
                            DictTypeConstants.HRM_RECRUIT_POST_EDUCATION, post.getEducationRequire()))
                    .setExperience(DictFrameworkUtils.parseDictDataLabel(
                            DictTypeConstants.HRM_RECRUIT_WORK_TIME, post.getWorkTime()))
                    .setSalary(formatSalary(post)));
        }
        return jobs;
    }

    // ==================== 求职意向 ====================

    private JSONObject readProfile(Long userId) {
        try {
            List<HrmPersonalResumeDO> profiles = personalResumeService.getResumeList(userId);
            if (profiles == null) {
                return null;
            }
            HrmPersonalResumeDO profile = profiles.stream()
                    .filter(item -> RECRUIT_PROFILE_TITLE.equals(item.getTitle()))
                    .findFirst().orElse(null);
            if (profile == null || StrUtil.isBlank(profile.getContent())) {
                return null;
            }
            return JSONUtil.parseObj(profile.getContent());
        } catch (RuntimeException ex) {
            log.warn("[hrm][agent] 求职助理读取求职资料失败，按未填写处理", ex);
            return null;
        }
    }

    /** 期望职位。新写入的是 expectedPostId，老数据里可能只有门户页写的 postTypeId */
    private Long readPostTypeId(JSONObject profile) {
        if (profile == null) {
            return null;
        }
        Long id = Convert.toLong(profile.get("expectedPostId"), null);
        return id != null ? id : Convert.toLong(profile.get("postTypeId"), null);
    }

    /** 意向城市取第一个。多选时其余城市不参与预筛，提示词里会带上完整意向让模型自己判断 */
    private Integer readFirstCityAreaId(JSONObject profile) {
        if (profile == null) {
            return null;
        }
        JSONArray ids = profile.getJSONArray("expectedCityAreaIds");
        if (ids == null) {
            return null;
        }
        for (Object raw : ids) {
            Integer id = Convert.toInt(raw, null);
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    private void appendPreference(StringBuilder sb, JSONObject profile) {
        sb.append("【本人的求职意向（来自求职资料）】\n");
        if (profile == null) {
            sb.append("（求职资料尚未填写）");
            return;
        }
        sb.append("期望职位：").append(StrUtil.blankToDefault(profile.getStr("expectedPostName"), "未填写"));
        sb.append("\n意向城市：").append(StrUtil.blankToDefault(readCities(profile), "未填写"));
    }

    private String readCities(JSONObject profile) {
        JSONArray cities = profile.getJSONArray("expectedCities");
        if (cities == null || cities.isEmpty()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (Object raw : cities) {
            String name = Convert.toStr(raw, null);
            if (StrUtil.isNotBlank(name)) {
                names.add(name);
            }
        }
        return names.isEmpty() ? null : String.join("、", names);
    }

    // ==================== 职位清单 ====================

    private void appendJobs(StringBuilder sb, List<HrmRecruitPostDO> posts, Map<Long, String> companyNames) {
        sb.append("\n\n【当前在招职位】按上面的求职意向预筛，最多 ").append(MAX_JOBS)
                .append(" 条，不是全部在招岗位\n");
        if (posts.isEmpty()) {
            sb.append("（没有匹配到职位）");
            return;
        }
        for (int i = 0; i < posts.size(); i++) {
            sb.append(i + 1).append(". ").append(renderJob(posts.get(i), companyNames)).append('\n');
        }
    }

    private String renderJob(HrmRecruitPostDO post, Map<Long, String> companyNames) {
        List<String> parts = new ArrayList<>();
        parts.add(StrUtil.blankToDefault(post.getPostName(), "未命名职位"));
        parts.add(post.getAreaId() == null ? null : AreaUtils.format(post.getAreaId()));
        parts.add(DictFrameworkUtils.parseDictDataLabel(
                DictTypeConstants.HRM_RECRUIT_POST_EDUCATION, post.getEducationRequire()));
        parts.add(DictFrameworkUtils.parseDictDataLabel(
                DictTypeConstants.HRM_RECRUIT_WORK_TIME, post.getWorkTime()));
        parts.add(formatSalary(post));
        parts.add(companyNames.computeIfAbsent(post.getTenantId(), this::readCompanyName));
        return parts.stream().filter(StrUtil::isNotBlank).reduce((a, b) -> a + "｜" + b).orElse("未命名职位");
    }

    private String readCompanyName(Long tenantId) {
        if (tenantId == null) {
            return null;
        }
        try {
            HrmRecruitCompanyProfileDO company = companyProfileService.getPortalProfile(tenantId);
            return company == null ? null : company.getName();
        } catch (RuntimeException ex) {
            // 企业档案读不到就少写这一项，不要让整条清单挂掉
            log.warn("[hrm][agent] 求职助理读取企业档案失败：tenantId({})", tenantId, ex);
            return null;
        }
    }

    /**
     * 薪资文案，口径与门户职位页一致。
     *
     * <p>salaryUnit 为 -1 表示面议；minSalary 为空或为负也按面议处理。
     * 数值单位是「千元」，所以要补 K —— 门户页的 formatSalary 也是这么写的。</p>
     */
    private String formatSalary(HrmRecruitPostDO post) {
        if (post.getSalaryUnit() != null && post.getSalaryUnit() == -1) {
            return "面议";
        }
        BigDecimal min = post.getMinSalary();
        if (min == null || min.signum() < 0) {
            return "面议";
        }
        BigDecimal max = post.getMaxSalary();
        if (max != null && max.signum() >= 0 && max.compareTo(min) != 0) {
            return plain(min) + "-" + plain(max) + "K";
        }
        return plain(min) + "K 起";
    }

    /** BigDecimal 直接拼会带出 8.00 这种尾零，模型会照抄进回答里 */
    private String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

}
