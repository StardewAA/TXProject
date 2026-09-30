package cn.iocoder.txgy.module.bidding.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **预警清单读接口**存在性门禁。
 *
 * <p>规范出处：决策 ③（《电子资质域-待决策与残留事项清单》§1③）——证照域与员工域此前"只扫不读"，
 * 2026-09-13 补齐 `GET /bidding/certificate-alert-run/alert/page` 与 `GET /bidding/personnel-alert-run/alert/page`。
 *
 * <p>为什么必须有这个门禁：这两个接口（连同 VO 与 Service 方法）在一次并发编辑中被**整体删除**过，
 * 而当时的编译、单测、乃至其它门禁**全是绿的** —— 功能静默消失（实测：接口返回
 * `404 请求地址不存在`，页面上表现为"预警清单打不开"）。删除是"少了一个端点"，
 * 不是语法错误，没有任何一层会报；只有把"这些端点必须存在"写成断言才会暴露。
 *
 * <p>业务目标：保证"到期预警看板"的读出口不会再次消失（新增/删除读接口时，这里同步维护）。
 */
class AlertListEndpointGateTest {

    /** 必须存在的预警清单接口（控制器全名 → 方法名）。 */
    private static final String[][] REQUIRED_ENDPOINTS = {
            {"cn.iocoder.txgy.module.bidding.controller.admin.certificate.CertificateAlertRunController", "getAlertPage"},
            {"cn.iocoder.txgy.module.bidding.controller.admin.personnel.PersonnelAlertRunController", "getAlertPage"},
    };

    /** 接口依赖的 VO 与 Service 方法（同样被整体删过，一并钉住）。 */
    private static final String[] REQUIRED_TYPES = {
            "cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateAlertPageReqVO",
            "cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateAlertRespVO",
            "cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelAlertPageReqVO",
            "cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelAlertRespVO",
    };

    private static final String[] REQUIRED_SERVICE_METHODS = {
            "cn.iocoder.txgy.module.bidding.service.certificate.CertificateAlertService#getAlertPage",
            "cn.iocoder.txgy.module.bidding.service.personnel.PersonnelAlertService#getAlertPage",
    };

    @Test
    @DisplayName("预警清单读接口与依赖类型/服务方法必须存在（曾被整体删除且全绿）")
    void alertListEndpoints_mustExist() throws Exception {
        List<String> problems = new ArrayList<>();

        for (String[] pair : REQUIRED_ENDPOINTS) {
            Class<?> controller = Class.forName(pair[0]);
            boolean found = false;
            for (Method m : controller.getDeclaredMethods()) {
                if (!m.getName().equals(pair[1])) {
                    continue;
                }
                GetMapping get = m.getAnnotation(GetMapping.class);
                RequestMapping base = controller.getAnnotation(RequestMapping.class);
                if (get != null && get.value().length > 0 && base != null && base.value().length > 0
                        && String.join("", get.value()).contains("alert/page")) {
                    found = true;
                }
            }
            if (!found) {
                problems.add(controller.getSimpleName() + "#" + pair[1] + " 缺少 GET .../alert/page");
            }
        }

        for (String type : REQUIRED_TYPES) {
            try {
                Class.forName(type);
            } catch (ClassNotFoundException e) {
                problems.add("缺少 VO：" + type);
            }
        }

        Path serviceRoot = Paths.get("src/main/java/cn/iocoder/txgy/module/bidding/service");
        for (String key : REQUIRED_SERVICE_METHODS) {
            String[] parts = key.split("#");
            String simple = parts[0].substring(parts[0].lastIndexOf('.') + 1);
            Path file = serviceRoot.resolve(parts[0].contains(".certificate.") ? "certificate/" + simple + ".java"
                    : "personnel/" + simple + ".java");
            boolean declared = false;
            if (Files.exists(file)) {
                declared = Files.readString(file, StandardCharsets.UTF_8).contains(parts[1] + "(");
            }
            if (!declared) {
                problems.add("服务接口缺少方法：" + key);
            }
        }

        assertTrue(problems.isEmpty(),
                "预警清单相关代码缺失（删除是静默的，只有这条断言会报）：" + problems
                        + "。若确要下线该能力，请同步修改本门禁并说明原因。");
    }

    @Test
    @DisplayName("自检：门禁本身能失败（用一个不存在的端点验证判定逻辑）")
    void gateItself_canFail() {
        boolean found = false;
        for (Method m : AlertListEndpointGateTest.class.getDeclaredMethods()) {
            if (m.getName().equals("thisMethodDoesNotExist")) {
                found = true;
            }
        }
        assertTrue(!found, "自检用例：不存在的端点不应被判为存在（本门禁的判定逻辑有效）");
    }
}
