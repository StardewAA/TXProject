package cn.iocoder.txgy.module.product.controller.app.inquiry;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class InquiryWorkbookGuardTest {
    private byte[] zip(int count, int size, String firstName) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try(var zip = new ZipOutputStream(bytes)) {
            for(var name : new String[]{"[Content_Types].xml", "xl/workbook.xml"}) {
                zip.putNextEntry(new ZipEntry(name)); zip.write("<xml/>".getBytes()); zip.closeEntry();
            }
            byte[] data = new byte[8192];
            for(int i=0;i<count;i++) {
                zip.putNextEntry(new ZipEntry(i==0?firstName:"part"+i));
                for(int remaining=size;remaining>0;remaining-=Math.min(remaining,data.length))
                    zip.write(data,0,Math.min(remaining,data.length));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
    @Test void acceptsSmallArchive() throws Exception {
        assertDoesNotThrow(()->InquiryWorkbookGuard.validate(zip(1,1024,"part0")));
    }
    @Test void rejectsOversizedActualEntry() throws Exception {
        var bytes=zip(1,8*1024*1024+1,"xl/worksheets/sheet1.xml");
        assertTrue(bytes.length<2*1024*1024);
        assertTrue(assertThrows(RuntimeException.class,()->InquiryWorkbookGuard.validate(bytes)).getMessage().contains("解压内容过大"));
        var directory=java.nio.file.Path.of("target","inquiry-qa");java.nio.file.Files.createDirectories(directory);
        java.nio.file.Files.write(directory.resolve("oversized-expanded.xlsx"),bytes);
    }
    @Test void rejectsTotalEvenWhenEachPartIsSmall() throws Exception {
        var bytes=zip(3,6*1024*1024,"part0");
        assertTrue(assertThrows(RuntimeException.class,()->InquiryWorkbookGuard.validate(bytes)).getMessage().contains("解压内容过大"));
    }
    @Test void rejectsTooManyParts() throws Exception {
        var bytes=zip(255,0,"part0");
        assertTrue(assertThrows(RuntimeException.class,()->InquiryWorkbookGuard.validate(bytes)).getMessage().contains("内部文件过多"));
    }
    @Test void rejectsUnsafeNames() throws Exception {
        for(var name:new String[]{"../escape", "/absolute", "xl/../escape", "xl\\escape", "C:escape"}) {
            var bytes=zip(1,1,name);
            assertTrue(assertThrows(RuntimeException.class,()->InquiryWorkbookGuard.validate(bytes)).getMessage().contains("路径异常"));
        }
    }
    @Test void rejectsEncryptedOrNonWorkbookBytes() {
        assertThrows(RuntimeException.class,()->InquiryWorkbookGuard.validate(new byte[]{(byte)0xd0,(byte)0xcf,0x11,(byte)0xe0}));
        assertThrows(RuntimeException.class,()->InquiryWorkbookGuard.validate(new byte[]{'P','K',3,4}));
    }
    private byte[] xmlArchive(String name, String xml) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            var parts = new java.util.LinkedHashMap<String,String>();
            parts.put("[Content_Types].xml", "<Types/>");
            parts.put("xl/workbook.xml", "<workbook/>");
            parts.put(name, xml);
            for (var part : parts.entrySet()) {
                zip.putNextEntry(new ZipEntry(part.getKey()));
                zip.write(part.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
    @Test void rejectsFormulaWithCachedValueAndNamedExpressions() throws Exception {
        for (var element : new String[]{"<f>1+1</f><v>2</v>","<f t=\"shared\" si=\"0\"/>","<definedName>1+1</definedName>"}) {
            var bytes=xmlArchive("xl/worksheets/sheet1.xml","<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"+element+"</worksheet>");
            assertTrue(assertThrows(RuntimeException.class,()->InquiryWorkbookGuard.validate(bytes)).getMessage().contains("公式"));
            var dir=java.nio.file.Path.of("target","inquiry-qa");java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.write(dir.resolve("formula.xlsx"),bytes);
        }
    }
    @Test void acceptsTextThatLooksLikeFormula() throws Exception {
        assertDoesNotThrow(()->InquiryWorkbookGuard.validate(xmlArchive("xl/sharedStrings.xml",
            "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><si><t>=1+1 &amp; &lt;f&gt;</t></si></sst>")));
    }
    @Test void rejectsExternalRelationshipsAndMacroContentTypes() throws Exception {
        var external=xmlArchive("xl/_rels/workbook.xml.rels","<Relationships><Relationship TargetMode=\"External\" Target=\"https://example.invalid/data\"/></Relationships>");
        assertTrue(assertThrows(RuntimeException.class,()->InquiryWorkbookGuard.validate(external)).getMessage().contains("外部链接"));
        var macro=xmlArchive("[Content_Types].xml","<Types><Override ContentType=\"application/vnd.ms-excel.sheet.macroEnabled.main+xml\"/></Types>");
        assertTrue(assertThrows(RuntimeException.class,()->InquiryWorkbookGuard.validate(macro)).getMessage().contains("宏"));
    }
    @Test void rejectsEmbeddedAndMacroParts() throws Exception {
        for(var name:new String[]{"xl/vbaProject.bin","xl/externalLinks/link.xml","xl/embeddings/object.xml","xl/macrosheets/sheet1.xml"}) {
            var bytes=xmlArchive(name,"<root/>");
            assertTrue(assertThrows(RuntimeException.class,()->InquiryWorkbookGuard.validate(bytes)).getMessage().contains("嵌入对象"));
        }
    }
    @Test void rejectsDoctypeMalformedAndExcessiveDepth() throws Exception {
        for(var xml:new String[]{"<!DOCTYPE a [<!ENTITY x SYSTEM 'file:///not-readable-qa'>]><a>&x;</a>","<a>","<a>".repeat(101)+"</a>".repeat(101)}) {
            var bytes=xmlArchive("xl/worksheets/sheet1.xml",xml);
            assertTrue(assertThrows(RuntimeException.class,()->InquiryWorkbookGuard.validate(bytes)).getMessage().contains("XML内容"));
        }
    }
}
