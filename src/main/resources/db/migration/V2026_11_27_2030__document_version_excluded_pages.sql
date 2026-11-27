-- ADM-04: 버전마다 적재에서 뺄 쪽 (빈칸 서식 등). "51-52,100" 형태, NULL 이면 빼는 쪽 없음.
-- 쪽 번호는 그 버전 파일의 PDF 뷰어 기준(1부터)이라 문서가 아니라 버전에 둔다.
ALTER TABLE document_version ADD COLUMN excluded_pages TEXT;
