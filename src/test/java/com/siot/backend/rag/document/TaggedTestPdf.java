package com.siot.backend.rag.document;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkInfo;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkedContentReference;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.PDTableAttributeObject;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.util.Matrix;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

/**
 * 테스트용 태그 PDF (InDesign 처럼 문단 스타일 이름을 역할 맵으로 P 에 연결한다).
 * 머리말·꼬리말은 Artifact, 문단·표 칸은 MCID 로 구조 트리에 연결한다. Helvetica 라 영문만 쓴다.
 */
public final class TaggedTestPdf {

    /** 표 칸 */
    public record Cell(String text, int rowSpan, int colSpan) {

        public static Cell of(String text) {
            return new Cell(text, 1, 1);
        }
    }

    private static final float SIZE = 10;
    private static final float LEADING = 14;
    private static final COSName ARTIFACT = COSName.getPDFName("Artifact");
    private static final COSName P = COSName.getPDFName("P");
    private static final COSName TD = COSName.getPDFName("TD");

    private final PDDocument document = new PDDocument();
    private final PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDStructureElement root;
    private PDPage page;
    private PDPageContentStream stream;
    private PDStructureElement lastParagraph;
    private int mcid;
    private float y;

    private TaggedTestPdf() {
        PDStructureTreeRoot treeRoot = new PDStructureTreeRoot();
        treeRoot.setRoleMap(Map.of("NormalParagraphStyle", "P", "Story", "Sect"));
        document.getDocumentCatalog().setStructureTreeRoot(treeRoot);
        PDMarkInfo markInfo = new PDMarkInfo();
        markInfo.setMarked(true);
        document.getDocumentCatalog().setMarkInfo(markInfo);
        root = new PDStructureElement("Story", treeRoot);
        treeRoot.appendKid(root);
    }

    public static TaggedTestPdf create() {
        return new TaggedTestPdf();
    }

    public TaggedTestPdf page() {
        closeStream();
        page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        try {
            stream = new PDPageContentStream(document, page);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        mcid = 0;
        y = 790;
        return this;
    }

    /** 머리말·꼬리말 (구조 트리에 없음) */
    public TaggedTestPdf artifact(String text) {
        try {
            stream.beginMarkedContent(ARTIFACT);
            draw(text, 50);
            stream.endMarkedContent();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    /** 구조 트리에 없는 일반 텍스트 (태그가 일부만 된 PDF) */
    public TaggedTestPdf untagged(String text) {
        try {
            draw(text, 50);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    /** 문단 (역할 맵으로 P 가 되는 NormalParagraphStyle) */
    public TaggedTestPdf paragraph(String text) {
        lastParagraph = element("NormalParagraphStyle", root);
        lastParagraph.appendKid(markedText(P, text));
        return this;
    }

    /** 단어 사이에 공백 글자 없이 간격만 두고 그린 문단 (띄어쓰기 복원 확인용) */
    public TaggedTestPdf paragraphWithoutSpaceGlyphs(String... words) {
        lastParagraph = element("NormalParagraphStyle", root);
        int id = mcid++;
        try {
            stream.beginMarkedContent(P, id);
            stream.beginText();
            stream.setFont(font, SIZE);
            float x = 50;
            for (String word : words) {
                stream.setTextMatrix(Matrix.getTranslateInstance(x, y));
                stream.showText(word);
                x += font.getStringWidth(word) / 1000 * SIZE + 4;
            }
            stream.endText();
            stream.endMarkedContent();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        y -= LEADING;
        lastParagraph.appendKid(id);
        return this;
    }

    /** 화면의 한 줄을 구조 요소 여러 개로 나눈 것 ("※" / "외국인근로자에게 적용되는" / "4" / "대 보험 …" 같은 PDF) */
    public TaggedTestPdf paragraphsOnOneLine(String... parts) {
        float x = 50;
        for (String part : parts) {
            PDStructureElement element = element("NormalParagraphStyle", root);
            element.appendKid(markedText(P, part, x, false));
            try {
                x += font.getStringWidth(part) / 1000 * SIZE + 1;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        y -= LEADING;
        return this;
    }

    /** 앞 쪽의 마지막 문단이 이 쪽으로 이어짐 (MCR 로 다른 쪽 내용을 가리킨다) */
    public TaggedTestPdf continueParagraph(String text) {
        PDMarkedContentReference reference = new PDMarkedContentReference();
        reference.setMCID(markedText(P, text));
        reference.setPage(page);
        lastParagraph.appendKid(reference);
        return this;
    }

    /** 표. 행마다 칸 목록 (RowSpan·ColSpan 으로 가려진 자리는 빼고 적는다) */
    public TaggedTestPdf table(List<List<Cell>> rows) {
        PDStructureElement table = element("Table", root);
        for (List<Cell> row : rows) {
            PDStructureElement tr = element("TR", table);
            float x = 50;
            for (Cell cell : row) {
                PDStructureElement td = element("TD", tr);
                if (cell.rowSpan() > 1 || cell.colSpan() > 1) {
                    PDTableAttributeObject attribute = new PDTableAttributeObject();
                    if (cell.rowSpan() > 1) {
                        attribute.setRowSpan(cell.rowSpan());
                    }
                    if (cell.colSpan() > 1) {
                        attribute.setColSpan(cell.colSpan());
                    }
                    td.addAttribute(attribute);
                }
                if (!cell.text().isEmpty()) {
                    td.appendKid(markedText(TD, cell.text(), x, false));
                }
                x += 150 * cell.colSpan();
            }
            y -= LEADING;
        }
        return this;
    }

    public byte[] build() {
        closeStream();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.save(out);
            document.close();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private PDStructureElement element(String type, PDStructureElement parent) {
        PDStructureElement element = new PDStructureElement(type, parent);
        element.setPage(page);
        parent.appendKid(element);
        return element;
    }

    private int markedText(COSName tag, String text) {
        return markedText(tag, text, 50, true);
    }

    private int markedText(COSName tag, String text, float x, boolean newLine) {
        int id = mcid++;
        try {
            stream.beginMarkedContent(tag, id);
            stream.beginText();
            stream.setFont(font, SIZE);
            stream.newLineAtOffset(x, y);
            stream.showText(text);
            stream.endText();
            stream.endMarkedContent();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (newLine) {
            y -= LEADING;
        }
        return id;
    }

    private void draw(String text, float x) throws IOException {
        stream.beginText();
        stream.setFont(font, SIZE);
        stream.newLineAtOffset(x, y);
        stream.showText(text);
        stream.endText();
        y -= LEADING;
    }

    private void closeStream() {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            stream = null;
        }
    }
}
