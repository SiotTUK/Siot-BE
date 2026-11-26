-- #14 FE 생활수칙 데이터 시드. scripts/generate_rule_seed.mjs 로 Siot-FE src/features/rules/content.ts·messages.ts 에서 만들었다.
-- 손으로 고치지 않는다. 항목 18개, FAQ 5개, 카테고리 6개, 4개 언어. 공식 페이지 확인일 2026-11-25.
-- 4개 언어 문구는 사람이 확인했으므로 reviewed = TRUE (자동 번역이 덮어쓰지 않는다).

-- 카테고리: 한국어 이름 = guide.category, FE 키·순서, en/zh/vi 이름
INSERT INTO rule_category (category, category_key, sort_order) VALUES ('기초 질서', 'order', 10)
ON CONFLICT (category) DO UPDATE SET category_key = EXCLUDED.category_key, sort_order = EXCLUDED.sort_order;
INSERT INTO rule_category_translation (category, language, name) VALUES ('기초 질서', 'en', 'Public order')
ON CONFLICT (category, language) DO UPDATE SET name = EXCLUDED.name, updated_at = CURRENT_TIMESTAMP;
INSERT INTO rule_category_translation (category, language, name) VALUES ('기초 질서', 'zh', '公共秩序')
ON CONFLICT (category, language) DO UPDATE SET name = EXCLUDED.name, updated_at = CURRENT_TIMESTAMP;
INSERT INTO rule_category_translation (category, language, name) VALUES ('기초 질서', 'vi', 'Trật tự công cộng')
ON CONFLICT (category, language) DO UPDATE SET name = EXCLUDED.name, updated_at = CURRENT_TIMESTAMP;
INSERT INTO rule_category (category, category_key, sort_order) VALUES ('쓰레기', 'waste', 20)
ON CONFLICT (category) DO UPDATE SET category_key = EXCLUDED.category_key, sort_order = EXCLUDED.sort_order;
INSERT INTO rule_category_translation (category, language, name) VALUES ('쓰레기', 'en', 'Waste')
ON CONFLICT (category, language) DO UPDATE SET name = EXCLUDED.name, updated_at = CURRENT_TIMESTAMP;
INSERT INTO rule_category_translation (category, language, name) VALUES ('쓰레기', 'zh', '垃圾')
ON CONFLICT (category, language) DO UPDATE SET name = EXCLUDED.name, updated_at = CURRENT_TIMESTAMP;
INSERT INTO rule_category_translation (category, language, name) VALUES ('쓰레기', 'vi', 'Rác thải')
ON CONFLICT (category, language) DO UPDATE SET name = EXCLUDED.name, updated_at = CURRENT_TIMESTAMP;
INSERT INTO rule_category (category, category_key, sort_order) VALUES ('교통', 'traffic', 30)
ON CONFLICT (category) DO UPDATE SET category_key = EXCLUDED.category_key, sort_order = EXCLUDED.sort_order;
INSERT INTO rule_category_translation (category, language, name) VALUES ('교통', 'en', 'Traffic')
ON CONFLICT (category, language) DO UPDATE SET name = EXCLUDED.name, updated_at = CURRENT_TIMESTAMP;
INSERT INTO rule_category_translation (category, language, name) VALUES ('교통', 'zh', '交通')
ON CONFLICT (category, language) DO UPDATE SET name = EXCLUDED.name, updated_at = CURRENT_TIMESTAMP;
INSERT INTO rule_category_translation (category, language, name) VALUES ('교통', 'vi', 'Giao thông')
ON CONFLICT (category, language) DO UPDATE SET name = EXCLUDED.name, updated_at = CURRENT_TIMESTAMP;
INSERT INTO rule_category (category, category_key, sort_order) VALUES ('세금', 'tax', 40)
ON CONFLICT (category) DO UPDATE SET category_key = EXCLUDED.category_key, sort_order = EXCLUDED.sort_order;
INSERT INTO rule_category_translation (category, language, name) VALUES ('세금', 'en', 'Taxes')
ON CONFLICT (category, language) DO UPDATE SET name = EXCLUDED.name, updated_at = CURRENT_TIMESTAMP;
INSERT INTO rule_category_translation (category, language, name) VALUES ('세금', 'zh', '税金')
ON CONFLICT (category, language) DO UPDATE SET name = EXCLUDED.name, updated_at = CURRENT_TIMESTAMP;
INSERT INTO rule_category_translation (category, language, name) VALUES ('세금', 'vi', 'Thuế')
ON CONFLICT (category, language) DO UPDATE SET name = EXCLUDED.name, updated_at = CURRENT_TIMESTAMP;
INSERT INTO rule_category (category, category_key, sort_order) VALUES ('생활 편의', 'living', 50)
ON CONFLICT (category) DO UPDATE SET category_key = EXCLUDED.category_key, sort_order = EXCLUDED.sort_order;
INSERT INTO rule_category_translation (category, language, name) VALUES ('생활 편의', 'en', 'Everyday life')
ON CONFLICT (category, language) DO UPDATE SET name = EXCLUDED.name, updated_at = CURRENT_TIMESTAMP;
INSERT INTO rule_category_translation (category, language, name) VALUES ('생활 편의', 'zh', '生活便利')
ON CONFLICT (category, language) DO UPDATE SET name = EXCLUDED.name, updated_at = CURRENT_TIMESTAMP;
INSERT INTO rule_category_translation (category, language, name) VALUES ('생활 편의', 'vi', 'Tiện ích')
ON CONFLICT (category, language) DO UPDATE SET name = EXCLUDED.name, updated_at = CURRENT_TIMESTAMP;
INSERT INTO rule_category (category, category_key, sort_order) VALUES ('교육·상담', 'support', 60)
ON CONFLICT (category) DO UPDATE SET category_key = EXCLUDED.category_key, sort_order = EXCLUDED.sort_order;
INSERT INTO rule_category_translation (category, language, name) VALUES ('교육·상담', 'en', 'Help & learning')
ON CONFLICT (category, language) DO UPDATE SET name = EXCLUDED.name, updated_at = CURRENT_TIMESTAMP;
INSERT INTO rule_category_translation (category, language, name) VALUES ('교육·상담', 'zh', '教育·咨询')
ON CONFLICT (category, language) DO UPDATE SET name = EXCLUDED.name, updated_at = CURRENT_TIMESTAMP;
INSERT INTO rule_category_translation (category, language, name) VALUES ('교육·상담', 'vi', 'Học tập·Tư vấn')
ON CONFLICT (category, language) DO UPDATE SET name = EXCLUDED.name, updated_at = CURRENT_TIMESTAMP;

-- order-noise
INSERT INTO guide (code, kind, item_type, category, sort_order, is_faq, source_url, checked_on)
VALUES ('order-noise', 'RULE', 'INFO', '기초 질서', 101, TRUE, 'https://floor.noiseinfo.or.kr/floornoise/home/guide/process.do', '2026-11-25')
ON CONFLICT (code) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'ko', guide_id, '층간소음', '공동주택에서 소음 갈등이 생기면 관리사무소에 먼저 알리고, 해결되지 않으면 층간소음 이웃사이센터에 상담을 신청해요.', '법령상 낮은 06:00~22:00, 밤은 22:00~06:00이며 밤에는 소음 기준이 더 엄격해요.
뛰는 소리 같은 직접충격 소음과 TV·음향기기 소리 같은 공기전달 소음이 기준에 들어가요.
이 기준은 처벌 기준이 아니라 다툼을 화해·조정할 때 쓰는 기준이에요.
센터는 전화 상담 → 방문 상담 → 소음 측정 순서로 도와줘요. 아파트·연립·다세대가 대상이에요.', ARRAY['법령상 낮은 06:00~22:00, 밤은 22:00~06:00이며 밤에는 소음 기준이 더 엄격해요.', '뛰는 소리 같은 직접충격 소음과 TV·음향기기 소리 같은 공기전달 소음이 기준에 들어가요.', '이 기준은 처벌 기준이 아니라 다툼을 화해·조정할 때 쓰는 기준이에요.', '센터는 전화 상담 → 방문 상담 → 소음 측정 순서로 도와줘요. 아파트·연립·다세대가 대상이에요.']::text[],
       NULL, NULL, NULL, '공동주택 층간소음의 범위와 기준에 관한 규칙', NULL, '층간소음 이웃사이센터 1661-2642 (평일 09:00~18:00)', '층간소음 이웃사이센터 업무절차', TRUE
FROM guide WHERE code = 'order-noise' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'en', guide_id, 'Noise between floors', 'If noise causes trouble in an apartment, tell the management office first. If it continues, ask the Floor Noise Neighbor Center for counseling.', 'By law, daytime is 06:00–22:00 and night is 22:00–06:00. The noise limits are stricter at night.
The limits cover impact noise such as running and airborne noise such as TV or speakers.
These limits are not for punishment. They are used to settle disputes.
The center helps in this order: phone counseling, a visit, then noise measurement. It covers apartments, row houses and multi-household houses.', ARRAY['By law, daytime is 06:00–22:00 and night is 22:00–06:00. The noise limits are stricter at night.', 'The limits cover impact noise such as running and airborne noise such as TV or speakers.', 'These limits are not for punishment. They are used to settle disputes.', 'The center helps in this order: phone counseling, a visit, then noise measurement. It covers apartments, row houses and multi-household houses.']::text[],
       NULL, NULL, NULL, 'Rules on the Scope and Standards of Floor Noise in Multi-unit Housing', NULL, 'Floor Noise Neighbor Center 1661-2642 (weekdays 09:00–18:00)', '층간소음 이웃사이센터 업무절차', TRUE
FROM guide WHERE code = 'order-noise' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'zh', guide_id, '楼层噪音', '公寓发生噪音纠纷时，请先告知管理处；仍未解决可向楼层噪音邻里中心申请咨询。', '法规上白天为06:00~22:00，夜间为22:00~06:00，夜间噪音标准更严格。
标准包括跑跳等直接撞击噪音，以及电视、音响等空气传播噪音。
该标准不是处罚标准，而是用于调解纠纷的标准。
中心按电话咨询→上门咨询→噪音测量的顺序提供帮助，适用于公寓、联排住宅和多户住宅。', ARRAY['法规上白天为06:00~22:00，夜间为22:00~06:00，夜间噪音标准更严格。', '标准包括跑跳等直接撞击噪音，以及电视、音响等空气传播噪音。', '该标准不是处罚标准，而是用于调解纠纷的标准。', '中心按电话咨询→上门咨询→噪音测量的顺序提供帮助，适用于公寓、联排住宅和多户住宅。']::text[],
       NULL, NULL, NULL, '《共同住宅楼层噪音范围与标准规则》', NULL, '楼层噪音邻里中心 1661-2642（工作日 09:00~18:00）', '층간소음 이웃사이센터 업무절차', TRUE
FROM guide WHERE code = 'order-noise' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'vi', guide_id, 'Tiếng ồn giữa các tầng', 'Khi có mâu thuẫn về tiếng ồn ở nhà chung cư, hãy báo ban quản lý trước. Nếu chưa giải quyết được, hãy đăng ký tư vấn tại Trung tâm láng giềng về tiếng ồn giữa các tầng.', 'Theo luật, ban ngày là 06:00–22:00 và ban đêm là 22:00–06:00. Ban đêm áp dụng tiêu chuẩn tiếng ồn nghiêm ngặt hơn.
Tiêu chuẩn gồm tiếng ồn va chạm như chạy nhảy và tiếng ồn truyền qua không khí như TV, loa.
Tiêu chuẩn này không dùng để xử phạt mà dùng để hòa giải tranh chấp.
Trung tâm hỗ trợ theo thứ tự: tư vấn qua điện thoại → tư vấn tại nhà → đo tiếng ồn. Áp dụng cho chung cư, nhà liên kế và nhà nhiều hộ.', ARRAY['Theo luật, ban ngày là 06:00–22:00 và ban đêm là 22:00–06:00. Ban đêm áp dụng tiêu chuẩn tiếng ồn nghiêm ngặt hơn.', 'Tiêu chuẩn gồm tiếng ồn va chạm như chạy nhảy và tiếng ồn truyền qua không khí như TV, loa.', 'Tiêu chuẩn này không dùng để xử phạt mà dùng để hòa giải tranh chấp.', 'Trung tâm hỗ trợ theo thứ tự: tư vấn qua điện thoại → tư vấn tại nhà → đo tiếng ồn. Áp dụng cho chung cư, nhà liên kế và nhà nhiều hộ.']::text[],
       NULL, NULL, NULL, 'Quy tắc về phạm vi và tiêu chuẩn tiếng ồn giữa các tầng trong nhà chung cư', NULL, 'Trung tâm láng giềng về tiếng ồn 1661-2642 (ngày thường 09:00–18:00)', '층간소음 이웃사이센터 업무절차', TRUE
