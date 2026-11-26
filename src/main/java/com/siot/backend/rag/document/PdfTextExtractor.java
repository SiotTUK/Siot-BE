package com.siot.backend.rag.document;

import com.siot.backend.rag.document.PageText.LineStyle;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * ADM-04 [EXTRACT]: PDF 쪽별 텍스트 추출 (PDFBox, 화면 위치 순서). 반복되는 머리말·꼬리말과 쪽 번호 줄은
 * {@link PageNoiseFilter} 로 뺀다. 태그 없는 PDF 에 쓴다 (태그 PDF 는 {@link TaggedPdfExtractor}).
 * 줄마다 글자 크기·굵기를 함께 모은다 (제목 판단용, {@link PageText#styles()}).
 * 스캔 이미지 PDF 는 텍스트가 없으므로 빈 결과가 된다 (OCR 은 하지 않는다).
 */
public class PdfTextExtractor {

    /** 굵은 글꼴 이름 (HWP 의 헤드라인체·Extra 굵기 포함) */
    private static final Pattern BOLD_FONT = Pattern.compile("(?i)bold|black|heavy|headline|extra(?!\\s*light)|,b$|-b$");

    /** @throws IOException PDF 를 열 수 없음 (손상·암호) */
    public List<PageText> extract(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return extract(document);
        }
    }

    public List<PageText> extract(PDDocument document) throws IOException {
        return PageNoiseFilter.clean(extractRaw(document));
    }

    /** 머리말·꼬리말을 빼기 전의 쪽 텍스트 */
    public List<PageText> extractRaw(PDDocument document) throws IOException {
        StyledStripper stripper = new StyledStripper();
        // 2단 편집·표에서 읽는 순서가 섞이지 않도록 화면 위치 순으로 읽는다
        stripper.setSortByPosition(true);
        stripper.setLineSeparator("\n");
        List<PageText> pages = new ArrayList<>(document.getNumberOfPages());
        for (int page = 1; page <= document.getNumberOfPages(); page++) {
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            String text = stripper.getText(document);
            pages.add(new PageText(page, text, stripper.takeStyles()));
        }
        return pages;
    }

    static boolean isBold(PDFont font) {
        if (font == null) {
            return false;
        }
        String name = font.getName();
        if (name != null && BOLD_FONT.matcher(name.replaceFirst("^[A-Z]{6}\\+", "")).find()) {
            return true;
        }
        PDFontDescriptor descriptor = font.getFontDescriptor();
        return descriptor != null && (descriptor.isForceBold() || descriptor.getFontWeight() >= 600);
    }

    /** 꽉 찬 줄을 판단할 오른쪽 끝을 정할 때 보는 줄의 최소 글자 수 */
    static final int MARGIN_LINE_MIN_CHARS = 15;
    /** 오른쪽 끝에 닿은 줄이 이 수 이상이어야 쪽에 일정한 오른쪽 끝이 있다고 본다 */
    static final int MARGIN_MIN_LINES = 3;

    /**
     * 줄마다 글자 크기(글자 수 가중 평균), 굵은 글자 비율, 오른쪽 끝 위치를 모은다.
     * 쪽의 오른쪽 끝(본문 줄들이 닿는 곳)까지 찬 줄은 다음 줄로 이어지는 줄로 본다 ({@link LineStyle#fullWidth()}).
     */
    private static final class StyledStripper extends PDFTextStripper {

        private final StringBuilder line = new StringBuilder();
        private final Map<String, float[]> lines = new HashMap<>();
        private double sizeSum;
        private int chars;
        private int boldChars;
        private float endX;
        private double widthSum;

        StyledStripper() {
            super();
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) throws IOException {
            super.writeString(text, positions);
            line.append(text);
            for (TextPosition position : positions) {
                if (position.getUnicode() == null || position.getUnicode().isBlank()) {
                    continue;
                }
                chars++;
                sizeSum += position.getFontSizeInPt();
                endX = Math.max(endX, position.getXDirAdj() + position.getWidthDirAdj());
                widthSum += position.getWidthDirAdj();
                if (isBold(position.getFont())) {
                    boldChars++;
                }
            }
        }

        @Override
        protected void writeWordSeparator() throws IOException {
            super.writeWordSeparator();
            line.append(' ');
        }

        @Override
        protected void writeLineSeparator() throws IOException {
            super.writeLineSeparator();
            flushLine();
        }

        @Override
        protected void writePageEnd() throws IOException {
            flushLine();
            super.writePageEnd();
        }

        /** 줄 → {글자 크기, 굵음(1/0), 오른쪽 끝, 글자 수, 평균 글자 너비} */
        private void flushLine() {
            String key = styleKey(line.toString());
            if (!key.isEmpty() && chars > 0) {
                lines.putIfAbsent(key, new float[]{(float) (sizeSum / chars), boldChars * 2 >= chars ? 1 : 0, endX,
                        key.length(), (float) (widthSum / chars)});
            }
            line.setLength(0);
            sizeSum = 0;
            chars = 0;
            boldChars = 0;
            endX = 0;
            widthSum = 0;
        }

        Map<String, LineStyle> takeStyles() {
            flushLine();
            float margin = 0;
            for (float[] value : lines.values()) {
                if (value[3] >= MARGIN_LINE_MIN_CHARS) {
                    margin = Math.max(margin, value[2]);
                }
            }
            int atMargin = 0;
            for (float[] value : lines.values()) {
                if (value[3] >= MARGIN_LINE_MIN_CHARS && reachesMargin(value, margin)) {
                    atMargin++;
                }
            }
            boolean hasMargin = atMargin >= MARGIN_MIN_LINES;
            Map<String, LineStyle> styles = new HashMap<>();
            for (Map.Entry<String, float[]> entry : lines.entrySet()) {
                float[] value = entry.getValue();
                styles.put(entry.getKey(),
                        new LineStyle(value[0], value[1] > 0, hasMargin && reachesMargin(value, margin)));
            }
            lines.clear();
            return styles;
        }

        /**
         * 오른쪽 끝에서 글자 2개 너비 안에서 끝남. 글자 크기(pt)는 글자 행렬 배율 때문에 실제 크기와 다를 수 있어
         * 화면에서 잰 평균 글자 너비를 쓴다
         */
        private static boolean reachesMargin(float[] value, float margin) {
            return value[2] >= margin - value[4] * 2;
        }
    }

    /** {@link PageText#styles()} 의 열쇠: 줄바꿈 없는 공백을 보통 공백으로 바꾸고 앞뒤 공백을 뺀 줄 */
    static String styleKey(String line) {
        return line.replace(' ', ' ').strip();
    }
}
