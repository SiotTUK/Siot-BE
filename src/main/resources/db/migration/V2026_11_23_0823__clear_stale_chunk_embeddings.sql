-- ADM-04, CB-14: 현재 버전이 아닌 완료 버전 조각의 임베딩을 비우고, HNSW 인덱스를 없앤다.
-- 조각 행(글·쪽·조각 번호)은 지우지 않는다 (예전 답변 출처 유지, CB-17).
-- 실패·처리 중 버전은 건드리지 않는다 (조각 저장과 버전 전환이 한 트랜잭션이라 보통 조각이 없다).
--
-- HNSW 는 후보 40개(ef_search)를 먼저 뽑고 현재 버전 조건으로 거른다. 옛 버전 조각이 후보를 채우면 결과가 0개가 되고,
-- 임베딩을 NULL 로 바꿔도 VACUUM 전에는 인덱스에 남는다. 옛 조각이 없어도 근사 검색이라 가장 가까운 조각을 놓칠 수 있다.
-- 현재 조각이 수천 개라 인덱스 없이 정확 검색해도 충분히 빠르다. 다시 만들려면:
--   CREATE INDEX idx_document_chunk_embedding ON document_chunk USING hnsw (embedding vector_cosine_ops);
-- 되돌릴 때 비운 임베딩은 문서 재처리로만 다시 생긴다.

UPDATE document_chunk c
SET embedding = NULL
FROM document_version v
WHERE v.document_version_id = c.document_version_id
  AND NOT v.is_current
  AND v.processing_status = 'COMPLETED'
  AND c.embedding IS NOT NULL;

DROP INDEX IF EXISTS idx_document_chunk_embedding;

ANALYZE document_chunk;