FROM guide WHERE code = 'order-noise' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 1, '층간소음 이웃사이센터 업무절차', 'https://floor.noiseinfo.or.kr/floornoise/home/guide/process.do' FROM guide WHERE code = 'order-noise'
ON CONFLICT (guide_id, sort_order) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 2, '공동주택 층간소음의 범위와 기준에 관한 규칙 별표 (법제처)', 'https://law.go.kr/flDownload.do?flSeq=123539983' FROM guide WHERE code = 'order-noise'
ON CONFLICT (guide_id, sort_order) DO NOTHING;

-- order-smoking
INSERT INTO guide (code, kind, item_type, category, sort_order, is_faq, source_url, checked_on)
VALUES ('order-smoking', 'RULE', 'INFO', '기초 질서', 102, FALSE, 'https://mohw.go.kr/board.es?act=view&bid=0027&list_no=1482759&mid=a10503000000', '2026-11-25')
ON CONFLICT (code) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'ko', guide_id, '금연구역', '금연구역에서 담배를 피우면 과태료를 내요. 어린이집·유치원·학교 주변 30m도 금연구역이에요.', '2024년 8월 17일부터 어린이집·유치원·초중고 경계 30m 이내가 금연구역이에요.
공중이용시설 같은 금연구역에서 피우면 과태료 10만 원이에요.', ARRAY['2024년 8월 17일부터 어린이집·유치원·초중고 경계 30m 이내가 금연구역이에요.', '공중이용시설 같은 금연구역에서 피우면 과태료 10만 원이에요.']::text[],
       NULL, NULL, NULL, '국민건강증진법', '과태료 10만 원 (학교 주변 30m: 10만 원 이하)', NULL, '보건복지부 보도자료 — 어린이집·학교 주변에서는 금연', TRUE
FROM guide WHERE code = 'order-smoking' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'en', guide_id, 'No-smoking areas', 'Smoking in a no-smoking area means a fine. The area within 30 m of daycare centers, kindergartens and schools is also no-smoking.', 'Since 17 August 2024, the area within 30 m of daycare centers, kindergartens and schools is a no-smoking area.
Smoking in a no-smoking area such as a public facility can bring a fine of 100,000 won.', ARRAY['Since 17 August 2024, the area within 30 m of daycare centers, kindergartens and schools is a no-smoking area.', 'Smoking in a no-smoking area such as a public facility can bring a fine of 100,000 won.']::text[],
       NULL, NULL, NULL, 'National Health Promotion Act', 'Fine of 100,000 won (near schools: up to 100,000 won)', NULL, '보건복지부 보도자료 — 어린이집·학교 주변에서는 금연', TRUE
FROM guide WHERE code = 'order-smoking' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'zh', guide_id, '禁烟区', '在禁烟区吸烟会被罚款。托儿所、幼儿园、学校周边30米内也是禁烟区。', '自2024年8月17日起，托儿所、幼儿园、中小学边界30米以内为禁烟区。
在公共设施等禁烟区吸烟将被处以10万韩元罚款。', ARRAY['自2024年8月17日起，托儿所、幼儿园、中小学边界30米以内为禁烟区。', '在公共设施等禁烟区吸烟将被处以10万韩元罚款。']::text[],
       NULL, NULL, NULL, '《国民健康增进法》', '罚款10万韩元（学校周边30米：10万韩元以下）', NULL, '보건복지부 보도자료 — 어린이집·학교 주변에서는 금연', TRUE
FROM guide WHERE code = 'order-smoking' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'vi', guide_id, 'Khu vực cấm hút thuốc', 'Hút thuốc trong khu vực cấm sẽ bị phạt tiền. Khu vực trong vòng 30 m quanh nhà trẻ, mẫu giáo và trường học cũng cấm hút thuốc.', 'Từ ngày 17/8/2024, khu vực trong vòng 30 m quanh nhà trẻ, mẫu giáo và trường phổ thông là khu vực cấm hút thuốc.
Hút thuốc ở khu vực cấm như nơi công cộng có thể bị phạt 100.000 won.', ARRAY['Từ ngày 17/8/2024, khu vực trong vòng 30 m quanh nhà trẻ, mẫu giáo và trường phổ thông là khu vực cấm hút thuốc.', 'Hút thuốc ở khu vực cấm như nơi công cộng có thể bị phạt 100.000 won.']::text[],
       NULL, NULL, NULL, 'Luật Nâng cao Sức khỏe Quốc dân', 'Phạt 100.000 won (gần trường học: tối đa 100.000 won)', NULL, '보건복지부 보도자료 — 어린이집·학교 주변에서는 금연', TRUE
FROM guide WHERE code = 'order-smoking' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 1, '보건복지부 보도자료 — 어린이집·학교 주변에서는 금연', 'https://mohw.go.kr/board.es?act=view&bid=0027&list_no=1482759&mid=a10503000000' FROM guide WHERE code = 'order-smoking'
ON CONFLICT (guide_id, sort_order) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 2, '정책브리핑 — 금연구역 과태료 10만 원', 'https://www.korea.kr/news/policyNewsView.do?newsId=148859253' FROM guide WHERE code = 'order-smoking'
ON CONFLICT (guide_id, sort_order) DO NOTHING;

-- waste-general
INSERT INTO guide (code, kind, item_type, category, sort_order, is_faq, source_url, checked_on)
VALUES ('waste-general', 'RULE', 'INFO', '쓰레기', 201, FALSE, 'https://www.siheung.go.kr/portal/contents.do?mId=0703010100', '2026-11-25')
ON CONFLICT (code) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'ko', guide_id, '생활 쓰레기 (종량제 봉투)', '일반 쓰레기는 흰색, 소각용 쓰레기는 분홍색 종량제 봉투에 담아 일~금요일 저녁 6시부터 다음 날 새벽 6시 사이에 집 앞에 내놓아요.', '흰색 봉투: 휴지, 영수증, 동물 뼈, 달걀 껍데기, 조개껍데기 등.
분홍색 봉투: 고무, 가죽, 이불, 베개, 이물질이 묻은 비닐·스티로폼 등.
깨진 유리는 조금이면 신문지에 싸서 흰색 봉투에, 많으면 불연성 마대에 담아요.
음식물이나 재활용품을 섞으면 수거하지 않아요. 봉투는 마트·편의점 등 판매소에서 사요.
일요일에는 수거하지 않아요.', ARRAY['흰색 봉투: 휴지, 영수증, 동물 뼈, 달걀 껍데기, 조개껍데기 등.', '분홍색 봉투: 고무, 가죽, 이불, 베개, 이물질이 묻은 비닐·스티로폼 등.', '깨진 유리는 조금이면 신문지에 싸서 흰색 봉투에, 많으면 불연성 마대에 담아요.', '음식물이나 재활용품을 섞으면 수거하지 않아요. 봉투는 마트·편의점 등 판매소에서 사요.', '일요일에는 수거하지 않아요.']::text[],
       '내 집 앞, 내 상가 앞', '일~금요일 18:00~다음 날 06:00', NULL, '폐기물관리법 제8조·제15조·제68조, 시흥시 폐기물관리 조례', '무단투기 100만 원 이하 과태료 (예: 비닐봉지에 담아 버리면 20만 원, 배출 장소 위반 1차 10만 원)', '시흥시 자원순환과 031-310-2254', '시흥시 생활쓰레기 배출 요령', TRUE
FROM guide WHERE code = 'waste-general' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'en', guide_id, 'Household waste (volume-rate bags)', 'Put general waste in a white bag and burnable waste in a pink bag. Leave them in front of your home between 6 p.m. and 6 a.m., Sunday to Friday.', 'White bag: tissues, receipts, animal bones, eggshells, shells and similar.
Pink bag: rubber, leather, blankets, pillows, and dirty vinyl or styrofoam.
Wrap a little broken glass in newspaper and put it in the white bag. Put a lot of it in a non-combustible sack.
Bags mixed with food waste or recyclables are not collected. Buy bags at shops such as supermarkets and convenience stores.
There is no collection on Sunday.', ARRAY['White bag: tissues, receipts, animal bones, eggshells, shells and similar.', 'Pink bag: rubber, leather, blankets, pillows, and dirty vinyl or styrofoam.', 'Wrap a little broken glass in newspaper and put it in the white bag. Put a lot of it in a non-combustible sack.', 'Bags mixed with food waste or recyclables are not collected. Buy bags at shops such as supermarkets and convenience stores.', 'There is no collection on Sunday.']::text[],
       'In front of your own home or shop', 'Sunday–Friday, 18:00–06:00', NULL, 'Wastes Control Act Articles 8, 15 and 68; Siheung waste ordinance', 'Illegal dumping: fine up to 1,000,000 won (e.g. 200,000 won for dumping in a plastic bag; 100,000 won for the wrong place the first time)', 'Siheung Resource Circulation Division 031-310-2254', '시흥시 생활쓰레기 배출 요령', TRUE
FROM guide WHERE code = 'waste-general' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'zh', guide_id, '生活垃圾（计量收费袋）', '一般垃圾装白色计量袋，焚烧类垃圾装粉色计量袋，于周日至周五晚6点至次日早6点放在自家门前。', '白色袋：纸巾、收据、动物骨头、蛋壳、贝壳等。
粉色袋：橡胶、皮革、被子、枕头、沾有异物的塑料和泡沫等。
少量碎玻璃用报纸包好放入白色袋，大量时装入不燃物麻袋。
混有厨余垃圾或可回收物的袋子不予收运。计量袋可在超市、便利店等销售点购买。
周日不收运。', ARRAY['白色袋：纸巾、收据、动物骨头、蛋壳、贝壳等。', '粉色袋：橡胶、皮革、被子、枕头、沾有异物的塑料和泡沫等。', '少量碎玻璃用报纸包好放入白色袋，大量时装入不燃物麻袋。', '混有厨余垃圾或可回收物的袋子不予收运。计量袋可在超市、便利店等销售点购买。', '周日不收运。']::text[],
       '自家门前、自家店铺门前', '周日至周五 18:00~次日06:00', NULL, '《废弃物管理法》第8条、第15条、第68条，始兴市废弃物管理条例', '乱扔垃圾最高罚款100万韩元（如装在塑料袋丢弃罚20万韩元，首次违反投放地点罚10万韩元）', '始兴市资源循环科 031-310-2254', '시흥시 생활쓰레기 배출 요령', TRUE
FROM guide WHERE code = 'waste-general' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'vi', guide_id, 'Rác sinh hoạt (túi tính phí theo lượng)', 'Rác thông thường cho vào túi trắng, rác đốt được cho vào túi hồng. Để trước nhà từ 18 giờ đến 6 giờ sáng hôm sau, từ Chủ nhật đến thứ Sáu.', 'Túi trắng: khăn giấy, hóa đơn, xương động vật, vỏ trứng, vỏ sò…
Túi hồng: cao su, da, chăn, gối, nilon hoặc xốp bị bẩn…
Ít mảnh kính vỡ thì gói bằng giấy báo rồi bỏ vào túi trắng; nhiều thì cho vào bao đựng rác không cháy.
Túi có lẫn rác thực phẩm hoặc đồ tái chế sẽ không được thu gom. Mua túi ở siêu thị, cửa hàng tiện lợi.
Không thu gom vào Chủ nhật.', ARRAY['Túi trắng: khăn giấy, hóa đơn, xương động vật, vỏ trứng, vỏ sò…', 'Túi hồng: cao su, da, chăn, gối, nilon hoặc xốp bị bẩn…', 'Ít mảnh kính vỡ thì gói bằng giấy báo rồi bỏ vào túi trắng; nhiều thì cho vào bao đựng rác không cháy.', 'Túi có lẫn rác thực phẩm hoặc đồ tái chế sẽ không được thu gom. Mua túi ở siêu thị, cửa hàng tiện lợi.', 'Không thu gom vào Chủ nhật.']::text[],
       'Trước nhà hoặc cửa hàng của mình', 'Chủ nhật–thứ Sáu, 18:00–06:00', NULL, 'Luật Quản lý Chất thải Điều 8, 15, 68; quy định về chất thải của Siheung', 'Vứt rác trái phép: phạt đến 1.000.000 won (ví dụ: bỏ rác trong túi nilon 200.000 won; sai nơi để lần đầu 100.000 won)', 'Phòng Tuần hoàn Tài nguyên Siheung 031-310-2254', '시흥시 생활쓰레기 배출 요령', TRUE
FROM guide WHERE code = 'waste-general' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 1, '시흥시 생활쓰레기 배출 요령', 'https://www.siheung.go.kr/portal/contents.do?mId=0703010100' FROM guide WHERE code = 'waste-general'
ON CONFLICT (guide_id, sort_order) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 2, '시흥시 무단투기 과태료 부과기준', 'https://www.siheung.go.kr/portal/contents.do?mId=0703011000' FROM guide WHERE code = 'waste-general'
ON CONFLICT (guide_id, sort_order) DO NOTHING;

