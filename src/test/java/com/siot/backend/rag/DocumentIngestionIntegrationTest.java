package com.siot.backend.rag;

import com.jayway.jsonpath.JsonPath;
import com.siot.backend.TestcontainersConfiguration;
import com.siot.backend.auth.service.SignupVerificationService;
import com.siot.backend.rag.ai.AiUnavailableException;
import com.siot.backend.rag.ai.DocumentEmbedder;
import com.siot.backend.rag.ai.RetrievedChunk;
import com.siot.backend.rag.document.TaggedTestPdf;
import com.siot.backend.rag.document.TestPdfs;
import com.siot.backend.rag.domain.DocumentChunk;
import com.siot.backend.rag.service.ChunkRetriever;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADM-02~04, NTF-06 문서 적재 통합 테스트. Gemini 임베딩은 모킹한다.
 * 임베딩 모킹: 조각에 "VERSION TWO" 가 있으면 1번 축, 아니면 0번 축 벡터를 돌려준다.
 */
@SpringBootTest(properties = {
        "siot.admin.emails=doc-admin@example.com",
        "siot.document.storage-dir=build/test-documents",
        "siot.document.embedding-batch-pause=0s",
        "siot.document.embedding-retry-wait=0s"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DocumentIngestionIntegrationTest {

    private static final String ADMIN_EMAIL = "doc-admin@example.com";
    private static final String MEMBER_EMAIL = "doc-member@example.com";
    private static final String PASSWORD = "abcd1234";
    private static final String SOURCE_URL = "https://www.siheung.go.kr/guide.pdf";
    /** PDFBox 는 저장할 때마다 문서 ID 가 바뀌므로 같은 파일 확인에는 한 번 만든 바이트를 쓴다 */
    private static final byte[] VERSION_ONE_PDF = versionOnePdf();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    ChunkRetriever chunkRetriever;

    @MockitoBean
    DocumentEmbedder documentEmbedder;

    private Cookie admin;

    @BeforeEach
    void setUp() throws Exception {
        reset(documentEmbedder);
        when(documentEmbedder.embedAll(anyList(), anyString())).thenAnswer(call -> fakeVectors(call.getArgument(0)));
        admin = login(ADMIN_EMAIL);
    }

    @Test
    void uploadIsIndexedAndBecomesCurrent() throws Exception {
        String name = "Living Guide " + UUID.randomUUID();
        MvcResult created = mockMvc.perform(multipart("/api/admin/documents")
                        .file(pdf(VERSION_ONE_PDF))
                        .param("name", name)
                        .param("sourceUrl", SOURCE_URL)
                        .param("publishedOn", "2026-03-01")
                        .cookie(admin).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value(name))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.currentVersion").doesNotExist())
                .andExpect(jsonPath("$.data.latestVersion.versionNo").value(1))
                .andExpect(jsonPath("$.data.latestVersion.status").value("PENDING"))
                .andReturn();
        long documentId = id(created, "$.data.documentId");

        awaitStatus(documentId, 1, "COMPLETED");

        mockMvc.perform(get("/api/admin/documents/{id}/versions", documentId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].versionNo").value(1))
                .andExpect(jsonPath("$.data[0].changeType").value("NEW"))
                .andExpect(jsonPath("$.data[0].current").value(true))
                .andExpect(jsonPath("$.data[0].chunkCount").value(2))
                .andExpect(jsonPath("$.data[0].failedStage").doesNotExist())
                .andExpect(jsonPath("$.data[0].originalFilename").value("guide.pdf"))
                .andExpect(jsonPath("$.data[0].uploadedAt").exists())
                .andExpect(jsonPath("$.data[0].processedAt").exists());

        // 쪽마다 절이 바뀌어 조각 2개. 첫 줄은 절 제목, 쪽 번호·조각 번호는 1부터, 정해진 임베딩 모델
        List<String> rows = jdbcTemplate.query("""
                SELECT c.chunk_no, c.page_start, c.page_end, c.embedding_model, c.content
                FROM document_chunk c JOIN document_version v ON v.document_version_id = c.document_version_id
                WHERE v.document_id = ? ORDER BY c.chunk_no""",
                (rs, i) -> rs.getInt(1) + "|" + rs.getInt(2) + "|" + rs.getInt(3) + "|" + rs.getString(4) + "|"
                        + rs.getString(5).lines().findFirst().orElse(""), documentId);
        assertThat(rows).containsExactly(
                "1|1|1|gemini-embedding-001|1. Alien Registration",
                "2|2|2|gemini-embedding-001|2. Health Insurance");
        // 문서 이름을 title 로 넘긴다
        verify(documentEmbedder).embedAll(anyList(), eq(name));

        mockMvc.perform(get("/api/admin/documents").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].documentId").value(documentId))
                .andExpect(jsonPath("$.data.content[0].currentVersion.versionNo").value(1))
                .andExpect(jsonPath("$.data.content[0].currentVersion.status").value("COMPLETED"));
    }

    @Test
    void failedVersionKeepsPreviousAndRetrySwitchesSearch() throws Exception {
        String name = "Switch Guide " + UUID.randomUUID();
        long documentId = createAndAwait(name);

        // 새 버전 임베딩 실패 → FAILED [EMBEDDING], 이전 버전 유지
        when(documentEmbedder.embedAll(argThat(texts -> texts.stream().anyMatch(t -> t.contains("VERSION TWO"))), anyString()))
                .thenThrow(new AiUnavailableException("Gemini document embedding failed", new RuntimeException("quota exceeded")));
        MvcResult uploaded = mockMvc.perform(multipart("/api/admin/documents/{id}/versions", documentId)
                        .file(pdf(versionTwoPdf()))
                        .param("changeSummary", "2026 rules")
                        .cookie(admin).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.versionNo").value(2))
                .andExpect(jsonPath("$.data.changeType").value("REPLACE"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        long versionTwo = id(uploaded, "$.data.versionId");

        awaitStatus(documentId, 2, "FAILED");
        mockMvc.perform(get("/api/admin/documents/{id}/versions", documentId).cookie(admin))
                .andExpect(jsonPath("$.data[0].versionNo").value(2))
                .andExpect(jsonPath("$.data[0].current").value(false))
                .andExpect(jsonPath("$.data[0].failedStage").value("EMBEDDING"))
                .andExpect(jsonPath("$.data[0].failureReason").value(org.hamcrest.Matchers.containsString("quota exceeded")))
                .andExpect(jsonPath("$.data[0].processedAt").exists())
                .andExpect(jsonPath("$.data[0].chunkCount").value(0))
                .andExpect(jsonPath("$.data[1].versionNo").value(1))
                .andExpect(jsonPath("$.data[1].current").value(true));
        assertThat(searchContents(name)).isNotEmpty().allSatisfy(content -> assertThat(content).doesNotContain("VERSION TWO"));

        // 재실행 → 새 버전으로 전환, 검색은 새 버전만. 이전 버전 조각은 지우지 않는다
        reset(documentEmbedder);
        when(documentEmbedder.embedAll(anyList(), anyString())).thenAnswer(call -> fakeVectors(call.getArgument(0)));
        mockMvc.perform(post("/api/admin/document-versions/{id}/retry", versionTwo).cookie(admin).with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
        awaitStatus(documentId, 2, "COMPLETED");

        assertThat(currentVersionNos(documentId)).containsExactly(2);
        List<String> contents = searchContents(name);
        assertThat(contents).isNotEmpty().allSatisfy(content -> assertThat(content).contains("VERSION TWO"));
        Integer oldChunks = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM document_chunk c JOIN document_version v ON v.document_version_id = c.document_version_id
                WHERE v.document_id = ? AND v.version_no = 1""", Integer.class, documentId);
        assertThat(oldChunks).isEqualTo(2);

        // 완료된 버전은 다시 반영할 수 없다
        mockMvc.perform(post("/api/admin/document-versions/{id}/retry", versionTwo).cookie(admin).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_VERSION_NOT_RETRYABLE"));
    }

    @Test
    void olderFailedVersionCannotReplaceNewerCurrent() throws Exception {
        long documentId = createAndAwait("Old Retry Guide " + UUID.randomUUID());

        when(documentEmbedder.embedAll(argThat(texts -> texts.stream().anyMatch(t -> t.contains("VERSION TWO"))), anyString()))
                .thenThrow(new AiUnavailableException("Gemini document embedding failed"));
        long versionTwo = id(mockMvc.perform(multipart("/api/admin/documents/{id}/versions", documentId)
                        .file(pdf(versionTwoPdf())).cookie(admin).with(csrf()))
                .andExpect(status().isCreated()).andReturn(), "$.data.versionId");
        awaitStatus(documentId, 2, "FAILED");

        mockMvc.perform(multipart("/api/admin/documents/{id}/versions", documentId)
                        .file(pdf(versionThreePdf())).cookie(admin).with(csrf()))
                .andExpect(status().isCreated());
        awaitStatus(documentId, 3, "COMPLETED");

        mockMvc.perform(post("/api/admin/document-versions/{id}/retry", versionTwo).cookie(admin).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_VERSION_NOT_RETRYABLE"));
        assertThat(currentVersionNos(documentId)).containsExactly(3);
    }

    @Test
    void taggedPdfStoresTableRowsAsOwnChunk() throws Exception {
        byte[] tagged = TaggedTestPdf.create()
                .page().artifact("Siheung Living Guide 17").paragraph("Foreign residents can join these programs.")
                .page().artifact("18 Welcome to Siheung")
                .paragraph("Welfare Center Programs")
                .table(List.of(
                        List.of(TaggedTestPdf.Cell.of("Type"), TaggedTestPdf.Cell.of("Program"), TaggedTestPdf.Cell.of("Target")),
                        List.of(new TaggedTestPdf.Cell("Korean class", 2, 1), TaggedTestPdf.Cell.of("Weekend class"),
                                TaggedTestPdf.Cell.of("Adults")),
                        List.of(TaggedTestPdf.Cell.of("Weekday class"), TaggedTestPdf.Cell.of("Workers"))))
                .build();
        long documentId = id(mockMvc.perform(multipart("/api/admin/documents")
                        .file(pdf(tagged))
                        .param("name", "Tagged Guide " + UUID.randomUUID())
                        .param("sourceUrl", SOURCE_URL)
                        .cookie(admin).with(csrf()))
                .andExpect(status().isCreated()).andReturn(), "$.data.documentId");

        awaitStatus(documentId, 1, "COMPLETED");

        List<String> rows = jdbcTemplate.query("""
                SELECT c.page_start, c.page_end, c.content
                FROM document_chunk c JOIN document_version v ON v.document_version_id = c.document_version_id
                WHERE v.document_id = ? ORDER BY c.chunk_no""",
                (rs, i) -> rs.getInt(1) + "-" + rs.getInt(2) + "|" + rs.getString(3), documentId);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1)).isEqualTo("""
                2-2|Welfare Center Programs
                Type: Korean class / Program: Weekend class / Target: Adults
                Type: Korean class / Program: Weekday class / Target: Workers""");
        assertThat(String.join("\n", rows)).doesNotContain("Siheung Living Guide", "Welcome to Siheung");
    }

    @Test
    void textlessPdfFailsAtExtract() throws Exception {
        long documentId = id(mockMvc.perform(multipart("/api/admin/documents")
                        .file(pdf(TestPdfs.blank(1)))
                        .param("name", "Scanned " + UUID.randomUUID())
                        .param("sourceUrl", SOURCE_URL)
                        .cookie(admin).with(csrf()))
                .andExpect(status().isCreated()).andReturn(), "$.data.documentId");

        awaitStatus(documentId, 1, "FAILED");
        mockMvc.perform(get("/api/admin/documents/{id}/versions", documentId).cookie(admin))
                .andExpect(jsonPath("$.data[0].failedStage").value("EXTRACT"))
                .andExpect(jsonPath("$.data[0].current").value(false));
    }

    @Test
    void rejectsSameFileAndUploadWhileInProgress() throws Exception {
        long documentId = createAndAwait("Same File Guide " + UUID.randomUUID());

        mockMvc.perform(multipart("/api/admin/documents/{id}/versions", documentId)
                        .file(pdf(VERSION_ONE_PDF)).cookie(admin).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_FILE_UNCHANGED"));

        // 반영 대기 중인 버전이 있으면 새 버전을 받지 않는다
        jdbcTemplate.update("INSERT INTO document_version (document_id, version_no, file_storage_key, processing_status) "
                + "VALUES (?, 9, 'documents/test/missing.pdf', 'PENDING')", documentId);
        mockMvc.perform(multipart("/api/admin/documents/{id}/versions", documentId)
                        .file(pdf(versionTwoPdf())).cookie(admin).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_VERSION_IN_PROGRESS"));
        jdbcTemplate.update("DELETE FROM document_version WHERE document_id = ? AND version_no = 9", documentId);
    }

    @Test
    void reprocessUsesSameFileAndBecomesCurrent() throws Exception {
        String name = "Reprocess Guide " + UUID.randomUUID();
        long documentId = createAndAwait(name);

        mockMvc.perform(post("/api/admin/documents/{id}/reprocess", documentId).cookie(admin).with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.versionNo").value(2))
                .andExpect(jsonPath("$.data.changeType").value("REPROCESS"))
                .andExpect(jsonPath("$.data.changeSummary").value("조각 규칙 변경으로 재처리"))
                .andExpect(jsonPath("$.data.originalFilename").value("guide.pdf"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.current").value(false));
        awaitStatus(documentId, 2, "COMPLETED");

        // 파일은 다시 저장하지 않고 같은 저장 키·checksum 을 쓴다
        Integer distinctFiles = jdbcTemplate.queryForObject(
                "SELECT count(DISTINCT (file_storage_key, checksum)) FROM document_version WHERE document_id = ?",
                Integer.class, documentId);
        assertThat(distinctFiles).isEqualTo(1);
        assertThat(currentVersionNos(documentId)).containsExactly(2);

        // 검색은 새 버전 조각만, 이전 버전 조각은 지우지 않는다 (CB-17)
        assertThat(searchedVersionNos(name)).isNotEmpty().containsOnly(2);
        assertThat(chunkCount(documentId, 1)).isEqualTo(2);
        assertThat(chunkCount(documentId, 2)).isEqualTo(2);

        // 같은 파일 새 버전 업로드는 계속 막는다
        mockMvc.perform(multipart("/api/admin/documents/{id}/versions", documentId)
                        .file(pdf(VERSION_ONE_PDF)).cookie(admin).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_FILE_UNCHANGED"));
    }

    @Test
    void failedReprocessKeepsPreviousVersion() throws Exception {
        String name = "Reprocess Fail Guide " + UUID.randomUUID();
        long documentId = createAndAwait(name);

        when(documentEmbedder.embedAll(anyList(), anyString()))
                .thenThrow(new AiUnavailableException("Gemini document embedding failed", new RuntimeException("quota exceeded")));
        long versionTwo = id(mockMvc.perform(post("/api/admin/documents/{id}/reprocess", documentId)
                        .cookie(admin).with(csrf()))
                .andExpect(status().isAccepted()).andReturn(), "$.data.versionId");
        awaitStatus(documentId, 2, "FAILED");

        assertThat(currentVersionNos(documentId)).containsExactly(1);
        assertThat(searchedVersionNos(name)).isNotEmpty().containsOnly(1);
        assertThat(chunkCount(documentId, 2)).isZero();

        // 실패한 재처리 버전은 다시 반영으로 살린다
        reset(documentEmbedder);
        when(documentEmbedder.embedAll(anyList(), anyString())).thenAnswer(call -> fakeVectors(call.getArgument(0)));
        mockMvc.perform(post("/api/admin/document-versions/{id}/retry", versionTwo).cookie(admin).with(csrf()))
                .andExpect(status().isAccepted());
        awaitStatus(documentId, 2, "COMPLETED");
        assertThat(currentVersionNos(documentId)).containsExactly(2);
    }

    @Test
    void reprocessRejectsInProgressMissingCurrentAndNonAdmin() throws Exception {
        long documentId = createAndAwait("Reprocess Reject Guide " + UUID.randomUUID());

        // 대기 중인 버전이 있으면 재처리하지 않는다
        jdbcTemplate.update("INSERT INTO document_version (document_id, version_no, file_storage_key, processing_status) "
                + "VALUES (?, 9, 'documents/test/missing.pdf', 'PENDING')", documentId);
        mockMvc.perform(post("/api/admin/documents/{id}/reprocess", documentId).cookie(admin).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_VERSION_IN_PROGRESS"));
        jdbcTemplate.update("DELETE FROM document_version WHERE document_id = ? AND version_no = 9", documentId);

        // 첫 버전이 실패해 현재 버전이 없으면 재처리할 수 없다
        long failedDocument = id(mockMvc.perform(multipart("/api/admin/documents")
                        .file(pdf(TestPdfs.blank(1)))
                        .param("name", "No Current Guide " + UUID.randomUUID())
                        .param("sourceUrl", SOURCE_URL)
                        .cookie(admin).with(csrf()))
                .andExpect(status().isCreated()).andReturn(), "$.data.documentId");
        awaitStatus(failedDocument, 1, "FAILED");
        mockMvc.perform(post("/api/admin/documents/{id}/reprocess", failedDocument).cookie(admin).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_CURRENT_VERSION_MISSING"));

        mockMvc.perform(post("/api/admin/documents/{id}/reprocess", Long.MAX_VALUE).cookie(admin).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));

        mockMvc.perform(post("/api/admin/documents/{id}/reprocess", documentId)
                        .cookie(login(MEMBER_EMAIL)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void validatesUploadAndRequiresAdmin() throws Exception {
        mockMvc.perform(multipart("/api/admin/documents")
                        .file(new MockMultipartFile("file", "notes.pdf", "application/pdf", "hello".getBytes()))
                        .param("name", "Not a PDF")
                        .param("sourceUrl", SOURCE_URL)
                        .cookie(admin).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DOCUMENT_FILE_INVALID"));

        mockMvc.perform(multipart("/api/admin/documents")
                        .file(pdf(VERSION_ONE_PDF))
                        .param("name", "No Link")
                        .cookie(admin).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(multipart("/api/admin/documents")
                        .param("name", "No File")
                        .param("sourceUrl", SOURCE_URL)
                        .cookie(admin).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(get("/api/admin/documents/{id}/versions", Long.MAX_VALUE).cookie(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));

        Cookie member = login(MEMBER_EMAIL);
        mockMvc.perform(get("/api/admin/documents").cookie(member))
                .andExpect(status().isForbidden());
    }

    @Test
    void deactivatedDocumentLeavesSearch() throws Exception {
        String name = "Inactive Guide " + UUID.randomUUID();
        long documentId = createAndAwait(name);
        assertThat(searchContents(name)).isNotEmpty();

        mockMvc.perform(patch("/api/admin/documents/{id}", documentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": false, \"name\": \"" + name + "\", \"publishedOn\": \"2026-04-01\"}")
                        .cookie(admin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.publishedOn").value("2026-04-01"))
                .andExpect(jsonPath("$.data.sourceUrl").value(SOURCE_URL))
                .andExpect(jsonPath("$.data.currentVersion.versionNo").value(1));

        assertThat(searchContents(name)).isEmpty();

        mockMvc.perform(patch("/api/admin/documents/{id}", documentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceUrl\": \"not-a-url\"}")
                        .cookie(admin).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- helpers

    private long createAndAwait(String name) throws Exception {
        long documentId = id(mockMvc.perform(multipart("/api/admin/documents")
                        .file(pdf(VERSION_ONE_PDF))
                        .param("name", name)
                        .param("sourceUrl", SOURCE_URL)
                        .cookie(admin).with(csrf()))
                .andExpect(status().isCreated()).andReturn(), "$.data.documentId");
        awaitStatus(documentId, 1, "COMPLETED");
        return documentId;
    }

    /** 반영은 뒤에서 진행되므로 상태가 바뀔 때까지 기다린다 */
    private void awaitStatus(long documentId, int versionNo, String expected) throws InterruptedException {
        String status = null;
        for (int i = 0; i < 150; i++) {
            List<String> rows = jdbcTemplate.queryForList(
                    "SELECT processing_status FROM document_version WHERE document_id = ? AND version_no = ?",
                    String.class, documentId, versionNo);
            status = rows.isEmpty() ? null : rows.getFirst();
            if (expected.equals(status)) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("document " + documentId + " v" + versionNo + " status " + status + ", expected " + expected);
    }

    private int chunkCount(long documentId, int versionNo) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FROM document_chunk c JOIN document_version v ON v.document_version_id = c.document_version_id
                WHERE v.document_id = ? AND v.version_no = ?""", Integer.class, documentId, versionNo);
    }

    /** 이 문서에서 검색되는 조각의 버전 번호 */
    private List<Integer> searchedVersionNos(String documentName) {
        List<Integer> versionNos = new ArrayList<>();
        for (int axis = 0; axis < 2; axis++) {
            for (RetrievedChunk chunk : chunkRetriever.search(axis(axis), 50)) {
                if (documentName.equals(chunk.documentName())) {
                    versionNos.add(jdbcTemplate.queryForObject("""
                            SELECT v.version_no FROM document_chunk c
                            JOIN document_version v ON v.document_version_id = c.document_version_id
                            WHERE c.chunk_id = ?""", Integer.class, chunk.chunkId()));
                }
            }
        }
        return versionNos;
    }

    private List<Integer> currentVersionNos(long documentId) {
        return jdbcTemplate.queryForList(
                "SELECT version_no FROM document_version WHERE document_id = ? AND is_current", Integer.class, documentId);
    }

    /** 이 문서에서 검색되는 조각 내용 (두 축 모두로 검색) */
    private List<String> searchContents(String documentName) {
        List<String> contents = new ArrayList<>();
        for (int axis = 0; axis < 2; axis++) {
            for (RetrievedChunk chunk : chunkRetriever.search(axis(axis), 50)) {
                if (documentName.equals(chunk.documentName())) {
                    contents.add(chunk.content());
                }
            }
        }
        return contents;
    }

    private static List<float[]> fakeVectors(List<String> texts) {
        return texts.stream().map(text -> axis(text.contains("VERSION TWO") ? 1 : 0)).toList();
    }

    private static float[] axis(int index) {
        float[] vector = new float[DocumentChunk.EMBEDDING_DIMENSION];
        vector[index] = 1f;
        return vector;
    }

    private static byte[] versionOnePdf() {
        return TestPdfs.create(List.of(
                List.of("1. Alien Registration", "Register within 90 days of entry at the immigration office."),
                List.of("2. Health Insurance", "Foreign residents join health insurance after six months.")));
    }

    private static byte[] versionTwoPdf() {
        return TestPdfs.create(List.of(
                List.of("1. Alien Registration", "VERSION TWO: register within 90 days, book a visit online."),
                List.of("2. Health Insurance", "VERSION TWO: premiums are paid every month.")));
    }

    private static byte[] versionThreePdf() {
        return TestPdfs.create(List.of(List.of("1. Car Tax", "Version three: car tax is paid in June and December.")));
    }

    private static MockMultipartFile pdf(byte[] content) {
        return new MockMultipartFile("file", "guide.pdf", "application/pdf", content);
    }

    private static long id(MvcResult result, String path) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), path)).longValue();
    }

    private Cookie login(String email) throws Exception {
        Integer exists = jdbcTemplate.queryForObject("SELECT count(*) FROM app_user WHERE email = ?", Integer.class, email);
        if (exists == 0) {
            redisTemplate.opsForValue().set(SignupVerificationService.VERIFIED_KEY + email, "1");
            mockMvc.perform(post("/api/auth/signup").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                                    + "\",\"passwordConfirm\":\"" + PASSWORD + "\"}"))
                    .andExpect(status().isCreated());
        }
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("SIOT_SESSION");
    }
}
