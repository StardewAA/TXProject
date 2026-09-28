package cn.iocoder.txgy.module.bidding.arch;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Arrays;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 架构/命名/注解规约自动化校验（ArchUnit）。
 *
 * <p>规则依据：《招投标模块开发规范》第二~五章（全量 VO、自建 VO、分层、注解、数据层）。
 * 违规即令 {@code mvn test} 失败；新增规则先改本文档（规范），再同步本测试，保持"文档即门禁"。
 *
 * <p>说明：包级占位文件 package-info 不参与命名规则；src/test 下的测试类不参与分析
 * （ImportOption.DoNotIncludeTests）。
 */
@AnalyzeClasses(packages = "cn.iocoder.txgy.module.bidding",
        importOptions = ImportOption.DoNotIncludeTests.class)
public class BiddingArchitectureRulesTest {

    private static final String ROOT = "cn.iocoder.txgy.module.bidding";
    private static final String API = ROOT + ".api";
    private static final String API_VO = API + ".vo";
    private static final String API_ENUMS = API + ".enums";
    private static final String PUBLIC_LEVEL = API_ENUMS + ".PublicLevel";
    private static final String BASE_DO = "cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO";
    private static final String TENANT_BASE_DO = "cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO";

    // ========== 工具：注解判定（按 simple name，避免对框架类路径的硬依赖） ==========

    private static DescribedPredicate<JavaClass> annotatedWithSimple(String annotationSimpleName) {
        return new DescribedPredicate<JavaClass>("annotated with @" + annotationSimpleName) {
            @Override
            public boolean test(JavaClass input) {
                return hasAnnotation(input, annotationSimpleName);
            }
        };
    }

    private static boolean hasAnnotation(JavaClass clazz, String annotationSimpleName) {
        return clazz.getAnnotations().stream()
                .anyMatch(a -> a.getRawType().getSimpleName().equals(annotationSimpleName));
    }

    private static boolean hasAnnotation(JavaMethod method, String annotationSimpleName) {
        return method.getAnnotations().stream()
                .anyMatch(a -> a.getRawType().getSimpleName().equals(annotationSimpleName));
    }

    private static void fail(ConditionEvents events, Object source, String message) {
        events.add(SimpleConditionEvent.violated(source, message));
    }

