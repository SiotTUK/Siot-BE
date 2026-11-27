-- 이미 저장한 http:// 행사 사진 주소를 https:// 로 바꾼다 (BE #19). 새로 수집하는 주소는 EventTexts.imageUrl 이 바꾼다.
--   수집이 다시 갱신하지 않는 행(끝난 행사, 삭제한 행사, 중복 항목에서 사진만 채운 행사 등)도 있어 여기서 한 번 바꾼다.
--   https 를 확인한 수집 출처의 호스트만 바꾼다. 관리자가 고친 사진(thumbnail_url_locked)과 관리자가 등록한 행사(MANUAL)는 그대로 둔다.
--   번역할 글이 아니라 revision 은 올리지 않는다.
UPDATE event
SET thumbnail_url = regexp_replace(thumbnail_url, '^http://', 'https://', 'i')
WHERE thumbnail_url ~* '^http://(tong\.visitkorea\.or\.kr|ggc\.ggcf\.kr)/'
  AND thumbnail_url_locked = FALSE
  AND external_source IS DISTINCT FROM 'MANUAL'
  AND length(thumbnail_url) < 2000;
