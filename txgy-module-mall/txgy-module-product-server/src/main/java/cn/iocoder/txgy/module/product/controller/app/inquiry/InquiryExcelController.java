package cn.iocoder.txgy.module.product.controller.app.inquiry;

import cn.idev.excel.annotation.ExcelProperty;
import cn.idev.excel.FastExcelFactory;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.read.listener.ReadListener;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.excel.core.util.ExcelUtils;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.txgy.module.product.dal.mysql.sku.ProductSkuMapper;
import cn.iocoder.txgy.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.util.*;
import java.io.IOException;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService.invalid;

@RestController @RequestMapping("/product/purchase-inquiry")
public class InquiryExcelController {
 @Resource private EnterpriseMembershipService membership;
 @Resource private ProductSkuMapper skuMapper;
 @Resource private ProductSpuMapper spuMapper;
 private static final Set<String> HEADERS=Set.of("商品名称","商品编码","分类","规格型号","采购数量","单位","备注");
 @Data public static class ExcelRow {
  @ExcelProperty("商品名称") private String name;
  @ExcelProperty("商品编码") private String code;
  @ExcelProperty("分类") private String category;
  @ExcelProperty("规格型号") private String specification;
  @ExcelProperty("采购数量") private String quantity;
  @ExcelProperty("单位") private String unit;
  @ExcelProperty("备注") private String remark;
 }
 public record Preview(int rowNumber, ExcelRow item, Long skuId, String matchStatus, List<String> errors, List<String> warnings) {}
 @GetMapping("/template")
 public void template(HttpServletResponse response) throws IOException {
  membership.requireBuyer(EnterpriseMembershipService.memberId());
  ExcelUtils.write(response,"采购商品导入模板.xlsx","采购商品",ExcelRow.class,List.of());
 }
 @PostMapping("/import-preview")
 public CommonResult<List<Preview>> preview(@RequestParam("file") MultipartFile file) throws IOException {
  membership.requireBuyer(EnterpriseMembershipService.memberId());
  if(file.isEmpty() || file.getSize()>2*1024*1024 || file.getOriginalFilename()==null
    || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx"))
   throw invalid("仅支持2MB以内的.xlsx文件，每次最多200行");
  byte[] workbook;
  try(var source=file.getInputStream()) { workbook=source.readNBytes(2*1024*1024+1); }
  if(workbook.length>2*1024*1024)throw invalid("仅支持2MB以内的.xlsx文件");
  InquiryWorkbookGuard.validate(workbook);
  List<ExcelRow> rows=new ArrayList<>();
  List<Integer> sourceRows=new ArrayList<>();
  try (var input=new java.io.ByteArrayInputStream(workbook)) {
   FastExcelFactory.read(input,ExcelRow.class,new ReadListener<ExcelRow>() {
    @Override public void invokeHead(Map<Integer,cn.idev.excel.metadata.data.ReadCellData<?>> head,AnalysisContext context) {
     var names=new ArrayList<>(cn.idev.excel.util.ConverterUtils.convertToStringMap(head,context).values());
     names.removeIf(name->name==null||name.isBlank());
     if(names.size()!=HEADERS.size()||!new HashSet<>(names).equals(HEADERS))
      throw invalid("模板表头不正确：须完整保留商品名称、商品编码、分类、规格型号、采购数量、单位、备注，不能重复或增加列");
    }
    @Override public void invoke(ExcelRow row,AnalysisContext context) {
     rows.add(row); sourceRows.add(context.readRowHolder().getRowIndex()+1);
    }
    @Override public boolean hasNext(AnalysisContext context) { return rows.size()<201; }
    @Override public void doAfterAllAnalysed(AnalysisContext context) { }
   }).autoCloseStream(false).sheet(0).doRead();
  }
  catch(Exception e) {
   for(Throwable cause=e;cause!=null;cause=cause.getCause())
    if(cause instanceof cn.iocoder.txgy.framework.common.exception.ServiceException business)throw business;
   throw invalid("无法读取Excel，请使用平台模板并填写纯文本或数值");
  }
  if(rows.isEmpty() || rows.size()>200) throw invalid("请导入1至200行物品");
  var result=new ArrayList<Preview>();
  var duplicates=new HashSet<List<String>>();
  for(int i=0;i<rows.size();i++) {
   var r=rows.get(i); var errors=new ArrayList<String>(); var warnings=new ArrayList<String>(); Long skuId=null;
   if(r.getName()==null || r.getName().isBlank()) errors.add("商品名称必填");
   if(r.getCategory()==null || r.getCategory().isBlank()) errors.add("分类必填");
   if(r.getUnit()==null || r.getUnit().isBlank()) errors.add("单位必填");
   checkLength(r.getName(),200,"商品名称",errors);checkLength(r.getCode(),100,"商品编码",errors);
   checkLength(r.getCategory(),100,"分类",errors);checkLength(r.getSpecification(),500,"规格型号",errors);
   checkLength(r.getUnit(),20,"单位",errors);checkLength(r.getRemark(),500,"备注",errors);
   try {
    if(r.getQuantity()==null||r.getQuantity().length()>64)throw new IllegalArgumentException();
    var quantity=new BigDecimal(r.getQuantity().trim());
    if(quantity.signum()<=0 || quantity.stripTrailingZeros().scale()>3 || quantity.compareTo(new BigDecimal("999999999.999"))>0)
     errors.add("数量须大于零、最多9位整数及3位小数");
   } catch(Exception e) { errors.add("采购数量不是有效数字"); }
   var key=List.of(text(r.getCode()),text(r.getName()),text(r.getCategory()),text(r.getSpecification()),text(r.getUnit()));
   if(!duplicates.add(key)) warnings.add("表内物品重复，请确认是否保留");
   if(errors.isEmpty() && r.getCode()!=null && !r.getCode().isBlank()) {
    var skus=skuMapper.selectList(new LambdaQueryWrapperX<ProductSkuDO>().eq(ProductSkuDO::getBarCode,r.getCode()));
    var valid=skus.stream().filter(s->{var p=spuMapper.selectById(s.getSpuId());return p!=null && Integer.valueOf(1).equals(p.getStatus());}).toList();
    if(valid.size()==1) {
     skuId=valid.get(0).getId();
     var product=spuMapper.selectById(valid.get(0).getSpuId());
     if(!Objects.equals(product.getName(),r.getName())) warnings.add("编码匹配商品名称："+product.getName()+"，保存时以平台名称为准");
     var properties=valid.get(0).getProperties();
     if(properties!=null&&!properties.isEmpty())warnings.add("平台SKU规格："+String.join(" / ",properties.stream().map(p->p.getPropertyName()+":"+p.getValueName()).toList()));
     warnings.add("请核对规格及计量单位，平台暂未提供统一单位映射，不自动换算");
    } else if(valid.size()>1) warnings.add("编码对应多个SKU，不自动绑定，请人工确认");
   }
   result.add(new Preview(sourceRows.get(i),r,skuId,skuId==null?"未匹配，按手工询价物品确认":"已按SKU条码匹配",errors,warnings));
  }
  return success(result);
 }
 private static String text(String value){return Objects.toString(value,"").trim();}
 private static void checkLength(String value,int max,String field,List<String> errors){
  if(value!=null&&value.length()>max)errors.add(field+"不能超过"+max+"字");
 }
}
