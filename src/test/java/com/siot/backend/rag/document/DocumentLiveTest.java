package com.siot.backend.rag.document;

import com.siot.backend.rag.ai.DocumentEmbedder;
import com.siot.backend.rag.config.DocumentAiConfig;
import com.siot.backend.rag.config.DocumentProperties;
import com.siot.backend.rag.config.GeminiProperties;
import com.siot.backend.rag.document.DocumentChunker.Chunk;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 실제 PDF·실제 Gemini 로 문서 적재를 확인한다 (ADM-04). 평소 ./gradlew test 와 CI 에서는 건너뛴다.
 * <pre>
 * GEMINI_LIVE=true LIVE_PDF=/path/to/guide.pdf ./gradlew test --tests '*DocumentLiveTest' --rerun
 * </pre>
 * <ul>
 *     <li>LIVE_PDF: 확인할 PDF 경로 (없으면 건너뜀)</li>
 *     <li>LIVE_DOCUMENT_NAME: 문서 이름 (첫 제목 전 조각의 첫 줄, 임베딩 title). 기본은 파일 이름</li>
 *     <li>LIVE_EMBED_LIMIT: 실제로 임베딩할 조각 수 (무료 한도 절약, 기본 20, 0 이면 임베딩 안 함)</li>
 *     <li>LIVE_EXCLUDE_PAGES: 적재에서 뺄 쪽 (예: "1-7", "1-3,5")</li>
 *     <li>GEMINI_API_KEY: .env 또는 환경 변수</li>
 * </ul>
 * 조각 전체와 표 목록은 build/live/chunks.txt 에 저장한다. 태그 PDF 의 표 조각은 [표], 표로 보이는 줄이 있는 조각은 [표?].
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "GEMINI_LIVE", matches = "true")
class DocumentLiveTest {

    private static final Path OUTPUT = Path.of("build", "live", "chunks.txt");