-- waste-food
INSERT INTO guide (code, kind, item_type, category, sort_order, is_faq, source_url, checked_on)
VALUES ('waste-food', 'RULE', 'INFO', '쓰레기', 202, TRUE, 'https://www.siheung.go.kr/portal/contents.do?mId=0703010200', '2026-11-25')
ON CONFLICT (code) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'ko', guide_id, '음식물 쓰레기', '노란색 음식물 종량제 봉투, 칩을 꽂은 전용 용기, 또는 RFID 기기로 일~금요일에 내요. 물기를 빼고 이물질은 빼요.', '비닐, 병뚜껑, 이쑤시개, 호일, 빨대는 빼고, 통무·통배추처럼 큰 것은 잘게 썰어요.
일반 쓰레기로 버리는 것: 굵은 뼈·털, 조개·게 껍데기, 생선뼈, 달걀 껍데기, 호두·밤 껍데기, 복숭아·감 씨, 파 뿌리, 옥수수 껍질·대, 한약 찌꺼기, 티백.
복어 내장은 분홍색 소각용 봉투에 넣어요.', ARRAY['비닐, 병뚜껑, 이쑤시개, 호일, 빨대는 빼고, 통무·통배추처럼 큰 것은 잘게 썰어요.', '일반 쓰레기로 버리는 것: 굵은 뼈·털, 조개·게 껍데기, 생선뼈, 달걀 껍데기, 호두·밤 껍데기, 복숭아·감 씨, 파 뿌리, 옥수수 껍질·대, 한약 찌꺼기, 티백.', '복어 내장은 분홍색 소각용 봉투에 넣어요.']::text[],
       NULL, '일~금요일', NULL, NULL, NULL, '시흥시 자원순환과 031-310-2362', '시흥시 음식물쓰레기', TRUE
FROM guide WHERE code = 'waste-food' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'en', guide_id, 'Food waste', 'Use a yellow food waste bag, a bin with a chip, or an RFID machine, Sunday to Friday. Drain the water and take out anything that is not food.', 'Remove vinyl, bottle caps, toothpicks, foil and straws. Cut large items like whole radishes or cabbages into small pieces.
These go in general waste: large bones and hair, shells of clams or crabs, fish bones, eggshells, walnut or chestnut shells, peach or persimmon seeds, green onion roots, corn husks and cobs, herbal medicine dregs, tea bags.
Put pufferfish guts in the pink bag for burnable waste.', ARRAY['Remove vinyl, bottle caps, toothpicks, foil and straws. Cut large items like whole radishes or cabbages into small pieces.', 'These go in general waste: large bones and hair, shells of clams or crabs, fish bones, eggshells, walnut or chestnut shells, peach or persimmon seeds, green onion roots, corn husks and cobs, herbal medicine dregs, tea bags.', 'Put pufferfish guts in the pink bag for burnable waste.']::text[],
       NULL, 'Sunday–Friday', NULL, NULL, NULL, 'Siheung Resource Circulation Division 031-310-2362', '시흥시 음식물쓰레기', TRUE
FROM guide WHERE code = 'waste-food' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'zh', guide_id, '厨余垃圾', '用黄色厨余计量袋、插卡专用桶或RFID设备，于周日至周五投放。请沥干水分并去除杂物。', '去除塑料、瓶盖、牙签、锡纸、吸管；整个萝卜、白菜等大块要切碎。
以下按一般垃圾处理：粗骨头和毛、贝壳和蟹壳、鱼骨、蛋壳、核桃和栗子壳、桃核和柿子籽、葱根、玉米皮和玉米芯、中药渣、茶包。
河豚内脏放入粉色焚烧类垃圾袋。', ARRAY['去除塑料、瓶盖、牙签、锡纸、吸管；整个萝卜、白菜等大块要切碎。', '以下按一般垃圾处理：粗骨头和毛、贝壳和蟹壳、鱼骨、蛋壳、核桃和栗子壳、桃核和柿子籽、葱根、玉米皮和玉米芯、中药渣、茶包。', '河豚内脏放入粉色焚烧类垃圾袋。']::text[],
       NULL, '周日至周五', NULL, NULL, NULL, '始兴市资源循环科 031-310-2362', '시흥시 음식물쓰레기', TRUE
FROM guide WHERE code = 'waste-food' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'vi', guide_id, 'Rác thực phẩm', 'Dùng túi rác thực phẩm màu vàng, thùng có gắn chip hoặc máy RFID, từ Chủ nhật đến thứ Sáu. Hãy để ráo nước và bỏ vật lạ ra.', 'Bỏ nilon, nắp chai, tăm, giấy bạc, ống hút. Cắt nhỏ đồ to như củ cải, bắp cải nguyên.
Những thứ sau bỏ vào rác thông thường: xương to và lông, vỏ sò, vỏ cua, xương cá, vỏ trứng, vỏ óc chó, vỏ hạt dẻ, hạt đào, hạt hồng, rễ hành, vỏ và lõi ngô, bã thuốc Bắc, túi trà.
Nội tạng cá nóc cho vào túi hồng dành cho rác đốt được.', ARRAY['Bỏ nilon, nắp chai, tăm, giấy bạc, ống hút. Cắt nhỏ đồ to như củ cải, bắp cải nguyên.', 'Những thứ sau bỏ vào rác thông thường: xương to và lông, vỏ sò, vỏ cua, xương cá, vỏ trứng, vỏ óc chó, vỏ hạt dẻ, hạt đào, hạt hồng, rễ hành, vỏ và lõi ngô, bã thuốc Bắc, túi trà.', 'Nội tạng cá nóc cho vào túi hồng dành cho rác đốt được.']::text[],
       NULL, 'Chủ nhật–thứ Sáu', NULL, NULL, NULL, 'Phòng Tuần hoàn Tài nguyên Siheung 031-310-2362', '시흥시 음식물쓰레기', TRUE
FROM guide WHERE code = 'waste-food' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 1, '시흥시 음식물쓰레기', 'https://www.siheung.go.kr/portal/contents.do?mId=0703010200' FROM guide WHERE code = 'waste-food'
ON CONFLICT (guide_id, sort_order) DO NOTHING;

-- waste-recycle
INSERT INTO guide (code, kind, item_type, category, sort_order, is_faq, source_url, checked_on)
VALUES ('waste-recycle', 'RULE', 'INFO', '쓰레기', 203, FALSE, 'https://www.siheung.go.kr/portal/contents.do?mId=0703010300', '2026-11-25')
ON CONFLICT (code) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'ko', guide_id, '분리수거 (페트병·캔·비닐)', '내용물을 비우고 헹군 뒤, 라벨·뚜껑·테이프 같은 다른 재질은 떼고 종류별로 나눠서 내요.', '투명 페트병은 라벨을 떼고 눌러서 내요. 단독주택은 목·금요일에 내요.
플라스틱, 캔, 유리병, 비닐은 씻어서 일~금요일에 내요. 아파트는 단지의 전용 수거함에 내요.
종이팩은 펼쳐서 헹구고 말린 뒤 일반 종이와 따로 내요. 깨진 유리·도자기는 재활용품이 아니에요.
폐건전지와 형광등은 주요 거점에 있는 전용 수거함에 넣어요.', ARRAY['투명 페트병은 라벨을 떼고 눌러서 내요. 단독주택은 목·금요일에 내요.', '플라스틱, 캔, 유리병, 비닐은 씻어서 일~금요일에 내요. 아파트는 단지의 전용 수거함에 내요.', '종이팩은 펼쳐서 헹구고 말린 뒤 일반 종이와 따로 내요. 깨진 유리·도자기는 재활용품이 아니에요.', '폐건전지와 형광등은 주요 거점에 있는 전용 수거함에 넣어요.']::text[],
       NULL, NULL, NULL, NULL, NULL, '시흥시 자원순환과 031-310-2363', '시흥시 재활용품', TRUE
FROM guide WHERE code = 'waste-recycle' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'en', guide_id, 'Recycling (PET bottles, cans, vinyl)', 'Empty and rinse items, remove labels, caps and tape made of other materials, and sort them by type.', 'Remove the label from clear PET bottles and crush them. Detached houses put them out on Thursday and Friday.
Wash plastic, cans, glass bottles and vinyl and put them out Sunday to Friday. Apartments use the complex’s own bins.
Open, rinse and dry paper cartons and keep them apart from other paper. Broken glass and ceramics are not recyclable.
Put used batteries and fluorescent lamps in the special bins at main collection points.', ARRAY['Remove the label from clear PET bottles and crush them. Detached houses put them out on Thursday and Friday.', 'Wash plastic, cans, glass bottles and vinyl and put them out Sunday to Friday. Apartments use the complex’s own bins.', 'Open, rinse and dry paper cartons and keep them apart from other paper. Broken glass and ceramics are not recyclable.', 'Put used batteries and fluorescent lamps in the special bins at main collection points.']::text[],
       NULL, NULL, NULL, NULL, NULL, 'Siheung Resource Circulation Division 031-310-2363', '시흥시 재활용품', TRUE
FROM guide WHERE code = 'waste-recycle' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'zh', guide_id, '分类回收（塑料瓶·易拉罐·塑料袋）', '倒空并冲洗后，撕掉标签、瓶盖、胶带等其他材质，按种类分开投放。', '透明塑料瓶撕掉标签后压扁投放。独栋住宅在周四、周五投放。
塑料、易拉罐、玻璃瓶、塑料袋洗净后于周日至周五投放。公寓投放到小区专用回收箱。
纸盒展开、冲洗、晾干后与普通纸张分开投放。碎玻璃和陶瓷不属于可回收物。
废电池和荧光灯放入主要地点的专用回收箱。', ARRAY['透明塑料瓶撕掉标签后压扁投放。独栋住宅在周四、周五投放。', '塑料、易拉罐、玻璃瓶、塑料袋洗净后于周日至周五投放。公寓投放到小区专用回收箱。', '纸盒展开、冲洗、晾干后与普通纸张分开投放。碎玻璃和陶瓷不属于可回收物。', '废电池和荧光灯放入主要地点的专用回收箱。']::text[],
       NULL, NULL, NULL, NULL, NULL, '始兴市资源循环科 031-310-2363', '시흥시 재활용품', TRUE
FROM guide WHERE code = 'waste-recycle' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'vi', guide_id, 'Phân loại tái chế (chai nhựa, lon, nilon)', 'Đổ hết, tráng sạch, bóc nhãn, nắp, băng dính khác chất liệu rồi phân loại theo từng loại.', 'Chai nhựa trong suốt bóc nhãn rồi ép dẹt. Nhà riêng bỏ vào thứ Năm và thứ Sáu.
Nhựa, lon, chai thủy tinh, nilon rửa sạch rồi bỏ từ Chủ nhật đến thứ Sáu. Chung cư bỏ vào thùng thu gom riêng của khu.
Hộp giấy mở ra, tráng, phơi khô rồi để riêng với giấy thường. Kính vỡ và đồ gốm không phải đồ tái chế.
Pin cũ và bóng đèn huỳnh quang bỏ vào thùng thu gom riêng ở các điểm chính.', ARRAY['Chai nhựa trong suốt bóc nhãn rồi ép dẹt. Nhà riêng bỏ vào thứ Năm và thứ Sáu.', 'Nhựa, lon, chai thủy tinh, nilon rửa sạch rồi bỏ từ Chủ nhật đến thứ Sáu. Chung cư bỏ vào thùng thu gom riêng của khu.', 'Hộp giấy mở ra, tráng, phơi khô rồi để riêng với giấy thường. Kính vỡ và đồ gốm không phải đồ tái chế.', 'Pin cũ và bóng đèn huỳnh quang bỏ vào thùng thu gom riêng ở các điểm chính.']::text[],
       NULL, NULL, NULL, NULL, NULL, 'Phòng Tuần hoàn Tài nguyên Siheung 031-310-2363', '시흥시 재활용품', TRUE
FROM guide WHERE code = 'waste-recycle' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 1, '시흥시 재활용품', 'https://www.siheung.go.kr/portal/contents.do?mId=0703010300' FROM guide WHERE code = 'waste-recycle'
ON CONFLICT (guide_id, sort_order) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 2, '시흥시 생활쓰레기 배출 요령', 'https://www.siheung.go.kr/portal/contents.do?mId=0703010100' FROM guide WHERE code = 'waste-recycle'
ON CONFLICT (guide_id, sort_order) DO NOTHING;

-- waste-bulky
INSERT INTO guide (code, kind, item_type, category, sort_order, is_faq, source_url, checked_on)
VALUES ('waste-bulky', 'RULE', 'INFO', '쓰레기', 204, TRUE, 'https://www.siheung.go.kr/portal/contents.do?mId=0712020000', '2026-11-25')
ON CONFLICT (code) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'ko', guide_id, '대형 폐기물 (가구·매트리스·가전)', '스마트클린 시흥에서 신청하고 수수료를 내거나, 판매소에서 납부필증 스티커를 사서 붙여 내요.', '스마트클린 시흥(smartclean.siheung.go.kr)은 앱 설치 없이 본인 인증 후 PC·휴대폰으로 신청해요.
신청 후 접수번호·품목·결제 금액을 종이에 적어 물건에 붙여요.
수수료는 품목별 수수료표를 따라요. 표에 없는 품목은 비슷한 품목 기준이에요.
냉장고·세탁기·에어컨·TV 같은 폐가전은 무상 방문수거를 신청할 수 있어요 (1599-0903, www.15990903.or.kr).', ARRAY['스마트클린 시흥(smartclean.siheung.go.kr)은 앱 설치 없이 본인 인증 후 PC·휴대폰으로 신청해요.', '신청 후 접수번호·품목·결제 금액을 종이에 적어 물건에 붙여요.', '수수료는 품목별 수수료표를 따라요. 표에 없는 품목은 비슷한 품목 기준이에요.', '냉장고·세탁기·에어컨·TV 같은 폐가전은 무상 방문수거를 신청할 수 있어요 (1599-0903, www.15990903.or.kr).']::text[],
       NULL, NULL, NULL, NULL, NULL, '시흥시 자원순환과 031-310-2254 (평일 09:30~18:00)', '시흥시 대형폐기물 신청안내', TRUE