    private static ArchCondition<JavaClass> condition(String description,
                                                      java.util.function.Predicate<JavaClass> check) {
        return new ArchCondition<JavaClass>(description) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (!check.test(item)) {
                    fail(events, item, item.getName() + " 违反：" + description);
                }
            }
        };
    }

    // ========== 1. API 模块隔离：全量 VO/代码集只属于 api，不得触碰 server 各层 ==========

    @ArchTest
    static final ArchRule API_ISOLATION = noClasses()
            .that().resideInAPackage(API + "..")
            .should().accessClassesThat().resideInAnyPackage(
                    ROOT + ".controller..",
                    ROOT + ".service..",
                    ROOT + ".dal..",
                    ROOT + ".enums..",
                    ROOT + ".framework..")
            .as("api 包（api/vo、api/enums）不得依赖 server 各层（规范 4.2 分层）");

    // ========== 2. 全量 VO：record、全 String、逐字段 @PublicLevel ==========

    @ArchTest
    static final ArchRule EXCHANGE_VO_IS_RECORD_OF_STRING_WITH_PUBLIC_LEVEL = classes()
            .that().resideInAPackage(API_VO + "..")
            .should(condition("全量 VO 必须是 record，且所有组件为 String 并标注 @PublicLevel（规范 2.1/2.2）",
                    clazz -> {
                        if (clazz.getSuperclass().map(s -> s.getName()).orElse("")
                                .equals("java.lang.Record")) {
                            for (JavaField field : clazz.getFields()) {
                                if (field.getModifiers().contains(JavaModifier.STATIC)) {
                                    continue;
                                }
                                if (!field.getModifiers().contains(JavaModifier.FINAL)
                                        || !"java.lang.String".equals(field.getRawType().getName())) {
                                    return false;
                                }
                                boolean hasPublicLevel = field.getAnnotations().stream()
                                        .anyMatch(a -> a.getRawType().getName().equals(PUBLIC_LEVEL));
                                if (!hasPublicLevel) {
                                    return false;
                                }
                            }
                            return true;
                        }
                        return false;
                    }));

    // ========== 3. 代码集常量：api/enums 命名白名单 + 最终类 + CODE_* 常量 ==========

    @ArchTest
    static final ArchRule API_ENUMS_NAME_WHITELIST = classes()
            .that(JavaClass.Predicates.resideInAPackage(API_ENUMS + "..")
                    .and(JavaClass.Predicates.simpleName("package-info").negate()))
            .should(condition("api/enums 只允许 National*/Shaanxi* 代码集与 PublicLevel 公开级别（规范七章）",
                    clazz -> clazz.getSimpleName().matches("(National|Shaanxi|PublicLevel).*")));

    @ArchTest
    static final ArchRule CODE_SET_ENUM_SHAPE = classes()
            .that(JavaClass.Predicates.resideInAPackage(API_ENUMS + "..")
                    .and(JavaClass.Predicates.simpleNameStartingWith("National")
                            .or(JavaClass.Predicates.simpleNameStartingWith("Shaanxi"))))
            .should(condition("代码集类必须为 final class，字段全为 public static final String CODE_* 或 DICT_TYPE_*（规范七章；2026-09-25 merge 后代码集常量改为字典类型常量 DICT_TYPE_*，规则同步放宽）",
                    clazz -> {
                        if (!clazz.getModifiers().contains(JavaModifier.FINAL)
                                || !clazz.getSimpleName().endsWith("Enum")) {
                            return false;
                        }
                        for (JavaField field : clazz.getFields()) {
                            if (!field.getModifiers().containsAll(
                                    Set.of(JavaModifier.PUBLIC, JavaModifier.STATIC, JavaModifier.FINAL))
                                    || !"java.lang.String".equals(field.getRawType().getName())
                                    || !(field.getName().startsWith("CODE_") || field.getName().startsWith("DICT_TYPE_"))) {
                                return false;
                            }
                        }
                        return true;
                    }));

    // ========== 4. Controller：位置/命名/注解与写方法守卫 ==========

    @ArchTest
    static final ArchRule CONTROLLER_PLACEMENT = classes()
            .that(annotatedWithSimple("RestController"))
            // 2026-09-16 放宽：一键生成需要门户(app-api)入口，规范 4.2 允许 controller/admin 与 controller/app 两层
            .should().resideInAnyPackage(ROOT + ".controller.admin..", ROOT + ".controller.app..")
            .andShould().haveSimpleNameEndingWith("Controller")
            .as("带 @RestController 的类必须位于 controller/{admin|app}/<domain> 且名为 XxxController（规范 4.2）")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule CONTROLLER_METHOD_GUARDS = classes()
            .that(annotatedWithSimple("RestController"))
            .should(new ArchCondition<JavaClass>("写方法带 @PreAuthorize 与 @Idempotent，映射方法带 @PreAuthorize（规范 4.4/4.5）") {
                @Override
                public void check(JavaClass clazz, ConditionEvents events) {
                    for (JavaMethod method : clazz.getMethods()) {
                        boolean mapping = method.getAnnotations().stream().anyMatch(
                                a -> Arrays.asList("RequestMapping", "GetMapping", "PostMapping",
                                        "PutMapping", "DeleteMapping", "PatchMapping")
                                        .contains(a.getRawType().getSimpleName()));
                        if (!mapping) {
                            continue;
                        }
                        boolean permitAll = hasAnnotation(method, "PermitAll");
                        if (!permitAll && !hasAnnotation(method, "PreAuthorize")) {
                            fail(events, method, method.getFullName() + " 缺 @PreAuthorize（权限码 bidding:xxx:query/update）");
                        }
                        boolean write = method.getAnnotations().stream().anyMatch(
                                a -> Arrays.asList("PostMapping", "PutMapping", "DeleteMapping", "PatchMapping")
                                        .contains(a.getRawType().getSimpleName()));
                        if (write && !hasAnnotation(method, "Idempotent")) {
                            fail(events, method, method.getFullName() + " 是写方法但缺 @Idempotent（规范 4.5 幂等）");
                        }
                    }
                }
            })
            .allowEmptyShould(true);

    // ========== 5. 全量 VO 不得外泄到 controller/dal（转换只在 service，规范 3.2） ==========

    @ArchTest
    static final ArchRule API_VO_ONLY_IN_SERVICE = noClasses()
            .that().resideInAnyPackage(ROOT + ".controller..", ROOT + ".dal..")
            .should().dependOnClassesThat().resideInAPackage(API_VO + "..")
            .as("全量 VO（api/vo）只能出现在 service 层转换，controller/dal 不得引用（规范 3.2）");

    // ========== 6. DO/Mapper 命名与位置 ==========

    @ArchTest
    static final ArchRule DO_LAYER = classes()
            .that(JavaClass.Predicates.resideInAPackage(ROOT + ".dal.dataobject..")
                    .and(JavaClass.Predicates.simpleName("package-info").negate()))
            .should(condition("dal/dataobject 下的 DO 须名为 XxxDO 且继承 BaseDO/TenantBaseDO（规范 4.2/五章四）",
                    clazz -> clazz.getSimpleName().endsWith("DO")
                            && isBaseDoSubtype(clazz)))
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule DO_EXTENDS_BASE_ONLY_IN_DATAOBJECT = classes()
            .that(new DescribedPredicate<JavaClass>("继承 BaseDO/TenantBaseDO 的实体") {
                @Override
                public boolean test(JavaClass input) {
                    return isBaseDoSubtype(input) && !input.getSimpleName().equals("package-info");
                }
            })
            .should().resideInAPackage(ROOT + ".dal.dataobject..")
            .as("继承 BaseDO/TenantBaseDO 的实体只能位于 dal/dataobject（规范五章四/五）")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule MAPPER_LAYER = classes()
            .that(JavaClass.Predicates.resideInAPackage(ROOT + ".dal.mysql..")
                    .and(JavaClass.Predicates.simpleName("package-info").negate()))
            .should(condition("dal/mysql 下的 Mapper 须为 XxxMapper 接口（规范 4.2）",
                    clazz -> clazz.getSimpleName().endsWith("Mapper") && clazz.isInterface()))
            .allowEmptyShould(true);

    private static boolean isBaseDoSubtype(JavaClass clazz) {
        // TenantBaseDO extends BaseDO；直接父类命中其一即可覆盖两条继承路径
        return clazz.getSuperclass().map(s -> s.getName()).map(name ->
                        name.equals(BASE_DO) || name.equals(TENANT_BASE_DO))
                .orElse(false);
    }

    // ========== 7. Service：XxxService ↔ XxxServiceImpl 同包配对 ==========

    @ArchTest
    static final ArchRule SERVICE_PAIRING = classes()
            .that(JavaClass.Predicates.resideInAPackage(ROOT + ".service..")
                    .and(JavaClass.Predicates.simpleName("package-info").negate()))
            .should(new ArchCondition<JavaClass>("service 接口与实现同包配对：XxxService ↔ XxxServiceImpl（规范 4.2）") {
                @Override
                public void check(JavaClass clazz, ConditionEvents events) {
                    String name = clazz.getSimpleName();
                    if (name.endsWith("Service") && clazz.isInterface()) {
                        boolean implemented = clazz.getSubclasses().stream()
                                .anyMatch(sub -> sub.getSimpleName().equals(name + "Impl"));
                        if (!implemented) {
                            fail(events, clazz, clazz.getName() + " 缺少同名 XxxServiceImpl 实现");
                        }
                    } else if (name.endsWith("ServiceImpl") && !clazz.isInterface()) {
                        boolean hasInterface = clazz.getInterfaces().stream()
                                .filter(iface -> iface instanceof JavaClass)
                                .map(iface -> (JavaClass) iface)
                                .anyMatch(iface -> iface.getSimpleName().equals(name.replace("Impl", ""))
                                        && iface.getPackageName().equals(clazz.getPackageName()));
                        if (!hasInterface) {
                            fail(events, clazz, clazz.getName() + " 未实现同包同名 XxxService 接口");
                        }
                    }
                }
            })
            .allowEmptyShould(true);

    // ========== 8. 跨模块边界：只经 api 契约访问其他 txgy 模块（规范 4.1/红线 6） ==========

    @ArchTest
    static final ArchRule CROSS_MODULE_ONLY_VIA_API = classes()
            .that().resideInAPackage(ROOT + "..")
            .should(new ArchCondition<JavaClass>("访问其他 txgy 模块必须落在其 .api. 契约包（规范 4.1/红线 6）") {
                @Override
                public void check(JavaClass clazz, ConditionEvents events) {
                    for (Dependency dependency : clazz.getDirectDependenciesFromSelf()) {
                        String targetPackage = dependency.getTargetClass().getPackageName();
                        if (isForbiddenCrossModuleTarget(targetPackage)) {
                            fail(events, dependency, dependency.getDescription());
                        }
                    }
                }

                private boolean isForbiddenCrossModuleTarget(String targetPackage) {
                    if (!targetPackage.startsWith("cn.iocoder.txgy.module.")) {
                        return false; // 框架/自研 starter、JDK/三方库不在拦截范围
                    }
                    if (targetPackage.startsWith(ROOT)) {
                        return false; // bidding 模块内部
                    }
                    // 仅允许访问其他模块的 api 契约子包（module.*.api.*）
                    String rest = targetPackage.substring("cn.iocoder.txgy.module.".length());
                    int dot = rest.indexOf('.');
                    if (dot > 0) {
                        String subPackage = rest.substring(dot + 1);
                        return !subPackage.startsWith("api.");
                    }
                    return false; // 其他模块根包（极少直连，不拦）
                }
            });
}
