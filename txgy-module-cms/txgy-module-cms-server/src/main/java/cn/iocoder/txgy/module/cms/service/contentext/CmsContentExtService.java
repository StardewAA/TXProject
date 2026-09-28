package cn.iocoder.txgy.module.cms.service.contentext;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 内容管理扩展：栏目树（含栏目级权限）、素材库、推荐位、SEO、审查任务。 */
@Service
@Slf4j
public class CmsContentExtService {

    private static final int MAX_PAGE_SIZE = 100;

    private final JdbcTemplate jdbc;

    public CmsContentExtService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==================== 栏目树 ====================

    /** 栏目树：按当前用户角色过滤（栏目级权限）。 */
    public List<Map<String, Object>> categoryTree() {
        long tenantId = tenant();
        List<Map<String, Object>> rows = jdbc.query("""
                SELECT id, parent_id AS parentId, name, code, content_type AS contentType, sort,
                       visible, status, visible_role_codes AS visibleRoleCodes, remark
                FROM cms_category WHERE tenant_id=? AND deleted=0 ORDER BY sort, id
                """, (rs, row) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rs.getLong("id"));
            item.put("parentId", rs.getLong("parentId"));
            item.put("name", rs.getString("name"));
            item.put("code", rs.getString("code"));
            item.put("contentType", rs.getString("contentType"));
            item.put("sort", rs.getInt("sort"));
            item.put("visible", rs.getBoolean("visible"));
            item.put("status", rs.getInt("status"));
            item.put("visibleRoleCodes", rs.getString("visibleRoleCodes"));
            item.put("remark", rs.getString("remark"));
            return item;
        }, tenantId);
        Set<String> myRoles = currentRoleCodes();
        rows.removeIf(item -> !categoryVisible(item, myRoles));
        return buildTree(rows, 0L);
    }

    private boolean categoryVisible(Map<String, Object> item, Set<String> myRoles) {
        String codes = (String) item.get("visibleRoleCodes");
        if (codes == null || codes.isBlank()) {
            return true;
        }
        if (myRoles.contains("super_admin")) {
            return true;
        }
        return Arrays.stream(codes.split(","))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .anyMatch(myRoles::contains);
    }

    private List<Map<String, Object>> buildTree(List<Map<String, Object>> rows, long parentId) {
        List<Map<String, Object>> tree = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (Long.valueOf(parentId).equals(row.get("parentId"))) {
                Map<String, Object> node = new LinkedHashMap<>(row);
                node.put("children", buildTree(rows, (Long) row.get("id")));
                tree.add(node);
            }
        }
        return tree;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long saveCategory(CategoryInput input) {
        long tenantId = tenant();
        String roleCodes = input.visibleRoleCodes() == null ? null
                : input.visibleRoleCodes().stream().filter(code -> code != null && !code.isBlank())
                        .collect(Collectors.joining(","));
        if (input.id() == null) {
            KeyHolder keys = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO cms_category
                          (tenant_id,parent_id,name,code,content_type,sort,visible,status,visible_role_codes,remark,creator)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?)
                        """, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, tenantId);
                ps.setLong(2, input.parentId() == null ? 0L : input.parentId());
                ps.setString(3, input.name());
                ps.setString(4, input.code());
                ps.setString(5, input.contentType());
                ps.setInt(6, input.sort() == null ? 0 : input.sort());
                ps.setBoolean(7, input.visible() == null || input.visible());
                ps.setInt(8, input.status() == null ? 0 : input.status());
                ps.setString(9, roleCodes);
                ps.setString(10, input.remark());
                ps.setString(11, nickname());
                return ps;
            }, keys);
            return keys.getKey().longValue();
        }
        jdbc.update("""
                UPDATE cms_category SET parent_id=?, name=?, code=?, content_type=?, sort=?, visible=?,
                       status=?, visible_role_codes=?, remark=?, updater=?
                WHERE tenant_id=? AND id=? AND deleted=0
                """, input.parentId() == null ? 0L : input.parentId(), input.name(), input.code(),
                input.contentType(), input.sort() == null ? 0 : input.sort(),
                input.visible() == null || input.visible(), input.status() == null ? 0 : input.status(),
                roleCodes, input.remark(), nickname(), tenantId, input.id());
        return input.id();
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Long id) {
        long tenantId = tenant();
        // 栏目下存在子栏目时禁止删除，避免树断裂
        Integer children = jdbc.queryForObject(
                "SELECT COUNT(*) FROM cms_category WHERE tenant_id=? AND parent_id=? AND deleted=0",
                Integer.class, tenantId, id);
        if (children != null && children > 0) {
            throw new ServiceException(1_041_000_030, "请先删除子栏目");
        }
        jdbc.update("UPDATE cms_category SET deleted=b'1' WHERE tenant_id=? AND id=?", tenantId, id);
    }

    // ==================== 素材库 ====================

    public Map<String, Object> assetPage(Integer pageNo, Integer pageSize, String assetType, String keyword) {
        long tenantId = tenant();
        int size = Math.min(pageSize == null ? 12 : pageSize, MAX_PAGE_SIZE);
        int page = Math.max(pageNo == null ? 1 : pageNo, 1);
        StringBuilder where = new StringBuilder("WHERE tenant_id=? AND deleted=0");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (assetType != null && !assetType.isBlank()) {
            where.append(" AND asset_type=?");
            args.add(assetType);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND name LIKE ?");
            args.add("%" + keyword.trim() + "%");
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM cms_asset " + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        List<Map<String, Object>> list = jdbc.query("""
                SELECT id, name, asset_type AS assetType, url, size, mime, category_id AS categoryId,
                       tags, usage_count AS usageCount, creator, create_time AS createTime
                FROM cms_asset """ + " " + where + " ORDER BY id DESC LIMIT ? OFFSET ?",
                (rs, row) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getLong("id"));
                    item.put("name", rs.getString("name"));
                    item.put("assetType", rs.getString("assetType"));
                    item.put("url", rs.getString("url"));
                    item.put("size", rs.getObject("size"));
                    item.put("mime", rs.getString("mime"));
                    item.put("categoryId", rs.getObject("categoryId"));
                    item.put("tags", rs.getString("tags"));
                    item.put("usageCount", rs.getInt("usageCount"));
                    item.put("creator", rs.getString("creator"));
                    item.put("createTime", rs.getTimestamp("createTime"));
                    return item;
                }, pageArgs.toArray());
        return page(list, total == null ? 0 : total);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long saveAsset(AssetInput input) {
        long tenantId = tenant();
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO cms_asset (tenant_id,name,asset_type,url,size,mime,category_id,tags,creator)
                    VALUES (?,?,?,?,?,?,?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, tenantId);
            ps.setString(2, input.name());
            ps.setString(3, input.assetType() == null ? "FILE" : input.assetType());
            ps.setString(4, input.url());
            ps.setObject(5, input.size());
            ps.setString(6, input.mime());
            ps.setObject(7, input.categoryId());
            ps.setString(8, input.tags());
            ps.setString(9, nickname());
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteAsset(Long id) {
        jdbc.update("UPDATE cms_asset SET deleted=b'1' WHERE tenant_id=? AND id=?", tenant(), id);
    }

    // ==================== 推荐位 ====================

    public Map<String, Object> recommendPage(Integer pageNo, Integer pageSize, String position) {
        long tenantId = tenant();
        int size = Math.min(pageSize == null ? 20 : pageSize, MAX_PAGE_SIZE);
        int page = Math.max(pageNo == null ? 1 : pageNo, 1);
        StringBuilder where = new StringBuilder("WHERE tenant_id=? AND deleted=0");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (position != null && !position.isBlank()) {
            where.append(" AND position=?");
            args.add(position);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM cms_recommend_slot " + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        List<Map<String, Object>> list = jdbc.query("""
                SELECT id, name, code, position, content_type AS contentType, content_id AS contentId,
                       title, image_url AS imageUrl, target_url AS targetUrl, sort, start_time AS startTime,
                       end_time AS endTime, status, remark
                FROM cms_recommend_slot """ + " " + where + " ORDER BY sort, id DESC LIMIT ? OFFSET ?",
                (rs, row) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getLong("id"));
                    item.put("name", rs.getString("name"));
                    item.put("code", rs.getString("code"));
                    item.put("position", rs.getString("position"));
                    item.put("contentType", rs.getString("contentType"));
                    item.put("contentId", rs.getObject("contentId"));
                    item.put("title", rs.getString("title"));
                    item.put("imageUrl", rs.getString("imageUrl"));
                    item.put("targetUrl", rs.getString("targetUrl"));
                    item.put("sort", rs.getInt("sort"));
                    item.put("startTime", rs.getTimestamp("startTime"));
                    item.put("endTime", rs.getTimestamp("endTime"));
                    item.put("status", rs.getInt("status"));
                    item.put("remark", rs.getString("remark"));
                    return item;
                }, pageArgs.toArray());
        return page(list, total == null ? 0 : total);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long saveRecommend(RecommendInput input) {
        long tenantId = tenant();
        int sort = input.sort() == null ? 0 : input.sort();
        int status = input.status() == null ? 0 : input.status();
        if (input.id() == null) {
            KeyHolder keys = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO cms_recommend_slot
                          (tenant_id,name,code,position,content_type,content_id,title,image_url,target_url,sort,start_time,end_time,status,remark,creator)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, tenantId);
                ps.setString(2, input.name());
                ps.setString(3, input.code());
                ps.setString(4, input.position());
                ps.setString(5, input.contentType());
                ps.setObject(6, input.contentId());
                ps.setString(7, input.title());
                ps.setString(8, input.imageUrl());
                ps.setString(9, input.targetUrl());
                ps.setInt(10, sort);
                ps.setObject(11, parseDateTime(input.startTime()));
                ps.setObject(12, parseDateTime(input.endTime()));
                ps.setInt(13, status);
                ps.setString(14, input.remark());
                ps.setString(15, nickname());
                return ps;
            }, keys);
            return keys.getKey().longValue();
        }
        jdbc.update("""
                UPDATE cms_recommend_slot SET name=?, code=?, position=?, content_type=?, content_id=?,
                       title=?, image_url=?, target_url=?, sort=?, start_time=?, end_time=?, status=?, remark=?, updater=?
                WHERE tenant_id=? AND id=? AND deleted=0
                """, input.name(), input.code(), input.position(), input.contentType(), input.contentId(),
                input.title(), input.imageUrl(), input.targetUrl(), sort, parseDateTime(input.startTime()), parseDateTime(input.endTime()), status,
                input.remark(), nickname(), tenantId, input.id());
        return input.id();
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRecommend(Long id) {
        jdbc.update("UPDATE cms_recommend_slot SET deleted=b'1' WHERE tenant_id=? AND id=?", tenant(), id);
    }

    // ==================== SEO ====================

    public Map<String, Object> getSeo(String contentType, Long contentId) {
        List<Map<String, Object>> rows = jdbc.query("""
                SELECT id, content_type AS contentType, content_id AS contentId, seo_title AS seoTitle,
                       seo_keywords AS seoKeywords, seo_description AS seoDescription,
                       canonical_url AS canonicalUrl
                FROM cms_seo_meta WHERE tenant_id=? AND content_type=? AND content_id=? AND deleted=0
                """, (rs, row) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rs.getLong("id"));
            item.put("contentType", rs.getString("contentType"));
            item.put("contentId", rs.getLong("contentId"));
            item.put("seoTitle", rs.getString("seoTitle"));
            item.put("seoKeywords", rs.getString("seoKeywords"));
            item.put("seoDescription", rs.getString("seoDescription"));
            item.put("canonicalUrl", rs.getString("canonicalUrl"));
            return item;
        }, tenant(), contentType, contentId);
        return rows.isEmpty() ? Map.of() : rows.getFirst();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long saveSeo(SeoInput input) {
        long tenantId = tenant();
        List<Long> ids = jdbc.query("""
                SELECT id FROM cms_seo_meta WHERE tenant_id=? AND content_type=? AND content_id=? AND deleted=0
                """, (rs, row) -> rs.getLong(1), tenantId, input.contentType(), input.contentId());
        if (ids.isEmpty()) {
            KeyHolder keys = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO cms_seo_meta (tenant_id,content_type,content_id,seo_title,seo_keywords,
                                                  seo_description,canonical_url,creator)
                        VALUES (?,?,?,?,?,?,?,?)
                        """, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, tenantId);
                ps.setString(2, input.contentType());
                ps.setLong(3, input.contentId());
                ps.setString(4, input.seoTitle());
                ps.setString(5, input.seoKeywords());
                ps.setString(6, input.seoDescription());
                ps.setString(7, input.canonicalUrl());
                ps.setString(8, nickname());
                return ps;
            }, keys);
            return keys.getKey().longValue();
        }
        jdbc.update("""
                UPDATE cms_seo_meta SET seo_title=?, seo_keywords=?, seo_description=?, canonical_url=?, updater=?
                WHERE tenant_id=? AND id=?
                """, input.seoTitle(), input.seoKeywords(), input.seoDescription(), input.canonicalUrl(),
                nickname(), tenantId, ids.getFirst());
        return ids.getFirst();
    }

    // ==================== 审查任务 ====================

    public Map<String, Object> reviewTaskPage(String status, Long assigneeId, Integer pageNo, Integer pageSize) {
        long tenantId = tenant();
        int size = Math.min(pageSize == null ? 20 : pageSize, MAX_PAGE_SIZE);
        int page = Math.max(pageNo == null ? 1 : pageNo, 1);
        StringBuilder where = new StringBuilder("WHERE tenant_id=? AND deleted=0");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (status != null && !status.isBlank()) {
            where.append(" AND status=?");
            args.add(status);
        }
        if (assigneeId != null) {
            where.append(" AND assignee_id=?");
            args.add(assigneeId);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM cms_review_task " + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        List<Map<String, Object>> list = jdbc.query("""
                SELECT id, review_id AS reviewId, title, assignee_id AS assigneeId, assignee_name AS assigneeName,
                       status, priority, due_time AS dueTime, finished_time AS finishedTime, remark,
                       create_time AS createTime,
                       (due_time IS NOT NULL AND due_time < NOW() AND status IN ('PENDING','PROCESSING')) AS overdue
                FROM cms_review_task """ + " " + where + " ORDER BY overdue DESC, priority DESC, id DESC LIMIT ? OFFSET ?",
                (rs, row) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getLong("id"));
                    item.put("reviewId", rs.getString("reviewId"));
                    item.put("title", rs.getString("title"));
                    item.put("assigneeId", rs.getObject("assigneeId"));
                    item.put("assigneeName", rs.getString("assigneeName"));
                    item.put("status", rs.getString("status"));
                    item.put("priority", rs.getInt("priority"));
                    item.put("dueTime", rs.getTimestamp("dueTime"));
                    item.put("finishedTime", rs.getTimestamp("finishedTime"));
                    item.put("remark", rs.getString("remark"));
                    item.put("createTime", rs.getTimestamp("createTime"));
                    item.put("overdue", rs.getBoolean("overdue"));
                    return item;
                }, pageArgs.toArray());
        return page(list, total == null ? 0 : total);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long assignTask(AssignInput input) {
        long tenantId = tenant();
        String assigneeName = input.assigneeName();
        if (assigneeName == null && input.assigneeId() != null) {
            List<String> names = jdbc.query("SELECT nickname FROM system_users WHERE id=? AND deleted=0",
                    (rs, row) -> rs.getString(1), input.assigneeId());
            assigneeName = names.isEmpty() ? null : names.getFirst();
        }
        String status = input.assigneeId() == null ? "PENDING" : "PROCESSING";
        List<Long> exists = jdbc.query(
                "SELECT id FROM cms_review_task WHERE tenant_id=? AND review_id=? AND deleted=0",
                (rs, row) -> rs.getLong(1), tenantId, input.reviewId());
        if (exists.isEmpty()) {
            KeyHolder keys = new GeneratedKeyHolder();
            String finalName = assigneeName;
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO cms_review_task
                          (tenant_id,review_id,title,assignee_id,assignee_name,status,priority,due_time,remark,creator)
                        VALUES (?,?,?,?,?,?,?,?,?,?)
                        """, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, tenantId);
                ps.setString(2, input.reviewId());
                ps.setString(3, input.title());
                ps.setObject(4, input.assigneeId());
                ps.setString(5, finalName);
                ps.setString(6, status);
                ps.setInt(7, input.priority() == null ? 1 : input.priority());
                ps.setObject(8, parseDateTime(input.dueTime()));
                ps.setString(9, input.remark());
                ps.setString(10, nickname());
                return ps;
            }, keys);
            return keys.getKey().longValue();
        }
        jdbc.update("""
                UPDATE cms_review_task SET title=?, assignee_id=?, assignee_name=?, status=?, priority=?,
                       due_time=?, remark=?, updater=?
                WHERE tenant_id=? AND id=?
                """, input.title(), input.assigneeId(), assigneeName, status,
                input.priority() == null ? 1 : input.priority(), parseDateTime(input.dueTime()), input.remark(),
                nickname(), tenantId, exists.getFirst());
        return exists.getFirst();
    }

    @Transactional(rollbackFor = Exception.class)
    public void completeTask(Long taskId) {
        jdbc.update("""
                UPDATE cms_review_task SET status='DONE', finished_time=NOW(), updater=?
                WHERE tenant_id=? AND id=? AND deleted=0
                """, nickname(), tenant(), taskId);
    }

    /** 定时任务：把超期未完成的任务标记为 OVERDUE。 */
    @Transactional(rollbackFor = Exception.class)
    public int markOverdue() {
        return jdbc.update("""
                UPDATE cms_review_task SET status='OVERDUE'
                WHERE deleted=0 AND due_time IS NOT NULL AND due_time < NOW()
                  AND status IN ('PENDING','PROCESSING')
                """);
    }

    // ==================== 公共方法 ====================

    private Map<String, Object> page(List<Map<String, Object>> list, long total) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", total);
        return result;
    }

    private Set<String> currentRoleCodes() {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null || loginUser.getId() == null) {
            return Set.of();
        }
        return jdbc.query("""
                SELECT r.code FROM system_role r
                JOIN system_user_role ur ON ur.role_id = r.id
                WHERE ur.user_id=? AND r.deleted=0 AND r.tenant_id=?
                """, (rs, row) -> rs.getString(1), loginUser.getId(), tenant())
                .stream().filter(code -> code != null).collect(Collectors.toSet());
    }

    private static String nickname() {
        String nickname = SecurityFrameworkUtils.getLoginUserNickname();
        return nickname == null ? "system" : nickname;
    }

    /** 兼容 'yyyy-MM-dd HH:mm:ss' 与 ISO（含 T）两种时间入参。 */
    private static java.time.LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim().replace('T', ' ');
        if (text.length() == 16) {
            text = text + ":00";
        }
        try {
            return java.time.LocalDateTime.parse(text,
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (java.time.format.DateTimeParseException ex) {
            return null;
        }
    }

    private static long tenant() {
        return TenantContextHolder.getRequiredTenantId();
    }

    public record CategoryInput(Long id, Long parentId, String name, String code, String contentType,
                                Integer sort, Boolean visible, Integer status,
                                List<String> visibleRoleCodes, String remark) { }

    public record AssetInput(String name, String assetType, String url, Long size, String mime,
                             Long categoryId, String tags) { }

    public record RecommendInput(Long id, String name, String code, String position, String contentType,
                                 Long contentId, String title, String imageUrl, String targetUrl, Integer sort,
                                 String startTime, String endTime,
                                 Integer status, String remark) { }

    public record SeoInput(String contentType, Long contentId, String seoTitle, String seoKeywords,
                           String seoDescription, String canonicalUrl) { }

    public record AssignInput(String reviewId, String title, Long assigneeId, String assigneeName,
                              Integer priority, String dueTime, String remark) { }
}