FROM guide WHERE code = 'waste-bulky' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'en', guide_id, 'Large items (furniture, mattresses, appliances)', 'Apply and pay on Smart Clean Siheung, or buy a fee sticker at a shop and attach it to the item.', 'Smart Clean Siheung (smartclean.siheung.go.kr) works on a PC or phone after identity verification. No app is needed.
After applying, write the receipt number, item and amount paid on paper and attach it to the item.
The fee follows the fee table by item. Items not on the table use the fee of a similar item.
Old appliances such as refrigerators, washing machines, air conditioners and TVs can be picked up for free (1599-0903, www.15990903.or.kr).', ARRAY['Smart Clean Siheung (smartclean.siheung.go.kr) works on a PC or phone after identity verification. No app is needed.', 'After applying, write the receipt number, item and amount paid on paper and attach it to the item.', 'The fee follows the fee table by item. Items not on the table use the fee of a similar item.', 'Old appliances such as refrigerators, washing machines, air conditioners and TVs can be picked up for free (1599-0903, www.15990903.or.kr).']::text[],
       NULL, NULL, NULL, NULL, NULL, 'Siheung Resource Circulation Division 031-310-2254 (weekdays 09:30–18:00)', '시흥시 대형폐기물 신청안내', TRUE
FROM guide WHERE code = 'waste-bulky' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'zh', guide_id, '大件垃圾（家具·床垫·家电）', '在“智能清洁始兴”网站申请并缴费，或在销售点购买缴费贴纸贴在物品上。', '“智能清洁始兴”(smartclean.siheung.go.kr)无需安装应用，身份认证后可用电脑或手机申请。
申请后将受理编号、物品、缴费金额写在纸上贴到物品上。
费用按物品收费表计算，表中没有的物品按类似物品收费。
冰箱、洗衣机、空调、电视等废旧家电可申请免费上门回收（1599-0903，www.15990903.or.kr）。', ARRAY['“智能清洁始兴”(smartclean.siheung.go.kr)无需安装应用，身份认证后可用电脑或手机申请。', '申请后将受理编号、物品、缴费金额写在纸上贴到物品上。', '费用按物品收费表计算，表中没有的物品按类似物品收费。', '冰箱、洗衣机、空调、电视等废旧家电可申请免费上门回收（1599-0903，www.15990903.or.kr）。']::text[],
       NULL, NULL, NULL, NULL, NULL, '始兴市资源循环科 031-310-2254（工作日 09:30~18:00）', '시흥시 대형폐기물 신청안내', TRUE
FROM guide WHERE code = 'waste-bulky' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'vi', guide_id, 'Rác cồng kềnh (đồ nội thất, nệm, đồ điện)', 'Đăng ký và trả phí trên Smart Clean Siheung, hoặc mua tem nộp phí ở cửa hàng rồi dán lên đồ.', 'Smart Clean Siheung (smartclean.siheung.go.kr) dùng trên máy tính hoặc điện thoại sau khi xác minh danh tính, không cần cài ứng dụng.
Sau khi đăng ký, ghi số tiếp nhận, tên đồ và số tiền đã trả lên giấy rồi dán lên đồ.
Phí theo bảng phí từng loại đồ; đồ không có trong bảng tính theo đồ tương tự.
Đồ điện cũ như tủ lạnh, máy giặt, máy lạnh, TV có thể đăng ký thu gom miễn phí tại nhà (1599-0903, www.15990903.or.kr).', ARRAY['Smart Clean Siheung (smartclean.siheung.go.kr) dùng trên máy tính hoặc điện thoại sau khi xác minh danh tính, không cần cài ứng dụng.', 'Sau khi đăng ký, ghi số tiếp nhận, tên đồ và số tiền đã trả lên giấy rồi dán lên đồ.', 'Phí theo bảng phí từng loại đồ; đồ không có trong bảng tính theo đồ tương tự.', 'Đồ điện cũ như tủ lạnh, máy giặt, máy lạnh, TV có thể đăng ký thu gom miễn phí tại nhà (1599-0903, www.15990903.or.kr).']::text[],
       NULL, NULL, NULL, NULL, NULL, 'Phòng Tuần hoàn Tài nguyên Siheung 031-310-2254 (ngày thường 09:30–18:00)', '시흥시 대형폐기물 신청안내', TRUE
FROM guide WHERE code = 'waste-bulky' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 1, '시흥시 대형폐기물 신청안내', 'https://www.siheung.go.kr/portal/contents.do?mId=0712020000' FROM guide WHERE code = 'waste-bulky'
ON CONFLICT (guide_id, sort_order) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 2, '스마트클린 시흥', 'https://smartclean.siheung.go.kr/' FROM guide WHERE code = 'waste-bulky'
ON CONFLICT (guide_id, sort_order) DO NOTHING;

-- traffic-parking
INSERT INTO guide (code, kind, item_type, category, sort_order, is_faq, source_url, checked_on)
VALUES ('traffic-parking', 'RULE', 'INFO', '교통', 301, FALSE, 'https://www.korea.kr/news/policyNewsView.do?newsId=148918697', '2026-11-25')
ON CONFLICT (code) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'ko', guide_id, '주정차 금지 구역', '소화전, 교차로 모퉁이, 버스정류소, 횡단보도, 어린이보호구역, 인도에 차를 세우면 주민 신고로도 과태료가 나와요.', '소화전 5m 이내, 교차로 모퉁이 5m 이내, 버스정류소 10m 이내, 횡단보도 위, 초등학교 정문 앞 어린이보호구역, 인도가 신고 대상이에요.
1분 넘게 세워 두면 주민이 안전신문고 앱으로 1분 간격 사진 2장을 찍어 신고할 수 있어요.', ARRAY['소화전 5m 이내, 교차로 모퉁이 5m 이내, 버스정류소 10m 이내, 횡단보도 위, 초등학교 정문 앞 어린이보호구역, 인도가 신고 대상이에요.', '1분 넘게 세워 두면 주민이 안전신문고 앱으로 1분 간격 사진 2장을 찍어 신고할 수 있어요.']::text[],
       NULL, NULL, NULL, '도로교통법 제32조', '승용차 기준 4만 원(교차로·버스정류소·횡단보도·인도), 8만 원(소화전), 12만 원(어린이보호구역)', NULL, '정책브리핑 — 6대 불법 주정차 주민신고', TRUE
FROM guide WHERE code = 'traffic-parking' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'en', guide_id, 'No-parking zones', 'Parking at fire hydrants, intersection corners, bus stops, crosswalks, school zones or sidewalks can be fined, even from a resident’s report.', 'Reportable spots: within 5 m of a fire hydrant, within 5 m of an intersection corner, within 10 m of a bus stop, on a crosswalk, in the school zone at an elementary school’s main gate, and on sidewalks.
If a car stays more than 1 minute, residents can report it with two photos taken 1 minute apart in the Safety e-Report app.', ARRAY['Reportable spots: within 5 m of a fire hydrant, within 5 m of an intersection corner, within 10 m of a bus stop, on a crosswalk, in the school zone at an elementary school’s main gate, and on sidewalks.', 'If a car stays more than 1 minute, residents can report it with two photos taken 1 minute apart in the Safety e-Report app.']::text[],
       NULL, NULL, NULL, 'Road Traffic Act Article 32', 'For cars: 40,000 won (corner, bus stop, crosswalk, sidewalk), 80,000 won (fire hydrant), 120,000 won (school zone)', NULL, '정책브리핑 — 6대 불법 주정차 주민신고', TRUE
FROM guide WHERE code = 'traffic-parking' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'zh', guide_id, '禁止停车区域', '在消防栓、路口转角、公交站、人行横道、儿童保护区、人行道停车，即使是居民举报也会被罚款。', '举报对象：消防栓5米内、路口转角5米内、公交站10米内、人行横道上、小学正门前儿童保护区、人行道。
停车超过1分钟，居民可用“安全申报”应用拍摄间隔1分钟的2张照片举报。', ARRAY['举报对象：消防栓5米内、路口转角5米内、公交站10米内、人行横道上、小学正门前儿童保护区、人行道。', '停车超过1分钟，居民可用“安全申报”应用拍摄间隔1分钟的2张照片举报。']::text[],
       NULL, NULL, NULL, '《道路交通法》第32条', '小汽车：4万韩元（路口、公交站、人行横道、人行道），8万韩元（消防栓），12万韩元（儿童保护区）', NULL, '정책브리핑 — 6대 불법 주정차 주민신고', TRUE
FROM guide WHERE code = 'traffic-parking' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'vi', guide_id, 'Khu vực cấm dừng đỗ xe', 'Đỗ xe ở trụ cứu hỏa, góc ngã tư, trạm xe buýt, vạch qua đường, khu vực bảo vệ trẻ em hoặc vỉa hè có thể bị phạt, kể cả khi người dân báo cáo.', 'Có thể bị báo cáo: trong vòng 5 m trụ cứu hỏa, 5 m góc ngã tư, 10 m trạm xe buýt, trên vạch qua đường, khu bảo vệ trẻ em trước cổng trường tiểu học, và trên vỉa hè.
Nếu xe đỗ quá 1 phút, người dân có thể báo cáo bằng 2 ảnh chụp cách nhau 1 phút qua ứng dụng Safety e-Report.', ARRAY['Có thể bị báo cáo: trong vòng 5 m trụ cứu hỏa, 5 m góc ngã tư, 10 m trạm xe buýt, trên vạch qua đường, khu bảo vệ trẻ em trước cổng trường tiểu học, và trên vỉa hè.', 'Nếu xe đỗ quá 1 phút, người dân có thể báo cáo bằng 2 ảnh chụp cách nhau 1 phút qua ứng dụng Safety e-Report.']::text[],
       NULL, NULL, NULL, 'Luật Giao thông Đường bộ Điều 32', 'Xe con: 40.000 won (góc ngã tư, trạm xe buýt, vạch qua đường, vỉa hè), 80.000 won (trụ cứu hỏa), 120.000 won (khu bảo vệ trẻ em)', NULL, '정책브리핑 — 6대 불법 주정차 주민신고', TRUE
FROM guide WHERE code = 'traffic-parking' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 1, '정책브리핑 — 6대 불법 주정차 주민신고', 'https://www.korea.kr/news/policyNewsView.do?newsId=148918697' FROM guide WHERE code = 'traffic-parking'
ON CONFLICT (guide_id, sort_order) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 2, '행정안전부 보도자료 — 인도 주정차 주민신고 (2023.8.1. 시행)', 'https://m.korea.kr/briefing/pressReleaseView.do?newsId=156582031' FROM guide WHERE code = 'traffic-parking'
ON CONFLICT (guide_id, sort_order) DO NOTHING;

-- traffic-alert
INSERT INTO guide (code, kind, item_type, category, sort_order, is_faq, source_url, checked_on)
VALUES ('traffic-alert', 'RULE', 'INFO', '교통', 302, TRUE, 'https://www.siheung.go.kr/car/inst/sbas_agre_aply.gs', '2026-11-25')
ON CONFLICT (code) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'ko', guide_id, '주정차 단속 알림 (휘슬)', '시흥시는 2026년 4월 1일부터 주정차 단속 사전알림을 휘슬 앱으로 운영해요.', '고정형 단속 알림은 정상 제공되고, 주행형 단속 알림은 현재 제공되지 않아요.', ARRAY['고정형 단속 알림은 정상 제공되고, 주행형 단속 알림은 현재 제공되지 않아요.']::text[],
       NULL, NULL, NULL, NULL, NULL, '휘슬 고객센터 1599-6270 / 시흥시 교통행정과 031-310-5160', '시흥시 주·정차 민원 서비스 — 사전알림', TRUE
FROM guide WHERE code = 'traffic-alert' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'en', guide_id, 'Parking enforcement alerts (Whistle)', 'Since 1 April 2026, Siheung sends advance parking enforcement alerts through the Whistle app.', 'Alerts from fixed cameras work normally. Alerts from patrol vehicles are not available now.', ARRAY['Alerts from fixed cameras work normally. Alerts from patrol vehicles are not available now.']::text[],
       NULL, NULL, NULL, NULL, NULL, 'Whistle 1599-6270 / Siheung Traffic Administration Division 031-310-5160', '시흥시 주·정차 민원 서비스 — 사전알림', TRUE
