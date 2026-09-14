# PDF 转图片工具（电子资质证明 → 标书插图）

> 批量把电子资质证明 PDF 的每一页转成 PNG/JPG 图片（可控 DPI、分辨率上限、质量、并发），
> 输出 `report.json`，供"一键制作投标书"时替换模板占位符。
> 技术选型与数学约定见 `00-调研与技术方案.md`。

## 构建与运行

```bash
# JDK 8+ / Maven
mvn -q -DskipTests package
mvn -q dependency:copy-dependencies -DoutputDirectory=target/lib

# 生成自测样例（2 页模拟资质证书 PDF）
java -cp "target/classes;target/lib/*" cn.iocoder.txgy.tool.pdftoimage.demo.SamplePdfGenerator sample-cert.pdf

# 批量转图（目录，默认 200dpi / PNG / 宽高上限 2480x3508 / 跳过已存在）
java -cp "target/classes;target/lib/*" cn.iocoder.txgy.tool.pdftoimage.cli.PdfToImageCli \
    -i sample-cert.pdf -o out --dpi 200 --format png --max-width 2480 --max-height 3508
```

常用参数：`-i 输入(pdf或目录) -o 输出目录`；`--dpi 72~1200`（默认200）；
`--format png|jpg`；`--quality 0.1~1.0`（JPG，默认0.9）；
`--max-width/--max-height px`（0=不限，默认2480×3508，**超限等比缩小**）；
`--threads n`（默认2）；`--overwrite`（默认跳过已存在，幂等）；`--no-recursive`。

产物：`{原文件名}_p{页码}.png` + `out/report.json`（含每页尺寸与失败清单）。
退出码：0=完成（含部分失败，看 report）、1=失败、2=用法错误。

## Java API（内嵌复用）

```java
PdfToImageRenderer r = new PdfBoxPdfToImageRenderer();       // Apache PDFBox 2.0.31
List<PageImage> pages = r.convert(new File("资质证书.pdf"), outDir,
        new PdfToImageOptions().setDpi(200).setFormat("PNG").setMaxWidth(2480).setMaxHeight(3508));

BatchSummary s = new BatchConverter().convert(inputPdfOrDir, outDir, options); // 批量 + report.json
boolean ok = s.isAllOk();
```

## 与"一键制作投标书（占位符替换）"的衔接

工作流：**证书 PDF → 本工具成图 → 模板占位符替换（图片）→ 输出投标文件**。
Word 模板（.docx）推荐 poi-tl（Apache-2.0，占位符 `{{证书图片}}`），示例：

```java
import com.deepoove.poi.XWPFTemplate;
import com.deepoove.poi.data.PictureType;
import com.deepoove.poi.data.Pictures;
// ...
Map<String, Object> data = new HashMap<>();
data.put("证书图片", Pictures.ofBytes(证书图片字节, PictureType.PNG).size(240, 320).create());
XWPFTemplate tpl = XWPFTemplate.compile(new File("投标书模板.docx"));
tpl.render(data);
tpl.writeAndClose(new FileOutputStream(new File("投标书-已填充.docx")));
```

> 占位符语法与版本以 poi-tl 官方文档为准；Word→PDF 定稿建议由平台既有转换工具或 LibreOffice headless 完成。

## 接口（Java API + REST 参考）

- **Java API**：`PdfToImageRenderer#convert` / `BatchConverter#convert`（本工程可直接打成 jar 依赖）。
- **REST（bidding-server 集成参考，需按模块约定补鉴权/审计/幂等）**：

```java
@RestController
@RequestMapping("/admin-api/bidding/pdf-to-image")
public class PdfToImageController {
    // 单文件：multipart pdf + options 表单 → {files:[{page,width,height,name}]}
    @PostMapping("/convert")
    public CommonResult<List<PageImageVO>> convert(MultipartFile file, PdfToImageOptionsForm form) { ... }
    // 目录批量（服务端路径）
    @PostMapping("/convert-dir")
    public CommonResult<BatchSummaryVO> convertDir(@RequestBody DirConvertReqDTO req) { ... }
}
```

设计要点：转换接口返回图片清单（路径/宽高），由制标服务据此调用模板渲染替换占位符；
批量接口在服务端任务队列执行（证书量大），完成后回调/轮询取 `report.json` 结论，避免长连接。

## 目录

```
src/main/java/cn/iocoder/txgy/tool/pdftoimage/
├── api/PdfToImageOptions.java      # 配置（dpi/format/质量/尺寸上限/并发/幂等）
├── api/PageImage.java              # 单页结果
├── api/BatchSummary.java / JsonUtil.java
├── core/PdfToImageRenderer.java    # 渲染接口
├── core/PdfBoxPdfToImageRenderer.java  # PDFBox 实现（含 DPI 元数据、白底、缩限）
├── batch/BatchConverter.java       # 目录批量 + report.json
├── cli/PdfToImageCli.java          # 命令行
└── demo/SamplePdfGenerator.java    # 自测样例生成
```

## 已知边界

- 只处理 **PDF**；住建部电子证照标准的原生载体是 **OFD**（建办市〔2024〕51号），OFD 需先转 PDF 再入本流程（另案）；
- PDF 未内嵌中文字体可能缺字/空白——需用真实证书样本回归，必要时做字体映射；
- 产物用于排版插图（非司法原件核验），原件核验走 `../ca-verify/`。