    @Test
    void splitAndEmbedRealPdf() throws IOException {
        String pdfPath = System.getenv("LIVE_PDF");
        assumeTrue(pdfPath != null && !pdfPath.isBlank(), "LIVE_PDF is not set");
        Path pdf = Path.of(pdfPath);
        String documentName = envOrDefault("LIVE_DOCUMENT_NAME", pdf.getFileName().toString().replaceFirst("\\.pdf$", ""));
        int embedLimit = Integer.parseInt(envOrDefault("LIVE_EMBED_LIMIT", "20"));

        long start = System.currentTimeMillis();
        java.util.SortedSet<Integer> excludedPages = PageRanges.parse(System.getenv("LIVE_EXCLUDE_PAGES"));
        ExtractedContent content = new PdfContentExtractor().extract(Files.readAllBytes(pdf), excludedPages);
        System.out.println("excluded pages: " + (excludedPages.isEmpty() ? "-" : PageRanges.format(excludedPages)));
        List<Chunk> chunks = new DocumentChunker().split(content, documentName);
        long splitMillis = System.currentTimeMillis() - start;

        String report = report(pdf, documentName, content, chunks, splitMillis);
        System.out.println(report);
        Files.createDirectories(OUTPUT.getParent());
        Files.writeString(OUTPUT, report + "\n\n" + tables(content) + "\n\n" + fullText(chunks));
        System.out.println("\nfull chunks: " + OUTPUT.toAbsolutePath());

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.content().length()).isLessThanOrEqualTo(DocumentChunker.MAX);
            assertThat(chunk.pageStart()).isBetween(1, content.pageCount());
            assertThat(chunk.pageEnd()).isBetween(chunk.pageStart(), content.pageCount());
        });

        if (embedLimit > 0) {
            embed(chunks.subList(0, Math.min(embedLimit, chunks.size())), documentName);
        }
    }

    private static String report(Path pdf, String documentName, ExtractedContent content, List<Chunk> chunks,
                                 long millis) {
        StringBuilder out = new StringBuilder();
        java.util.Set<Integer> textPages = new java.util.HashSet<>();
        for (ContentBlock block : content.blocks()) {
            switch (block) {
                case ContentBlock.TextLine line -> textPages.add(line.page());
                case ContentBlock.TableBlock table -> table.rows().forEach(row -> textPages.add(row.page()));
            }
        }
        long tableBlocks = content.blocks().stream().filter(block -> block instanceof ContentBlock.TableBlock).count();
        out.append("PDF: ").append(pdf.toAbsolutePath()).append('\n')
                .append("document name: ").append(documentName).append('\n')
                .append("extraction: ").append(content.source())
                .append(String.format(" (coverage %.1f%%)", content.coverage() * 100))
                .append(", tables: ").append(tableBlocks).append('\n')
                .append("pages: ").append(content.pageCount())
                .append(" (no text: ").append(content.pageCount() - textPages.size()).append(")\n")
                .append("chunks: ").append(chunks.size()).append(" in ").append(millis).append(" ms\n");
        if (chunks.isEmpty()) {
            return out.toString();
        }
        IntSummaryStatistics lengths = chunks.stream().mapToInt(chunk -> chunk.content().length()).summaryStatistics();
        long over = chunks.stream().filter(chunk -> chunk.content().length() > DocumentChunker.MAX).count();
        long small = chunks.stream().filter(chunk -> chunk.content().length() < 200).count();
        long structured = chunks.stream().filter(Chunk::structuredTable).count();
        long guessed = chunks.stream().filter(chunk -> chunk.tableLike() && !chunk.structuredTable()).count();
        long titles = chunks.stream().map(Chunk::title).distinct().count();
        long titled = chunks.stream().filter(chunk -> chunk.title() != null && !chunk.title().equals(documentName)).count();
        out.append("length: min ").append(lengths.getMin())
                .append(" / avg ").append(Math.round(lengths.getAverage()))
                .append(" / max ").append(lengths.getMax()).append('\n')
                .append("over ").append(DocumentChunker.MAX).append(": ").append(over)
                .append(", under 200: ").append(small)
                .append(", [표]: ").append(structured)
                .append(", [표?]: ").append(guessed)
                .append(", titled: ").append(titled).append('/').append(chunks.size())
                .append(", distinct titles: ").append(titles).append('\n')
                .append('\n')
                .append(String.format("%4s %-9s %5s  %-5s %-40s | %s%n", "no", "pages", "len", "table", "title", "start"));
        for (Chunk chunk : chunks) {
            String body = chunk.content().substring(chunk.title() == null ? 0 : chunk.title().length() + 1);
            out.append(String.format("%4d %-9s %5d  %-5s %-40s | %s%n",
                    chunk.chunkNo(),
                    chunk.pageStart() == chunk.pageEnd() ? "p" + chunk.pageStart() : "p" + chunk.pageStart() + "-" + chunk.pageEnd(),
                    chunk.content().length(),
                    chunk.structuredTable() ? "[표]" : chunk.tableLike() ? "[표?]" : "",
                    cut(chunk.title(), 40),
                    cut(body.replace('\n', '⏎'), 80)));
        }
        return out.toString();
    }

    /**
     * 태그 PDF 표 목록 (첫 행을 머리 행으로 본 결과 확인용). 머리 이름이 값처럼 보이거나(숫자·☎·주소·긴 글)
     * 2칸 표이면 표시한다: 머리 행 없이 왼쪽 칸이 항목 이름인 표일 수 있다.
     */
    private static String tables(ExtractedContent content) {
        StringBuilder out = new StringBuilder("==== tables (header = first row) ====\n");
        int index = 0;
        int flagged = 0;
        for (ContentBlock block : content.blocks()) {
            if (!(block instanceof ContentBlock.TableBlock table)) {
                continue;
            }
            index++;
            int width = table.rows().stream().mapToInt(row -> row.fields().size()).max().orElse(0);
            List<String> flags = new java.util.ArrayList<>();
            if (table.columns().isEmpty()) {
                flags.add("머리없음");
            } else {
                long columnCount = table.columns().stream().filter(java.util.Objects::nonNull).count();
                if (columnCount == 2) {
                    flags.add("2칸");
                }
                if (table.columns().stream().filter(java.util.Objects::nonNull).anyMatch(DocumentLiveTest::looksLikeValue)) {
                    flags.add("머리값?");
                }
            }
            if (!flags.isEmpty()) {
                flagged++;
            }
            out.append(String.format("T%-3d p%-3d rows=%-3d width=%-2d %-14s | header: %s%n",
                    index, table.firstPage(), table.rows().size(), width, String.join(",", flags),
                    cut(String.join(" | ", table.columns().stream().map(String::valueOf).toList()), 100)));
            out.append("       row1: ").append(cut(table.rows().getFirst().render(), 160)).append('\n');
        }
        out.append("tables: ").append(index).append(", flagged: ").append(flagged).append('\n');
        return out.toString();
    }

    private static boolean looksLikeValue(String header) {
        return header.length() > 15 || header.matches(".*(\\d|☎|www|http|로\\s?\\d|길\\s?\\d).*");
    }

    private static String fullText(List<Chunk> chunks) {
        StringBuilder out = new StringBuilder();
        for (Chunk chunk : chunks) {
            out.append("==== #").append(chunk.chunkNo())
                    .append(" p").append(chunk.pageStart()).append('-').append(chunk.pageEnd())
                    .append(" (").append(chunk.content().length()).append("자)")
                    .append(chunk.structuredTable() ? " [표]" : chunk.tableLike() ? " [표?]" : "")
                    .append(" ====\n")
                    .append(chunk.content()).append("\n\n");
        }
        return out.toString();
    }

    private static void embed(List<Chunk> chunks, String title) throws IOException {
        String apiKey = readDotEnv(Path.of(".env")).getOrDefault("GEMINI_API_KEY", System.getenv("GEMINI_API_KEY"));
        assertThat(apiKey).as("GEMINI_API_KEY in .env").isNotBlank();
        // application.yml 의 siot.gemini.*, siot.document.* 기본값과 같게 둔다
        GeminiProperties gemini = new GeminiProperties(apiKey, "unused", "gemini-embedding-001", 768, 0.2, Duration.ofSeconds(20));
        DocumentEmbedder embedder = new DocumentAiConfig().documentEmbedder(gemini);
        BatchEmbedder batchEmbedder = new BatchEmbedder(embedder,
                new DocumentProperties("unused", 100, Duration.ofSeconds(2), Duration.ofSeconds(60), 2),
                duration -> Thread.sleep(duration));

        long start = System.currentTimeMillis();
        List<float[]> vectors = batchEmbedder.embed(chunks.stream().map(Chunk::content).toList(), title);
        System.out.printf("%nembedded %d chunks in %d ms (title: %s)%n", vectors.size(), System.currentTimeMillis() - start, title);
        for (int i = 0; i < vectors.size(); i++) {
            float[] vector = vectors.get(i);
            double sum = 0;
            for (float v : vector) {
                sum += (double) v * v;
            }
            System.out.printf("[embedding #%d] dimension=%d, length=%.6f%n", chunks.get(i).chunkNo(), vector.length, Math.sqrt(sum));
            assertThat(vector).hasSize(768);
            assertThat(Math.sqrt(sum)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-4));
        }
    }

    private static String cut(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    /** KEY=VALUE 줄만 읽는다. 따옴표는 벗긴다 */
    private static Map<String, String> readDotEnv(Path path) throws IOException {
        Map<String, String> values = new HashMap<>();
        if (!Files.exists(path)) {
            return values;
        }
        for (String line : Files.readAllLines(path)) {
            String trimmed = line.strip();
            int eq = trimmed.indexOf('=');
            if (trimmed.startsWith("#") || eq <= 0) {
                continue;
            }
            String value = trimmed.substring(eq + 1).strip();
            if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                    || value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            if (!value.isEmpty()) {
                values.put(trimmed.substring(0, eq).strip(), value);
            }
        }
        return values;
    }
}