FROM guide WHERE code = 'traffic-alert' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'zh', guide_id, '违停执法提醒（Whistle）', '始兴市自2026年4月1日起通过Whistle应用提供违停执法提前提醒。', '固定式执法提醒正常提供，巡逻车执法提醒目前暂停。', ARRAY['固定式执法提醒正常提供，巡逻车执法提醒目前暂停。']::text[],
       NULL, NULL, NULL, NULL, NULL, 'Whistle客服 1599-6270 / 始兴市交通行政科 031-310-5160', '시흥시 주·정차 민원 서비스 — 사전알림', TRUE
FROM guide WHERE code = 'traffic-alert' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'vi', guide_id, 'Thông báo kiểm tra đỗ xe (Whistle)', 'Từ ngày 1/4/2026, Siheung gửi thông báo trước về kiểm tra đỗ xe qua ứng dụng Whistle.', 'Thông báo từ camera cố định hoạt động bình thường; thông báo từ xe tuần tra hiện chưa có.', ARRAY['Thông báo từ camera cố định hoạt động bình thường; thông báo từ xe tuần tra hiện chưa có.']::text[],
       NULL, NULL, NULL, NULL, NULL, 'Whistle 1599-6270 / Phòng Hành chính Giao thông Siheung 031-310-5160', '시흥시 주·정차 민원 서비스 — 사전알림', TRUE
FROM guide WHERE code = 'traffic-alert' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 1, '시흥시 주·정차 민원 서비스 — 사전알림', 'https://www.siheung.go.kr/car/inst/sbas_agre_aply.gs' FROM guide WHERE code = 'traffic-alert'
ON CONFLICT (guide_id, sort_order) DO NOTHING;

-- tax-resident
INSERT INTO guide (code, kind, item_type, category, sort_order, is_faq, source_url, checked_on)
VALUES ('tax-resident', 'RULE', 'INFO', '세금', 401, TRUE, 'https://www.siheung.go.kr/economy/contents.do?mId=0402010600', '2026-11-25')
ON CONFLICT (code) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'ko', guide_id, '주민세 (개인분)', '7월 1일 기준 시흥시에 주소(외국인은 체류지)를 둔 개인은 원칙적으로 8월 16~31일에 주민세를 내요. 기초생활수급자, 일정 요건에 해당하는 미성년자·세대원 등은 제외돼요. 7월 1일에 외국인등록 후 1년이 지나지 않은 외국인도 제외돼요.', '외국인은 주소 대신 출입국관리법상 체류지를 기준으로 해요.
시흥시는 외국인 세대주에게 한국어·영어·중국어 고지서를 보내요. 내지 않으면 비자 연장 제한 같은 불이익이 있을 수 있어요.', ARRAY['외국인은 주소 대신 출입국관리법상 체류지를 기준으로 해요.', '시흥시는 외국인 세대주에게 한국어·영어·중국어 고지서를 보내요. 내지 않으면 비자 연장 제한 같은 불이익이 있을 수 있어요.']::text[],
       NULL, '8월 16일~31일', NULL, '지방세법 제75조', NULL, '시흥시 시세관리과 031-310-2089', '시흥시 주민세', TRUE
FROM guide WHERE code = 'tax-resident' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'en', guide_id, 'Resident tax (individual)', 'Individuals whose address (or registered place of stay for foreign residents) is in Siheung on 1 July generally pay resident tax from 16 to 31 August. Basic livelihood benefit recipients and certain minors and household members are exempt. Foreign residents who registered less than one year before 1 July are also exempt.', 'For foreigners, the place of stay under the Immigration Act is used instead of an address.
Siheung sends bills in Korean, English and Chinese to foreign heads of household. Not paying can lead to disadvantages such as limits on visa extension.', ARRAY['For foreigners, the place of stay under the Immigration Act is used instead of an address.', 'Siheung sends bills in Korean, English and Chinese to foreign heads of household. Not paying can lead to disadvantages such as limits on visa extension.']::text[],
       NULL, '16–31 August', NULL, 'Local Tax Act Article 75', NULL, 'Siheung City Tax Management Division 031-310-2089', '시흥시 주민세', TRUE
FROM guide WHERE code = 'tax-resident' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'zh', guide_id, '居民税（个人部分）', '截至7月1日住址（外国人为居留地）在始兴市的个人，原则上于8月16日至31日缴纳居民税。基础生活保障领取者及符合条件的未成年人、家庭成员等免缴。截至7月1日办理外国人登记未满一年的外国人也免缴。', '外国人以《出入境管理法》规定的居留地代替住址为准。
始兴市向外国人户主寄送韩、英、中文缴费通知书。未缴纳可能会受到限制签证延期等不利影响。', ARRAY['外国人以《出入境管理法》规定的居留地代替住址为准。', '始兴市向外国人户主寄送韩、英、中文缴费通知书。未缴纳可能会受到限制签证延期等不利影响。']::text[],
       NULL, '8月16日~31日', NULL, '《地方税法》第75条', NULL, '始兴市市税管理科 031-310-2089', '시흥시 주민세', TRUE
FROM guide WHERE code = 'tax-resident' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'vi', guide_id, 'Thuế cư trú (cá nhân)', 'Cá nhân có địa chỉ tại Siheung (nơi lưu trú đối với người nước ngoài) vào ngày 1/7 thường nộp thuế cư trú từ 16 đến 31/8. Người hưởng trợ cấp bảo đảm sinh hoạt cơ bản và một số người chưa thành niên, thành viên hộ gia đình được miễn. Người nước ngoài đăng ký chưa đủ một năm tính đến ngày 1/7 cũng được miễn.', 'Với người nước ngoài, nơi cư trú theo Luật Xuất nhập cảnh được dùng thay cho địa chỉ.
Siheung gửi giấy báo thuế bằng tiếng Hàn, Anh, Trung cho chủ hộ người nước ngoài. Không nộp có thể bị bất lợi như hạn chế gia hạn visa.', ARRAY['Với người nước ngoài, nơi cư trú theo Luật Xuất nhập cảnh được dùng thay cho địa chỉ.', 'Siheung gửi giấy báo thuế bằng tiếng Hàn, Anh, Trung cho chủ hộ người nước ngoài. Không nộp có thể bị bất lợi như hạn chế gia hạn visa.']::text[],
       NULL, '16–31/8', NULL, 'Luật Thuế Địa phương Điều 75', NULL, 'Phòng Quản lý Thuế Siheung 031-310-2089', '시흥시 주민세', TRUE
FROM guide WHERE code = 'tax-resident' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 1, '시흥시 주민세', 'https://www.siheung.go.kr/economy/contents.do?mId=0402010600' FROM guide WHERE code = 'tax-resident'
ON CONFLICT (guide_id, sort_order) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 2, '국가법령정보센터 — 지방세법 제75조', 'https://www.law.go.kr/LSW/lsLinkCommonInfo.do?chrClsCd=010202&lsJoLnkSeq=1030540701' FROM guide WHERE code = 'tax-resident'
ON CONFLICT (guide_id, sort_order) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 3, '시흥시 보도자료 — 외국인 납세자 맞춤형 주민세 고지서 발송', 'https://www.siheung.go.kr/media/bbs/view.do?mId=0100000000&bIdx=177130&ptIdx=82' FROM guide WHERE code = 'tax-resident'
ON CONFLICT (guide_id, sort_order) DO NOTHING;

-- tax-property
INSERT INTO guide (code, kind, item_type, category, sort_order, is_faq, source_url, checked_on)
VALUES ('tax-property', 'RULE', 'INFO', '세금', 402, FALSE, 'https://www.siheung.go.kr/economy/contents.do?mId=0402010800', '2026-11-25')
ON CONFLICT (code) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'ko', guide_id, '재산세', '6월 1일 소유자를 기준으로 해요. 주택 외 건축물은 7월, 토지는 9월에 내요. 주택분은 7월과 9월에 나눠 내지만 연간 본세가 10만 원 이하면 7월에 전액 내요.', '7월(16~31일): 주택분의 절반과 주택 외 건축물분. 주택분 연간 본세가 10만 원 이하이면 7월에 전액 내요.
9월(16~30일): 주택 세금의 나머지 절반과 토지.', ARRAY['7월(16~31일): 주택분의 절반과 주택 외 건축물분. 주택분 연간 본세가 10만 원 이하이면 7월에 전액 내요.', '9월(16~30일): 주택 세금의 나머지 절반과 토지.']::text[],
       NULL, '7월 16~31일, 9월 16~30일', NULL, NULL, NULL, '시흥시 시세관리과 재산세팀 031-310-2081', '시흥시 재산세', TRUE
FROM guide WHERE code = 'tax-property' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'en', guide_id, 'Property tax', 'Tax is based on ownership on 1 June. Non-residential buildings are billed in July and land in September. Homeowners pay in July and September, except when the annual base property tax on the home is 100,000 won or less, in which case the full amount is due in July.', 'July (16–31): half of the home’s property tax and all tax on buildings other than homes. If the annual base property tax on the home is 100,000 won or less, the full amount is due in July.
September (16–30): the other half of the housing tax and land.', ARRAY['July (16–31): half of the home’s property tax and all tax on buildings other than homes. If the annual base property tax on the home is 100,000 won or less, the full amount is due in July.', 'September (16–30): the other half of the housing tax and land.']::text[],
       NULL, '16–31 July, 16–30 September', NULL, NULL, NULL, 'Siheung City Tax Management Division, property tax 031-310-2081', '시흥시 재산세', TRUE
FROM guide WHERE code = 'tax-property' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'zh', guide_id, '财产税', '按6月1日的所有权确定纳税人。非住宅建筑物在7月缴纳，土地在9月缴纳。住宅在7月和9月分两期缴纳；如果住宅全年财产税本金不超过10万韩元，则在7月一次缴清。', '7月（16~31日）：缴纳住宅财产税的一半及非住宅建筑物的全部税额。住宅全年财产税本金不超过10万韩元时，在7月一次缴清。
9月（16~30日）：住宅税额的另一半及土地。', ARRAY['7月（16~31日）：缴纳住宅财产税的一半及非住宅建筑物的全部税额。住宅全年财产税本金不超过10万韩元时，在7月一次缴清。', '9月（16~30日）：住宅税额的另一半及土地。']::text[],
       NULL, '7月16~31日、9月16~30日', NULL, NULL, NULL, '始兴市市税管理科财产税组 031-310-2081', '시흥시 재산세', TRUE
FROM guide WHERE code = 'tax-property' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'vi', guide_id, 'Thuế tài sản', 'Thuế được xác định theo quyền sở hữu vào ngày 1/6. Công trình không phải nhà ở nộp vào tháng 7, đất nộp vào tháng 9. Thuế nhà ở nộp vào tháng 7 và tháng 9; nếu tiền thuế gốc cả năm không quá 100.000 won thì nộp toàn bộ vào tháng 7.', 'Tháng 7 (16–31): nộp một nửa thuế nhà ở và toàn bộ thuế công trình không phải nhà ở. Nếu tiền thuế gốc cả năm của nhà ở không quá 100.000 won thì nộp toàn bộ vào tháng 7.
Tháng 9 (16–30): nửa còn lại của thuế nhà ở và đất.', ARRAY['Tháng 7 (16–31): nộp một nửa thuế nhà ở và toàn bộ thuế công trình không phải nhà ở. Nếu tiền thuế gốc cả năm của nhà ở không quá 100.000 won thì nộp toàn bộ vào tháng 7.', 'Tháng 9 (16–30): nửa còn lại của thuế nhà ở và đất.']::text[],
       NULL, '16–31/7, 16–30/9', NULL, NULL, NULL, 'Phòng Quản lý Thuế Siheung, tổ thuế tài sản 031-310-2081', '시흥시 재산세', TRUE
FROM guide WHERE code = 'tax-property' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 1, '시흥시 재산세', 'https://www.siheung.go.kr/economy/contents.do?mId=0402010800' FROM guide WHERE code = 'tax-property'
ON CONFLICT (guide_id, sort_order) DO NOTHING;

-- tax-car
INSERT INTO guide (code, kind, item_type, category, sort_order, is_faq, source_url, checked_on)
VALUES ('tax-car', 'RULE', 'INFO', '세금', 403, FALSE, 'https://www.siheung.go.kr/economy/contents.do?mId=0402010900', '2026-11-25')
ON CONFLICT (code) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'ko', guide_id, '자동차세', '자동차를 가진 사람이 6월과 12월에 나눠서 내요. 1년치를 미리 내면 할인돼요.', '1기분(1~6월분)은 6월 16~30일, 2기분(7~12월분)은 12월 16~31일에 내요.
연납: 미리 한 번에 내면 남은 기간 세금의 5%를 빼 줘요. 1월·3월·6월·9월 16일부터 신청해요.', ARRAY['1기분(1~6월분)은 6월 16~30일, 2기분(7~12월분)은 12월 16~31일에 내요.', '연납: 미리 한 번에 내면 남은 기간 세금의 5%를 빼 줘요. 1월·3월·6월·9월 16일부터 신청해요.']::text[],
       NULL, '6월 16~30일, 12월 16~31일', NULL, NULL, NULL, '시흥시 시세관리과 031-310-2093', '시흥시 자동차세', TRUE
FROM guide WHERE code = 'tax-car' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'en', guide_id, 'Car tax', 'Car owners pay it in June and December. Paying the whole year in advance gives a discount.', 'The first half (January–June) is paid 16–30 June, the second half (July–December) 16–31 December.
Annual payment: paying in advance takes 5% off the tax for the remaining period. Apply from the 16th of January, March, June or September.', ARRAY['The first half (January–June) is paid 16–30 June, the second half (July–December) 16–31 December.', 'Annual payment: paying in advance takes 5% off the tax for the remaining period. Apply from the 16th of January, March, June or September.']::text[],
       NULL, '16–30 June, 16–31 December', NULL, NULL, NULL, 'Siheung City Tax Management Division 031-310-2093', '시흥시 자동차세', TRUE
FROM guide WHERE code = 'tax-car' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'zh', guide_id, '汽车税', '拥有汽车的人于6月和12月分两次缴纳。提前缴纳全年税额可享受减免。', '第1期（1~6月）于6月16~30日缴纳，第2期（7~12月）于12月16~31日缴纳。
年缴：提前一次缴纳可减免剩余期间税额的5%，可于1月、3月、6月、9月16日起申请。', ARRAY['第1期（1~6月）于6月16~30日缴纳，第2期（7~12月）于12月16~31日缴纳。', '年缴：提前一次缴纳可减免剩余期间税额的5%，可于1月、3月、6月、9月16日起申请。']::text[],
       NULL, '6月16~30日、12月16~31日', NULL, NULL, NULL, '始兴市市税管理科 031-310-2093', '시흥시 자동차세', TRUE
FROM guide WHERE code = 'tax-car' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'vi', guide_id, 'Thuế ô tô', 'Chủ xe nộp chia làm hai lần vào tháng 6 và tháng 12. Nộp trước cả năm sẽ được giảm.', 'Kỳ 1 (tháng 1–6) nộp 16–30/6, kỳ 2 (tháng 7–12) nộp 16–31/12.
Nộp cả năm: nộp trước một lần được giảm 5% thuế của thời gian còn lại. Đăng ký từ ngày 16 tháng 1, 3, 6 hoặc 9.', ARRAY['Kỳ 1 (tháng 1–6) nộp 16–30/6, kỳ 2 (tháng 7–12) nộp 16–31/12.', 'Nộp cả năm: nộp trước một lần được giảm 5% thuế của thời gian còn lại. Đăng ký từ ngày 16 tháng 1, 3, 6 hoặc 9.']::text[],
       NULL, '16–30/6, 16–31/12', NULL, NULL, NULL, 'Phòng Quản lý Thuế Siheung 031-310-2093', '시흥시 자동차세', TRUE
FROM guide WHERE code = 'tax-car' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 1, '시흥시 자동차세', 'https://www.siheung.go.kr/economy/contents.do?mId=0402010900' FROM guide WHERE code = 'tax-car'
ON CONFLICT (guide_id, sort_order) DO NOTHING;

-- tax-payment
INSERT INTO guide (code, kind, item_type, category, sort_order, is_faq, source_url, checked_on)
VALUES ('tax-payment', 'RULE', 'INFO', '세금', 404, FALSE, 'https://www.siheung.go.kr/economy/contents.do?mId=0402020300', '2026-11-25')
ON CONFLICT (code) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'ko', guide_id, '세금 내는 방법과 가산세', '위택스·스마트위택스 앱이나 고지서의 가상계좌로 내요. 기한을 넘기면 3%가 더 붙어요.', '위택스에서는 계좌이체·신용카드·간편결제를 쓸 수 있어요 (00:30~23:30).
가상계좌는 00:30~22:00에 이체할 수 있고, 다른 은행에서 보내면 수수료는 내는 사람이 부담해요.
세금이 45만 원 이상이면 기한 후 한 달마다 0.66%가 최대 60개월까지 더 붙어요.', ARRAY['위택스에서는 계좌이체·신용카드·간편결제를 쓸 수 있어요 (00:30~23:30).', '가상계좌는 00:30~22:00에 이체할 수 있고, 다른 은행에서 보내면 수수료는 내는 사람이 부담해요.', '세금이 45만 원 이상이면 기한 후 한 달마다 0.66%가 최대 60개월까지 더 붙어요.']::text[],
       NULL, NULL, NULL, NULL, NULL, '시흥시 징수과 031-310-3069', '시흥시 인터넷 납부', TRUE
FROM guide WHERE code = 'tax-payment' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'en', guide_id, 'How to pay and late fees', 'Pay on Wetax, the Smart Wetax app, or the virtual account on the bill. After the due date, 3% is added.', 'Wetax accepts bank transfer, credit card and simple payment (00:30–23:30).
Virtual accounts accept transfers 00:30–22:00. Transfers from another bank may cost you a fee.
If the tax is 450,000 won or more, 0.66% is added every month after the due date, for up to 60 months.', ARRAY['Wetax accepts bank transfer, credit card and simple payment (00:30–23:30).', 'Virtual accounts accept transfers 00:30–22:00. Transfers from another bank may cost you a fee.', 'If the tax is 450,000 won or more, 0.66% is added every month after the due date, for up to 60 months.']::text[],
       NULL, NULL, NULL, NULL, NULL, 'Siheung Tax Collection Division 031-310-3069', '시흥시 인터넷 납부', TRUE
FROM guide WHERE code = 'tax-payment' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'zh', guide_id, '缴税方法与滞纳金', '可通过Wetax网站、Smart Wetax应用或通知书上的虚拟账户缴纳。逾期加收3%。', 'Wetax支持转账、信用卡和简易支付（00:30~23:30）。
虚拟账户可于00:30~22:00转账，从其他银行转账的手续费由缴纳人承担。
税额在45万韩元以上的，逾期后每月加收0.66%，最长60个月。', ARRAY['Wetax支持转账、信用卡和简易支付（00:30~23:30）。', '虚拟账户可于00:30~22:00转账，从其他银行转账的手续费由缴纳人承担。', '税额在45万韩元以上的，逾期后每月加收0.66%，最长60个月。']::text[],
       NULL, NULL, NULL, NULL, NULL, '始兴市征收科 031-310-3069', '시흥시 인터넷 납부', TRUE
FROM guide WHERE code = 'tax-payment' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'vi', guide_id, 'Cách nộp thuế và tiền phạt chậm nộp', 'Nộp qua Wetax, ứng dụng Smart Wetax hoặc tài khoản ảo trên giấy báo. Quá hạn sẽ cộng thêm 3%.', 'Wetax nhận chuyển khoản, thẻ tín dụng và thanh toán nhanh (00:30–23:30).
Tài khoản ảo nhận chuyển khoản 00:30–22:00; chuyển từ ngân hàng khác thì người nộp chịu phí.
Nếu thuế từ 450.000 won trở lên, mỗi tháng quá hạn cộng thêm 0,66%, tối đa 60 tháng.', ARRAY['Wetax nhận chuyển khoản, thẻ tín dụng và thanh toán nhanh (00:30–23:30).', 'Tài khoản ảo nhận chuyển khoản 00:30–22:00; chuyển từ ngân hàng khác thì người nộp chịu phí.', 'Nếu thuế từ 450.000 won trở lên, mỗi tháng quá hạn cộng thêm 0,66%, tối đa 60 tháng.']::text[],
       NULL, NULL, NULL, NULL, NULL, 'Phòng Thu thuế Siheung 031-310-3069', '시흥시 인터넷 납부', TRUE
FROM guide WHERE code = 'tax-payment' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 1, '시흥시 인터넷 납부', 'https://www.siheung.go.kr/economy/contents.do?mId=0402020300' FROM guide WHERE code = 'tax-payment'
ON CONFLICT (guide_id, sort_order) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 2, '시흥시 납부지연가산세', 'https://www.siheung.go.kr/economy/contents.do?mId=0402011100' FROM guide WHERE code = 'tax-payment'
ON CONFLICT (guide_id, sort_order) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 3, '위택스 납부하기', 'https://www.wetax.go.kr/tcp/wtg/J040104M01.do' FROM guide WHERE code = 'tax-payment'
ON CONFLICT (guide_id, sort_order) DO NOTHING;

-- living-siru
INSERT INTO guide (code, kind, item_type, category, sort_order, is_faq, source_url, checked_on)
VALUES ('living-siru', 'RULE', 'INFO', '생활 편의', 501, FALSE, 'https://www.siheung.go.kr/economy/contents.do?mId=0504060000', '2026-11-25')
ON CONFLICT (code) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'ko', guide_id, '시흥화폐 시루', '시흥시 지역화폐예요. 살 때 8~10% 할인 혜택이 있어요.', '종이 상품권은 시흥시 안의 농협·새마을금고·신협에서 사요.
모바일은 ‘지역상품권 chak’ 앱을 설치해서 써요.', ARRAY['종이 상품권은 시흥시 안의 농협·새마을금고·신협에서 사요.', '모바일은 ‘지역상품권 chak’ 앱을 설치해서 써요.']::text[],
       NULL, NULL, NULL, NULL, NULL, '시흥시 소상공인과 031-310-3545', '시흥화폐 시루', TRUE
FROM guide WHERE code = 'living-siru' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'en', guide_id, 'Siheung local currency “Siru”', 'Siheung’s local currency. You get an 8–10% discount when you buy it.', 'Buy paper vouchers at NongHyup, MG Saemaul Geumgo or Shinhyup branches in Siheung.
For mobile, install the “chak” local voucher app.', ARRAY['Buy paper vouchers at NongHyup, MG Saemaul Geumgo or Shinhyup branches in Siheung.', 'For mobile, install the “chak” local voucher app.']::text[],
       NULL, NULL, NULL, NULL, NULL, 'Siheung Small Business Division 031-310-3545', '시흥화폐 시루', TRUE
FROM guide WHERE code = 'living-siru' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'zh', guide_id, '始兴货币“Siru”', '始兴市的地区货币，购买时可享受8~10%的优惠。', '纸质商品券可在始兴市内的农协、新村金库、信协购买。
手机支付请安装“地区商品券chak”应用。', ARRAY['纸质商品券可在始兴市内的农协、新村金库、信协购买。', '手机支付请安装“地区商品券chak”应用。']::text[],
       NULL, NULL, NULL, NULL, NULL, '始兴市小商工人科 031-310-3545', '시흥화폐 시루', TRUE
FROM guide WHERE code = 'living-siru' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'vi', guide_id, 'Tiền địa phương Siheung “Siru”', 'Tiền địa phương của Siheung. Khi mua được ưu đãi 8–10%.', 'Phiếu giấy mua tại NongHyup, MG Saemaul Geumgo hoặc Shinhyup ở Siheung.
Dùng trên điện thoại bằng cách cài ứng dụng “chak”.', ARRAY['Phiếu giấy mua tại NongHyup, MG Saemaul Geumgo hoặc Shinhyup ở Siheung.', 'Dùng trên điện thoại bằng cách cài ứng dụng “chak”.']::text[],
       NULL, NULL, NULL, NULL, NULL, 'Phòng Tiểu thương Siheung 031-310-3545', '시흥화폐 시루', TRUE
FROM guide WHERE code = 'living-siru' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 1, '시흥화폐 시루', 'https://www.siheung.go.kr/economy/contents.do?mId=0504060000' FROM guide WHERE code = 'living-siru'
ON CONFLICT (guide_id, sort_order) DO NOTHING;

-- living-bike
INSERT INTO guide (code, kind, item_type, category, sort_order, is_faq, source_url, checked_on)
VALUES ('living-bike', 'RULE', 'INFO', '생활 편의', 502, FALSE, 'https://www.siheung.go.kr/portal/contents.do?mId=0517030200', '2026-11-25')
ON CONFLICT (code) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'ko', guide_id, '자전거 대여소', '정왕역과 월곶역 자전거 대여소에서 신분증을 보여 주고 빌린 뒤, 같은 곳에 그날 반납해요.', '사진이 있는 신분증(여권 포함)과 휴대폰이 필요해요. 신분증이 없으면 빌릴 수 없어요.
정왕역점: 월~금 07:00~20:00 (031-433-0101). 월곶역점: 수~일 08:30~20:30 (031-435-0201).
운영 기간이 정해져 있어요 (2026년은 3월~11월). 해마다 시흥시 안내를 확인하세요.', ARRAY['사진이 있는 신분증(여권 포함)과 휴대폰이 필요해요. 신분증이 없으면 빌릴 수 없어요.', '정왕역점: 월~금 07:00~20:00 (031-433-0101). 월곶역점: 수~일 08:30~20:30 (031-435-0201).', '운영 기간이 정해져 있어요 (2026년은 3월~11월). 해마다 시흥시 안내를 확인하세요.']::text[],
       NULL, NULL, NULL, NULL, NULL, '시흥시 도로시설과 031-310-3146', '시흥시 자전거 대여소', TRUE
FROM guide WHERE code = 'living-bike' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'en', guide_id, 'Bicycle rental', 'Rent a bike at the Jeongwang or Wolgot station rental office with your ID and return it there the same day.', 'Bring a photo ID (a passport is fine) and your phone. Without ID you cannot rent.
Jeongwang Station: Mon–Fri 07:00–20:00 (031-433-0101). Wolgot Station: Wed–Sun 08:30–20:30 (031-435-0201).
They open only for a set period (March–November in 2026). Check Siheung’s notice each year.', ARRAY['Bring a photo ID (a passport is fine) and your phone. Without ID you cannot rent.', 'Jeongwang Station: Mon–Fri 07:00–20:00 (031-433-0101). Wolgot Station: Wed–Sun 08:30–20:30 (031-435-0201).', 'They open only for a set period (March–November in 2026). Check Siheung’s notice each year.']::text[],
       NULL, NULL, NULL, NULL, NULL, 'Siheung Road Facilities Division 031-310-3146', '시흥시 자전거 대여소', TRUE
FROM guide WHERE code = 'living-bike' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'zh', guide_id, '自行车租赁点', '在正往站或月串站自行车租赁点出示身份证租借，当天归还到原租赁点。', '需要带照片的身份证件（含护照）和手机，没有证件无法租借。
正往站点：周一至周五 07:00~20:00（031-433-0101）。月串站点：周三至周日 08:30~20:30（031-435-0201）。
有固定运营期间（2026年为3月~11月），请每年查看始兴市通知。', ARRAY['需要带照片的身份证件（含护照）和手机，没有证件无法租借。', '正往站点：周一至周五 07:00~20:00（031-433-0101）。月串站点：周三至周日 08:30~20:30（031-435-0201）。', '有固定运营期间（2026年为3月~11月），请每年查看始兴市通知。']::text[],
       NULL, NULL, NULL, NULL, NULL, '始兴市道路设施科 031-310-3146', '시흥시 자전거 대여소', TRUE
FROM guide WHERE code = 'living-bike' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'vi', guide_id, 'Điểm cho thuê xe đạp', 'Thuê xe đạp tại điểm ga Jeongwang hoặc Wolgot bằng giấy tờ tùy thân và trả lại đúng nơi đó trong ngày.', 'Cần giấy tờ có ảnh (kể cả hộ chiếu) và điện thoại. Không có giấy tờ thì không thuê được.
Ga Jeongwang: thứ Hai–thứ Sáu 07:00–20:00 (031-433-0101). Ga Wolgot: thứ Tư–Chủ nhật 08:30–20:30 (031-435-0201).
Chỉ mở trong thời gian nhất định (năm 2026: tháng 3–11). Hãy xem thông báo của Siheung mỗi năm.', ARRAY['Cần giấy tờ có ảnh (kể cả hộ chiếu) và điện thoại. Không có giấy tờ thì không thuê được.', 'Ga Jeongwang: thứ Hai–thứ Sáu 07:00–20:00 (031-433-0101). Ga Wolgot: thứ Tư–Chủ nhật 08:30–20:30 (031-435-0201).', 'Chỉ mở trong thời gian nhất định (năm 2026: tháng 3–11). Hãy xem thông báo của Siheung mỗi năm.']::text[],
       NULL, NULL, NULL, NULL, NULL, 'Phòng Cơ sở Hạ tầng Đường bộ Siheung 031-310-3146', '시흥시 자전거 대여소', TRUE
FROM guide WHERE code = 'living-bike' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 1, '시흥시 자전거 대여소', 'https://www.siheung.go.kr/portal/contents.do?mId=0517030200' FROM guide WHERE code = 'living-bike'
ON CONFLICT (guide_id, sort_order) DO NOTHING;

-- support-1345
INSERT INTO guide (code, kind, item_type, category, sort_order, is_faq, source_url, checked_on)
VALUES ('support-1345', 'RULE', 'INFO', '교육·상담', 601, FALSE, 'https://www.immigration.go.kr/immigration/1530/subview.do', '2026-11-25')
ON CONFLICT (code) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'ko', guide_id, '외국인종합안내센터 1345', '체류·비자 같은 생활 문의를 20개 언어로 상담해요. 국번 없이 1345로 전화해요.', '평일 09:00~22:00에 운영하고, 18시 이후에는 한국어·영어·중국어만 돼요.
해외에서는 +82-2-1345로 걸어요.', ARRAY['평일 09:00~22:00에 운영하고, 18시 이후에는 한국어·영어·중국어만 돼요.', '해외에서는 +82-2-1345로 걸어요.']::text[],
       NULL, NULL, NULL, NULL, NULL, '1345', '법무부 외국인종합안내센터', TRUE
FROM guide WHERE code = 'support-1345' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'en', guide_id, 'Immigration Contact Center 1345', 'Ask about stay, visas and daily life in 20 languages. Call 1345 without an area code.', 'Open weekdays 09:00–22:00. After 18:00, only Korean, English and Chinese are available.
From abroad, call +82-2-1345.', ARRAY['Open weekdays 09:00–22:00. After 18:00, only Korean, English and Chinese are available.', 'From abroad, call +82-2-1345.']::text[],
       NULL, NULL, NULL, NULL, NULL, '1345', '법무부 외국인종합안내센터', TRUE
FROM guide WHERE code = 'support-1345' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'zh', guide_id, '外国人综合服务中心 1345', '可用20种语言咨询居留、签证等生活问题。直接拨打1345，无需区号。', '工作日09:00~22:00运营，18点后仅提供韩语、英语和中文服务。
在海外请拨打 +82-2-1345。', ARRAY['工作日09:00~22:00运营，18点后仅提供韩语、英语和中文服务。', '在海外请拨打 +82-2-1345。']::text[],
       NULL, NULL, NULL, NULL, NULL, '1345', '법무부 외국인종합안내센터', TRUE
FROM guide WHERE code = 'support-1345' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'vi', guide_id, 'Trung tâm hỗ trợ người nước ngoài 1345', 'Tư vấn về lưu trú, visa và đời sống bằng 20 ngôn ngữ. Gọi 1345, không cần mã vùng.', 'Hoạt động ngày thường 09:00–22:00; sau 18:00 chỉ có tiếng Hàn, Anh và Trung.
Từ nước ngoài, gọi +82-2-1345.', ARRAY['Hoạt động ngày thường 09:00–22:00; sau 18:00 chỉ có tiếng Hàn, Anh và Trung.', 'Từ nước ngoài, gọi +82-2-1345.']::text[],
       NULL, NULL, NULL, NULL, NULL, '1345', '법무부 외국인종합안내센터', TRUE
FROM guide WHERE code = 'support-1345' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 1, '법무부 외국인종합안내센터', 'https://www.immigration.go.kr/immigration/1530/subview.do' FROM guide WHERE code = 'support-1345'
ON CONFLICT (guide_id, sort_order) DO NOTHING;

-- support-welfare-center
INSERT INTO guide (code, kind, item_type, category, sort_order, is_faq, source_url, checked_on)
VALUES ('support-welfare-center', 'RULE', 'INFO', '교육·상담', 602, FALSE, 'https://www.shmwc.or.kr', '2026-11-25')
ON CONFLICT (code) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'ko', guide_id, '시흥시외국인복지센터', '정왕동에서 외국인 주민을 위한 상담·교육·복지·문화 프로그램을 운영해요.', '평일 09:00~18:00 (점심 12:00~13:00), 토요일은 쉬어요.', ARRAY['평일 09:00~18:00 (점심 12:00~13:00), 토요일은 쉬어요.']::text[],
       '경기도 시흥시 공단1대로259번길 5 (정왕동)', NULL, NULL, NULL, NULL, '031-434-0411', '시흥시외국인복지센터', TRUE
FROM guide WHERE code = 'support-welfare-center' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'en', guide_id, 'Siheung Foreign Residents Welfare Center', 'Runs counseling, education, welfare and culture programs for foreign residents in Jeongwang-dong.', 'Weekdays 09:00–18:00 (lunch 12:00–13:00). Closed on Saturday.', ARRAY['Weekdays 09:00–18:00 (lunch 12:00–13:00). Closed on Saturday.']::text[],
       '5 Gongdan 1-daero 259beon-gil, Siheung (Jeongwang-dong)', NULL, NULL, NULL, NULL, '031-434-0411', '시흥시외국인복지센터', TRUE
FROM guide WHERE code = 'support-welfare-center' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'zh', guide_id, '始兴市外国人福利中心', '在正往洞为外国人居民提供咨询、教育、福利和文化项目。', '工作日09:00~18:00（午休12:00~13:00），周六休息。', ARRAY['工作日09:00~18:00（午休12:00~13:00），周六休息。']::text[],
       '京畿道始兴市工团1大路259号街5（正往洞）', NULL, NULL, NULL, NULL, '031-434-0411', '시흥시외국인복지센터', TRUE
FROM guide WHERE code = 'support-welfare-center' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'vi', guide_id, 'Trung tâm Phúc lợi Người nước ngoài Siheung', 'Tổ chức tư vấn, giáo dục, phúc lợi và văn hóa cho cư dân nước ngoài tại Jeongwang-dong.', 'Ngày thường 09:00–18:00 (nghỉ trưa 12:00–13:00), nghỉ thứ Bảy.', ARRAY['Ngày thường 09:00–18:00 (nghỉ trưa 12:00–13:00), nghỉ thứ Bảy.']::text[],
       '5 Gongdan 1-daero 259beon-gil, Siheung (Jeongwang-dong)', NULL, NULL, NULL, NULL, '031-434-0411', '시흥시외국인복지센터', TRUE
FROM guide WHERE code = 'support-welfare-center' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 1, '시흥시외국인복지센터', 'https://www.shmwc.or.kr' FROM guide WHERE code = 'support-welfare-center'
ON CONFLICT (guide_id, sort_order) DO NOTHING;

-- support-family
INSERT INTO guide (code, kind, item_type, category, sort_order, is_faq, source_url, checked_on)
VALUES ('support-family', 'RULE', 'INFO', '교육·상담', 603, FALSE, 'https://www.liveinkorea.kr/portal/KOR/page/contents.do?menuSeq=6652&pageSeq=161', '2026-11-25')
ON CONFLICT (code) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'ko', guide_id, '다누리콜센터·가족센터', '다문화가족은 다누리콜센터(1577-1366)에서 365일 24시간 15개 언어로 상담받을 수 있어요.', '다누리콜센터는 생활 정보, 3자 통화 통역, 폭력 피해 긴급 지원, 가족 상담을 해요.
시흥시 가족센터는 결혼이민자와 중도입국 자녀에게 3월~12월 한국어 교육을 해요.', ARRAY['다누리콜센터는 생활 정보, 3자 통화 통역, 폭력 피해 긴급 지원, 가족 상담을 해요.', '시흥시 가족센터는 결혼이민자와 중도입국 자녀에게 3월~12월 한국어 교육을 해요.']::text[],
       '시흥시 가족센터: 시흥시 정왕천로449번길 51', NULL, NULL, NULL, NULL, '다누리콜센터 1577-1366 / 시흥시 가족센터 031-319-7997', '다누리 — 다누리콜센터 안내', TRUE
FROM guide WHERE code = 'support-family' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'en', guide_id, 'Danuri Call Center and Family Center', 'Multicultural families can get counseling in 15 languages, 24 hours a day, every day, at the Danuri Call Center (1577-1366).', 'Danuri offers daily-life information, three-way interpreting calls, emergency help for violence victims and family counseling.
Siheung Family Center teaches Korean to marriage migrants and children who came to Korea later, from March to December.', ARRAY['Danuri offers daily-life information, three-way interpreting calls, emergency help for violence victims and family counseling.', 'Siheung Family Center teaches Korean to marriage migrants and children who came to Korea later, from March to December.']::text[],
       'Siheung Family Center: 51 Jeongwangcheon-ro 449beon-gil, Siheung', NULL, NULL, NULL, NULL, 'Danuri 1577-1366 / Siheung Family Center 031-319-7997', '다누리 — 다누리콜센터 안내', TRUE
FROM guide WHERE code = 'support-family' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'zh', guide_id, 'Danuri呼叫中心·家庭中心', '多元文化家庭可通过Danuri呼叫中心（1577-1366）全年24小时以15种语言接受咨询。', 'Danuri提供生活信息、三方通话翻译、暴力受害紧急支援和家庭咨询。
始兴市家庭中心于3月至12月为结婚移民者和中途入境子女提供韩语教育。', ARRAY['Danuri提供生活信息、三方通话翻译、暴力受害紧急支援和家庭咨询。', '始兴市家庭中心于3月至12月为结婚移民者和中途入境子女提供韩语教育。']::text[],
       '始兴市家庭中心：始兴市正往川路449号街51', NULL, NULL, NULL, NULL, 'Danuri 1577-1366 / 始兴市家庭中心 031-319-7997', '다누리 — 다누리콜센터 안내', TRUE
FROM guide WHERE code = 'support-family' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'vi', guide_id, 'Tổng đài Danuri và Trung tâm Gia đình', 'Gia đình đa văn hóa được tư vấn bằng 15 ngôn ngữ, 24 giờ mỗi ngày, cả năm tại Tổng đài Danuri (1577-1366).', 'Danuri cung cấp thông tin đời sống, phiên dịch qua cuộc gọi ba bên, hỗ trợ khẩn cấp cho nạn nhân bạo lực và tư vấn gia đình.
Trung tâm Gia đình Siheung dạy tiếng Hàn cho người nhập cư kết hôn và con nhập cảnh giữa chừng từ tháng 3 đến tháng 12.', ARRAY['Danuri cung cấp thông tin đời sống, phiên dịch qua cuộc gọi ba bên, hỗ trợ khẩn cấp cho nạn nhân bạo lực và tư vấn gia đình.', 'Trung tâm Gia đình Siheung dạy tiếng Hàn cho người nhập cư kết hôn và con nhập cảnh giữa chừng từ tháng 3 đến tháng 12.']::text[],
       'Trung tâm Gia đình Siheung: 51 Jeongwangcheon-ro 449beon-gil, Siheung', NULL, NULL, NULL, NULL, 'Danuri 1577-1366 / Trung tâm Gia đình Siheung 031-319-7997', '다누리 — 다누리콜센터 안내', TRUE
FROM guide WHERE code = 'support-family' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 1, '다누리 — 다누리콜센터 안내', 'https://www.liveinkorea.kr/portal/KOR/page/contents.do?menuSeq=6652&pageSeq=161' FROM guide WHERE code = 'support-family'
ON CONFLICT (guide_id, sort_order) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 2, '시흥시 가족센터 오시는 길', 'https://shcity.familynet.or.kr/center/lay1/program/S295T315C321/center_info/view.do' FROM guide WHERE code = 'support-family'
ON CONFLICT (guide_id, sort_order) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 3, '시흥시 다문화가족 지원사업', 'https://www.siheung.go.kr/portal/contents.do?mId=0205020000' FROM guide WHERE code = 'support-family'
ON CONFLICT (guide_id, sort_order) DO NOTHING;

-- support-work
INSERT INTO guide (code, kind, item_type, category, sort_order, is_faq, source_url, checked_on)
VALUES ('support-work', 'RULE', 'INFO', '교육·상담', 604, FALSE, 'https://www.hrdkorea.or.kr/1/3/3/4', '2026-11-25')
ON CONFLICT (code) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'ko', guide_id, '일자리·근로 상담', '외국인 근로자의 고용·생활 고충은 외국인력상담센터(1577-0071)에서 상담해요.', '시흥고용센터 외국인력팀은 별관(마유로418번길 10, 310호)에 있어요. 평일 09:00~18:00.', ARRAY['시흥고용센터 외국인력팀은 별관(마유로418번길 10, 310호)에 있어요. 평일 09:00~18:00.']::text[],
       NULL, NULL, NULL, NULL, NULL, '외국인력상담센터 1577-0071 / 시흥고용센터 외국인력팀 031-496-1922', '한국산업인력공단 외국인력상담센터', TRUE
FROM guide WHERE code = 'support-work' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'en', guide_id, 'Work and job counseling', 'Foreign workers can get help with work and daily-life problems at the Foreign Workforce Counseling Center (1577-0071).', 'The foreign workforce team of Siheung Employment Center is in the annex (10 Mayu-ro 418beon-gil, room 310). Weekdays 09:00–18:00.', ARRAY['The foreign workforce team of Siheung Employment Center is in the annex (10 Mayu-ro 418beon-gil, room 310). Weekdays 09:00–18:00.']::text[],
       NULL, NULL, NULL, NULL, NULL, 'Foreign Workforce Counseling Center 1577-0071 / Siheung Employment Center foreign workforce team 031-496-1922', '한국산업인력공단 외국인력상담센터', TRUE
FROM guide WHERE code = 'support-work' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'zh', guide_id, '工作·劳动咨询', '外国人劳动者的雇佣和生活困扰可向外国人力咨询中心（1577-0071）咨询。', '始兴就业中心外国人力组位于别馆（马游路418号街10，310室），工作日09:00~18:00。', ARRAY['始兴就业中心外国人力组位于别馆（马游路418号街10，310室），工作日09:00~18:00。']::text[],
       NULL, NULL, NULL, NULL, NULL, '外国人力咨询中心 1577-0071 / 始兴就业中心外国人力组 031-496-1922', '한국산업인력공단 외국인력상담센터', TRUE
FROM guide WHERE code = 'support-work' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'vi', guide_id, 'Tư vấn việc làm và lao động', 'Lao động nước ngoài được tư vấn về việc làm và đời sống tại Trung tâm Tư vấn Nhân lực Nước ngoài (1577-0071).', 'Tổ nhân lực nước ngoài của Trung tâm Việc làm Siheung ở tòa phụ (10 Mayu-ro 418beon-gil, phòng 310). Ngày thường 09:00–18:00.', ARRAY['Tổ nhân lực nước ngoài của Trung tâm Việc làm Siheung ở tòa phụ (10 Mayu-ro 418beon-gil, phòng 310). Ngày thường 09:00–18:00.']::text[],
       NULL, NULL, NULL, NULL, NULL, 'Trung tâm Tư vấn Nhân lực Nước ngoài 1577-0071 / Tổ nhân lực nước ngoài, Trung tâm Việc làm Siheung 031-496-1922', '한국산업인력공단 외국인력상담센터', TRUE
FROM guide WHERE code = 'support-work' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 1, '한국산업인력공단 외국인력상담센터', 'https://www.hrdkorea.or.kr/1/3/3/4' FROM guide WHERE code = 'support-work'
ON CONFLICT (guide_id, sort_order) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 2, '시흥고용센터 찾아오시는 길', 'https://www.work.go.kr/siheung/ctrIntro/guideMap/guideMap.do' FROM guide WHERE code = 'support-work'
ON CONFLICT (guide_id, sort_order) DO NOTHING;

-- FAQ (순서대로)
WITH faq AS (
    INSERT INTO rule_faq (guide_id, sort_order) SELECT guide_id, 1 FROM guide WHERE code = 'waste-food' RETURNING faq_id)
INSERT INTO rule_faq_translation (faq_id, language, question, answer)
SELECT faq.faq_id, t.language, t.question, t.answer FROM faq,
    (VALUES ('ko', '음식물 쓰레기는 어디에 버려요?', '노란색 음식물 봉투나 칩을 꽂은 전용 용기, RFID 기기에 일~금요일에 내요. 뼈·조개껍데기·달걀 껍데기는 일반 쓰레기예요.'),
           ('en', 'Where do I throw away food waste?', 'Use a yellow food waste bag, a bin with a chip or an RFID machine, Sunday to Friday. Bones, shells and eggshells are general waste.'),
           ('zh', '厨余垃圾扔在哪里？', '周日至周五投放到黄色厨余袋、插卡专用桶或RFID设备。骨头、贝壳、蛋壳属于一般垃圾。'),
           ('vi', 'Rác thực phẩm bỏ ở đâu?', 'Bỏ vào túi rác thực phẩm màu vàng, thùng có chip hoặc máy RFID từ Chủ nhật đến thứ Sáu. Xương, vỏ sò, vỏ trứng là rác thông thường.')) AS t(language, question, answer);
WITH faq AS (
    INSERT INTO rule_faq (guide_id, sort_order) SELECT guide_id, 2 FROM guide WHERE code = 'waste-bulky' RETURNING faq_id)
INSERT INTO rule_faq_translation (faq_id, language, question, answer)
SELECT faq.faq_id, t.language, t.question, t.answer FROM faq,
    (VALUES ('ko', '가구·매트리스는 어떻게 버려요?', '스마트클린 시흥에서 신청해 수수료를 내거나, 판매소에서 납부필증 스티커를 사서 붙여요. 냉장고·세탁기 같은 폐가전은 1599-0903으로 무상 수거를 신청해요.'),
           ('en', 'How do I throw away furniture or a mattress?', 'Apply and pay on Smart Clean Siheung, or buy a fee sticker at a shop and attach it. For old appliances like refrigerators or washing machines, call 1599-0903 for free pickup.'),
           ('zh', '家具、床垫怎么扔？', '在“智能清洁始兴”申请缴费，或在销售点购买缴费贴纸贴上。冰箱、洗衣机等废旧家电拨打1599-0903申请免费回收。'),
           ('vi', 'Bỏ đồ nội thất, nệm như thế nào?', 'Đăng ký và trả phí trên Smart Clean Siheung, hoặc mua tem nộp phí ở cửa hàng rồi dán. Đồ điện cũ như tủ lạnh, máy giặt gọi 1599-0903 để thu gom miễn phí.')) AS t(language, question, answer);
WITH faq AS (
    INSERT INTO rule_faq (guide_id, sort_order) SELECT guide_id, 3 FROM guide WHERE code = 'traffic-alert' RETURNING faq_id)
INSERT INTO rule_faq_translation (faq_id, language, question, answer)
SELECT faq.faq_id, t.language, t.question, t.answer FROM faq,
    (VALUES ('ko', '주정차 단속 알림은 어떻게 받아요?', '시흥시는 2026년 4월 1일부터 휘슬 앱으로 사전알림을 보내요. 문의는 휘슬 고객센터 1599-6270이에요.'),
           ('en', 'How do I get parking enforcement alerts?', 'Since 1 April 2026, Siheung sends advance alerts through the Whistle app. Whistle help line: 1599-6270.'),
           ('zh', '如何接收违停执法提醒？', '始兴市自2026年4月1日起通过Whistle应用发送提前提醒。咨询：Whistle客服 1599-6270。'),
           ('vi', 'Làm sao nhận thông báo kiểm tra đỗ xe?', 'Từ ngày 1/4/2026, Siheung gửi thông báo trước qua ứng dụng Whistle. Hỗ trợ: 1599-6270.')) AS t(language, question, answer);
WITH faq AS (
    INSERT INTO rule_faq (guide_id, sort_order) SELECT guide_id, 4 FROM guide WHERE code = 'tax-resident' RETURNING faq_id)
INSERT INTO rule_faq_translation (faq_id, language, question, answer)
SELECT faq.faq_id, t.language, t.question, t.answer FROM faq,
    (VALUES ('ko', '주민세는 누가 내요?', '7월 1일 기준 시흥시에 주소(외국인은 체류지)를 둔 개인은 원칙적으로 8월 16~31일에 주민세를 내요. 기초생활수급자, 일정 요건에 해당하는 미성년자·세대원 등은 제외돼요. 7월 1일에 외국인등록 후 1년이 지나지 않은 외국인도 제외돼요.'),
           ('en', 'Who pays the resident tax?', 'Individuals whose address (or registered place of stay for foreign residents) is in Siheung on 1 July generally pay resident tax from 16 to 31 August. Basic livelihood benefit recipients and certain minors and household members are exempt. Foreign residents who registered less than one year before 1 July are also exempt.'),
           ('zh', '谁需要缴纳居民税？', '截至7月1日住址（外国人为居留地）在始兴市的个人，原则上于8月16日至31日缴纳居民税。基础生活保障领取者及符合条件的未成年人、家庭成员等免缴。截至7月1日办理外国人登记未满一年的外国人也免缴。'),
           ('vi', 'Ai phải nộp thuế cư trú?', 'Cá nhân có địa chỉ tại Siheung (nơi lưu trú đối với người nước ngoài) vào ngày 1/7 thường nộp thuế cư trú từ 16 đến 31/8. Người hưởng trợ cấp bảo đảm sinh hoạt cơ bản và một số người chưa thành niên, thành viên hộ gia đình được miễn. Người nước ngoài đăng ký chưa đủ một năm tính đến ngày 1/7 cũng được miễn.')) AS t(language, question, answer);
WITH faq AS (
    INSERT INTO rule_faq (guide_id, sort_order) SELECT guide_id, 5 FROM guide WHERE code = 'order-noise' RETURNING faq_id)
INSERT INTO rule_faq_translation (faq_id, language, question, answer)
SELECT faq.faq_id, t.language, t.question, t.answer FROM faq,
    (VALUES ('ko', '이웃 소음 문제는 어디에 물어봐요?', '아파트라면 관리사무소에 먼저 알리고, 해결되지 않으면 층간소음 이웃사이센터 1661-2642(평일 09:00~18:00)에 상담을 신청해요.'),
           ('en', 'Who can help with noise from neighbors?', 'In an apartment, tell the management office first. If that does not solve it, call the Floor Noise Neighbor Center at 1661-2642 (weekdays 09:00–18:00).'),
           ('zh', '邻居噪音问题向哪里咨询？', '住公寓的话先告知管理处，仍未解决可拨打楼层噪音邻里中心1661-2642（工作日09:00~18:00）申请咨询。'),
           ('vi', 'Hỏi ở đâu khi hàng xóm gây ồn?', 'Nếu ở chung cư, hãy báo ban quản lý trước. Nếu chưa giải quyết được, gọi Trung tâm láng giềng về tiếng ồn 1661-2642 (ngày thường 09:00–18:00).')) AS t(language, question, answer);
