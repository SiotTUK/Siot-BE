-- FE #73 생활수칙 추가: 공공장소 기본 예절(경범죄 범칙금), 어린이·청소년 교통비 지원(경기도·시흥패스+).
-- 공식 자료를 직접 열어 확인한 내용만 넣었다 (확인일 2026-11-27). 근거는 항목마다 guide_source 에 있다.
-- - 공공예절: 경범죄 처벌법 시행령 [별표] 범칙행위 및 범칙금액 (법제처 원문, 2022. 3. 8. 개정)
-- - 교통비: 경기도 어린이·청소년 교통비 지원 포털(경기교통공사). 시흥시 추가 지원(시흥패스+)의 금액·외국인 대상 여부는
--   공식 페이지에서 확인하지 못해(시흥시 교통비 누리집 인증서 만료) 적지 않고 콜센터 확인으로 안내한다
-- - 대형폐기물 앱 '빼기'는 시흥시 공식 안내에 없어 넣지 않는다 (시흥시는 앱 설치 없는 '스마트클린 시흥', waste-bulky)
-- 한국어는 원문 대조를 마쳐 reviewed = TRUE, en/zh/vi 는 사람 검토 전이라 FALSE (관리자 화면에서 검토 대상으로 보인다).

-- order-etiquette
INSERT INTO guide (code, kind, item_type, category, sort_order, is_faq, source_url, checked_on)
VALUES ('order-etiquette', 'RULE', 'INFO', '기초 질서', 103, FALSE, 'https://www.law.go.kr/%EB%B2%95%EB%A0%B9/%EA%B2%BD%EB%B2%94%EC%A3%84%EC%B2%98%EB%B2%8C%EB%B2%95', '2026-11-27')
ON CONFLICT (code) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'ko', guide_id, '공공장소 기본 예절과 범칙금', '길·공원·대중교통 같은 공공장소에서 쓰레기 투기, 침 뱉기, 새치기, 소란을 하면 경범죄로 범칙금을 낼 수 있어요.', '쓰레기를 아무 곳에나 버리면 범칙금 5만원, 담배꽁초·껌·휴지는 3만원이에요.
길·공원처럼 여러 사람이 다니는 곳에서 대소변을 보거나 데려온 개의 대변을 치우지 않으면 5만원, 침을 뱉으면 3만원이에요.
버스·지하철 승차나 입장·매표 줄에 끼어들거나 떠밀면 5만원이에요.
음식점·극장·버스·지하철 등에서 몹시 거친 말이나 행동으로 시끄럽게 하거나 술에 취해 주정하면 5만원이에요.
악기·TV·확성기 소리를 지나치게 크게 내거나 큰 소리로 떠들거나 노래해 이웃을 시끄럽게 하면 3만원이에요.
범칙금 통고를 받고 기한 안에 내지 않으면 가산금이 붙어요 (최대 10만원).', ARRAY['쓰레기를 아무 곳에나 버리면 범칙금 5만원, 담배꽁초·껌·휴지는 3만원이에요.', '길·공원처럼 여러 사람이 다니는 곳에서 대소변을 보거나 데려온 개의 대변을 치우지 않으면 5만원, 침을 뱉으면 3만원이에요.', '버스·지하철 승차나 입장·매표 줄에 끼어들거나 떠밀면 5만원이에요.', '음식점·극장·버스·지하철 등에서 몹시 거친 말이나 행동으로 시끄럽게 하거나 술에 취해 주정하면 5만원이에요.', '악기·TV·확성기 소리를 지나치게 크게 내거나 큰 소리로 떠들거나 노래해 이웃을 시끄럽게 하면 3만원이에요.', '범칙금 통고를 받고 기한 안에 내지 않으면 가산금이 붙어요 (최대 10만원).']::text[],
       NULL, NULL, NULL, '경범죄 처벌법 제3조제1항 제11·12·20·21·36호, 같은 법 시행령 별표 (2022. 3. 8. 개정 기준)', '범칙금 3만~5만원 (행위별 금액은 위 안내)', NULL, '경범죄 처벌법 시행령 별표', TRUE
FROM guide WHERE code = 'order-etiquette' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'en', guide_id, 'Basic manners in public places and fines', 'Littering, spitting, cutting in line or making a disturbance in public places such as streets, parks and public transport is a minor offense and can lead to a fine.', 'Throwing away rubbish anywhere: 50,000 won fine. Cigarette butts, gum or tissues: 30,000 won.
Relieving yourself in streets, parks or other busy places, or not picking up after a dog you brought: 50,000 won. Spitting: 30,000 won.
Cutting in or pushing in a line for a bus, subway, entrance or ticket counter: 50,000 won.
Making a disturbance with very rough words or behavior, or acting drunk toward others, in restaurants, theaters, buses, subways and similar places: 50,000 won.
Disturbing neighbors with very loud instruments, TV or speakers, or by shouting or singing loudly: 30,000 won.
If you do not pay a fine notice by the deadline, a surcharge is added (up to 100,000 won).', ARRAY['Throwing away rubbish anywhere: 50,000 won fine. Cigarette butts, gum or tissues: 30,000 won.', 'Relieving yourself in streets, parks or other busy places, or not picking up after a dog you brought: 50,000 won. Spitting: 30,000 won.', 'Cutting in or pushing in a line for a bus, subway, entrance or ticket counter: 50,000 won.', 'Making a disturbance with very rough words or behavior, or acting drunk toward others, in restaurants, theaters, buses, subways and similar places: 50,000 won.', 'Disturbing neighbors with very loud instruments, TV or speakers, or by shouting or singing loudly: 30,000 won.', 'If you do not pay a fine notice by the deadline, a surcharge is added (up to 100,000 won).']::text[],
       NULL, NULL, NULL, 'Minor Offenses Act Article 3(1) items 11, 12, 20, 21 and 36, and the table in its Enforcement Decree (as amended on 8 March 2022)', 'Fine of 30,000 to 50,000 won (amount for each act above)', NULL, '경범죄 처벌법 시행령 별표', FALSE
FROM guide WHERE code = 'order-etiquette' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'zh', guide_id, '公共场所基本礼仪与罚款', '在街道、公园、公共交通等公共场所乱扔垃圾、随地吐痰、插队或吵闹，属于轻微违法行为，可能被罚款。', '随处乱扔垃圾罚款5万韩元，乱扔烟头、口香糖、纸巾罚款3万韩元。
在街道、公园等人多的地方大小便，或不清理所带狗的粪便，罚款5万韩元；随地吐痰罚款3万韩元。
在乘坐公交·地铁、入场或买票的队伍中插队或推挤，罚款5万韩元。
在餐厅、剧场、公交、地铁等场所用非常粗暴的言行吵闹，或醉酒后无故纠缠他人，罚款5万韩元。
乐器、电视、扩音器声音过大，或大声喧哗、唱歌吵到邻居，罚款3万韩元。
收到罚款通知后未在期限内缴纳，会加收滞纳金（最多10万韩元）。', ARRAY['随处乱扔垃圾罚款5万韩元，乱扔烟头、口香糖、纸巾罚款3万韩元。', '在街道、公园等人多的地方大小便，或不清理所带狗的粪便，罚款5万韩元；随地吐痰罚款3万韩元。', '在乘坐公交·地铁、入场或买票的队伍中插队或推挤，罚款5万韩元。', '在餐厅、剧场、公交、地铁等场所用非常粗暴的言行吵闹，或醉酒后无故纠缠他人，罚款5万韩元。', '乐器、电视、扩音器声音过大，或大声喧哗、唱歌吵到邻居，罚款3万韩元。', '收到罚款通知后未在期限内缴纳，会加收滞纳金（最多10万韩元）。']::text[],
       NULL, NULL, NULL, '《轻犯罪处罚法》第3条第1款第11·12·20·21·36项及同法施行令附表（2022年3月8日修订）', '罚款3万~5万韩元（各行为金额见上文）', NULL, '경범죄 처벌법 시행령 별표', FALSE
FROM guide WHERE code = 'order-etiquette' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'vi', guide_id, 'Phép lịch sự cơ bản nơi công cộng và tiền phạt', 'Xả rác, khạc nhổ, chen hàng hoặc gây ồn ào ở nơi công cộng như đường phố, công viên, phương tiện công cộng là vi phạm nhẹ và có thể bị phạt tiền.', 'Vứt rác bừa bãi: phạt 50.000 won. Đầu lọc thuốc lá, kẹo cao su, giấy vệ sinh: 30.000 won.
Đi vệ sinh ở đường phố, công viên hoặc nơi đông người, hoặc không dọn phân của chó mình dắt theo: 50.000 won. Khạc nhổ: 30.000 won.
Chen ngang hoặc xô đẩy trong hàng lên xe buýt, tàu điện ngầm, vào cửa hoặc mua vé: 50.000 won.
Gây ồn ào bằng lời nói hay hành động rất thô lỗ, hoặc say rượu quấy rối người khác ở nhà hàng, rạp hát, xe buýt, tàu điện ngầm…: 50.000 won.
Làm phiền hàng xóm vì để nhạc cụ, TV, loa quá to hoặc la hét, hát lớn: 30.000 won.
Nếu không nộp phạt đúng hạn sau khi nhận thông báo, sẽ bị cộng thêm tiền phạt chậm nộp (tối đa 100.000 won).', ARRAY['Vứt rác bừa bãi: phạt 50.000 won. Đầu lọc thuốc lá, kẹo cao su, giấy vệ sinh: 30.000 won.', 'Đi vệ sinh ở đường phố, công viên hoặc nơi đông người, hoặc không dọn phân của chó mình dắt theo: 50.000 won. Khạc nhổ: 30.000 won.', 'Chen ngang hoặc xô đẩy trong hàng lên xe buýt, tàu điện ngầm, vào cửa hoặc mua vé: 50.000 won.', 'Gây ồn ào bằng lời nói hay hành động rất thô lỗ, hoặc say rượu quấy rối người khác ở nhà hàng, rạp hát, xe buýt, tàu điện ngầm…: 50.000 won.', 'Làm phiền hàng xóm vì để nhạc cụ, TV, loa quá to hoặc la hét, hát lớn: 30.000 won.', 'Nếu không nộp phạt đúng hạn sau khi nhận thông báo, sẽ bị cộng thêm tiền phạt chậm nộp (tối đa 100.000 won).']::text[],
       NULL, NULL, NULL, 'Luật Xử phạt vi phạm nhẹ Điều 3 khoản 1 mục 11, 12, 20, 21, 36 và bảng phụ lục Nghị định thi hành (sửa đổi ngày 8/3/2022)', 'Phạt 30.000–50.000 won (mức phạt từng hành vi ở trên)', NULL, '경범죄 처벌법 시행령 별표', FALSE
FROM guide WHERE code = 'order-etiquette' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 1, '경범죄 처벌법 (국가법령정보센터)', 'https://www.law.go.kr/%EB%B2%95%EB%A0%B9/%EA%B2%BD%EB%B2%94%EC%A3%84%EC%B2%98%EB%B2%8C%EB%B2%95' FROM guide WHERE code = 'order-etiquette'
ON CONFLICT (guide_id, sort_order) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 2, '경범죄 처벌법 시행령 별표 범칙행위 및 범칙금액 (2022. 3. 8. 개정)', 'https://www.law.go.kr/LSW/flDownload.do?gubun=&flSeq=113559497&bylClsCd=110201' FROM guide WHERE code = 'order-etiquette'
ON CONFLICT (guide_id, sort_order) DO NOTHING;

-- living-youth-transport
INSERT INTO guide (code, kind, item_type, category, sort_order, is_faq, source_url, checked_on)
VALUES ('living-youth-transport', 'RULE', 'INFO', '생활 편의', 503, FALSE, 'https://www.gbuspb.kr/child/user/svc/userServiceInfoView.do', '2026-11-27')
ON CONFLICT (code) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'ko', guide_id, '어린이·청소년 교통비 지원 (경기도·시흥패스+)', '경기도에 사는 만 6~18세 어린이·청소년은 수도권 대중교통 요금을 분기 6만원까지 돌려받아요. 도내 외국인도 대상이에요.', '경기도에 사는 만 6~18세 어린이·청소년이 대상이에요. 도내 외국인과 국내 거소 신고를 한 외국국적 동포도 포함돼요.
수도권 대중교통을 이용한 요금을 분기 6만원, 1년 24만원까지 지역화폐로 돌려받아요.
시흥시는 2025년 7월 1일부터 시흥패스+로 지원을 더 늘렸어요. 시흥시 추가 지원 금액과 외국인 대상 여부는 콜센터에 확인하세요.
경기도 어린이·청소년 교통비 지원 포털(www.gbuspb.kr)에서 회원 가입 → 거주지 확인 → 교통카드 등록 → 지역화폐 등록을 하면 자동으로 신청돼요. 1년 내내 가입할 수 있어요.
준비물: 본인 또는 대리인의 간편·공동·금융 인증서, 어린이·청소년 본인 교통카드(최대 2장), 지역화폐(어린이는 대리인 지역화폐로 등록 가능).
경기도이민사회통합지원센터 안내(2024)에 따르면 외국인은 1개월 안에 발급받은 외국인등록사실증명서(동포는 국내거소사실증명서)를 준비해야 해요.', ARRAY['경기도에 사는 만 6~18세 어린이·청소년이 대상이에요. 도내 외국인과 국내 거소 신고를 한 외국국적 동포도 포함돼요.', '수도권 대중교통을 이용한 요금을 분기 6만원, 1년 24만원까지 지역화폐로 돌려받아요.', '시흥시는 2025년 7월 1일부터 시흥패스+로 지원을 더 늘렸어요. 시흥시 추가 지원 금액과 외국인 대상 여부는 콜센터에 확인하세요.', '경기도 어린이·청소년 교통비 지원 포털(www.gbuspb.kr)에서 회원 가입 → 거주지 확인 → 교통카드 등록 → 지역화폐 등록을 하면 자동으로 신청돼요. 1년 내내 가입할 수 있어요.', '준비물: 본인 또는 대리인의 간편·공동·금융 인증서, 어린이·청소년 본인 교통카드(최대 2장), 지역화폐(어린이는 대리인 지역화폐로 등록 가능).', '경기도이민사회통합지원센터 안내(2024)에 따르면 외국인은 1개월 안에 발급받은 외국인등록사실증명서(동포는 국내거소사실증명서)를 준비해야 해요.']::text[],
       NULL, '연중 수시 가입', NULL, NULL, NULL, '경기교통공사 어린이·청소년 교통비 지원 1577-8459 (평일 09:00~18:00, 점심 12:00~13:00 제외)', '경기도 어린이·청소년 교통비 지원 포털', TRUE
FROM guide WHERE code = 'living-youth-transport' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'en', guide_id, 'Transport fare support for children and teens (Gyeonggi, Siheung Pass+)', 'Children and teens aged 6 to 18 living in Gyeonggi get back up to 60,000 won per quarter of their public transport fares in the capital area. Foreign residents of Gyeonggi are included.', 'For children and teens aged 6 to 18 who live in Gyeonggi. Foreign residents of Gyeonggi and overseas Koreans with a domestic residence report are included.
You get back fares for public transport in the capital area, up to 60,000 won per quarter (240,000 won a year), as local currency.
Since 1 July 2025 Siheung adds more support through Siheung Pass+. Ask the call center about the extra Siheung amount and whether foreigners can get it.
On the Gyeonggi children and teens transport support portal (www.gbuspb.kr): sign up → verify your address → register a transit card → register local currency. After that you apply automatically. You can sign up any time of the year.
You need: an easy, joint or financial certificate of the child or a proxy, the child or teen’s own transit card (up to 2), and local currency (children can use a proxy’s local currency account).
According to the Gyeonggi Immigrant Integration Support Center (2024), foreigners need a certificate of alien registration (overseas Koreans: certificate of domestic residence report) issued within the last month.', ARRAY['For children and teens aged 6 to 18 who live in Gyeonggi. Foreign residents of Gyeonggi and overseas Koreans with a domestic residence report are included.', 'You get back fares for public transport in the capital area, up to 60,000 won per quarter (240,000 won a year), as local currency.', 'Since 1 July 2025 Siheung adds more support through Siheung Pass+. Ask the call center about the extra Siheung amount and whether foreigners can get it.', 'On the Gyeonggi children and teens transport support portal (www.gbuspb.kr): sign up → verify your address → register a transit card → register local currency. After that you apply automatically. You can sign up any time of the year.', 'You need: an easy, joint or financial certificate of the child or a proxy, the child or teen’s own transit card (up to 2), and local currency (children can use a proxy’s local currency account).', 'According to the Gyeonggi Immigrant Integration Support Center (2024), foreigners need a certificate of alien registration (overseas Koreans: certificate of domestic residence report) issued within the last month.']::text[],
       NULL, 'Sign up any time of the year', NULL, NULL, NULL, 'Gyeonggi Transportation Corporation, children and teens fare support 1577-8459 (weekdays 09:00–18:00, closed 12:00–13:00)', '경기도 어린이·청소년 교통비 지원 포털', FALSE
FROM guide WHERE code = 'living-youth-transport' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'zh', guide_id, '儿童·青少年交通费补贴（京畿道·始兴通行证+）', '居住在京畿道的6~18周岁儿童·青少年，乘坐首都圈公共交通的费用每季度最多可返还6万韩元。道内外国人也在补贴范围内。', '对象为居住在京畿道的6~18周岁儿童·青少年。道内外国人及已办理国内居所申报的外国国籍同胞也包括在内。
乘坐首都圈公共交通的费用以地区货币返还，每季度最多6万韩元，每年最多24万韩元。
始兴市自2025年7月1日起通过“始兴通行证+”（시흥패스+）增加补贴。始兴市追加补贴金额及外国人是否适用，请向客服中心确认。
在京畿道儿童·青少年交通费补贴网站（www.gbuspb.kr）注册会员 → 验证居住地 → 登记交通卡 → 登记地区货币后即自动申请。全年均可注册。
准备：本人或代理人的简易·共同·金融认证书、儿童·青少年本人的交通卡（最多2张）、地区货币（儿童可登记代理人的地区货币）。
据京畿道移民社会融合支援中心（2024年）介绍，外国人需准备1个月内开具的外国人登录事实证明书（同胞为国内居所申报事实证明书）。', ARRAY['对象为居住在京畿道的6~18周岁儿童·青少年。道内外国人及已办理国内居所申报的外国国籍同胞也包括在内。', '乘坐首都圈公共交通的费用以地区货币返还，每季度最多6万韩元，每年最多24万韩元。', '始兴市自2025年7月1日起通过“始兴通行证+”（시흥패스+）增加补贴。始兴市追加补贴金额及外国人是否适用，请向客服中心确认。', '在京畿道儿童·青少年交通费补贴网站（www.gbuspb.kr）注册会员 → 验证居住地 → 登记交通卡 → 登记地区货币后即自动申请。全年均可注册。', '准备：本人或代理人的简易·共同·金融认证书、儿童·青少年本人的交通卡（最多2张）、地区货币（儿童可登记代理人的地区货币）。', '据京畿道移民社会融合支援中心（2024年）介绍，外国人需准备1个月内开具的外国人登录事实证明书（同胞为国内居所申报事实证明书）。']::text[],
       NULL, '全年随时注册', NULL, NULL, NULL, '京畿交通公社 儿童·青少年交通费补贴 1577-8459（工作日 09:00~18:00，12:00~13:00 午休）', '경기도 어린이·청소년 교통비 지원 포털', FALSE
FROM guide WHERE code = 'living-youth-transport' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)
SELECT 'vi', guide_id, 'Hỗ trợ phí giao thông cho trẻ em và thanh thiếu niên (Gyeonggi, Siheung Pass+)', 'Trẻ em và thanh thiếu niên 6–18 tuổi sống ở Gyeonggi được hoàn lại tiền đi phương tiện công cộng vùng thủ đô, tối đa 60.000 won mỗi quý. Người nước ngoài sống ở Gyeonggi cũng được hỗ trợ.', 'Đối tượng là trẻ em và thanh thiếu niên 6–18 tuổi sống ở Gyeonggi, gồm cả người nước ngoài trong tỉnh và đồng bào quốc tịch nước ngoài đã khai báo cư trú trong nước.
Tiền đi phương tiện công cộng vùng thủ đô được hoàn lại bằng tiền tệ địa phương, tối đa 60.000 won mỗi quý (240.000 won mỗi năm).
Từ ngày 1/7/2025, Siheung hỗ trợ thêm qua Siheung Pass+. Hãy hỏi tổng đài về số tiền hỗ trợ thêm của Siheung và việc người nước ngoài có được nhận hay không.
Trên cổng hỗ trợ phí giao thông trẻ em và thanh thiếu niên Gyeonggi (www.gbuspb.kr): đăng ký thành viên → xác minh nơi ở → đăng ký thẻ giao thông → đăng ký tiền tệ địa phương, sau đó sẽ tự động được đăng ký nhận. Có thể đăng ký quanh năm.
Cần chuẩn bị: chứng thư đơn giản, chung hoặc tài chính của bản thân hoặc người đại diện, thẻ giao thông của chính trẻ (tối đa 2 thẻ), tiền tệ địa phương (trẻ em có thể dùng tài khoản của người đại diện).
Theo Trung tâm Hỗ trợ Hội nhập Xã hội Người nhập cư Gyeonggi (2024), người nước ngoài cần giấy chứng nhận đăng ký người nước ngoài (đồng bào: giấy chứng nhận khai báo cư trú trong nước) cấp trong vòng 1 tháng.', ARRAY['Đối tượng là trẻ em và thanh thiếu niên 6–18 tuổi sống ở Gyeonggi, gồm cả người nước ngoài trong tỉnh và đồng bào quốc tịch nước ngoài đã khai báo cư trú trong nước.', 'Tiền đi phương tiện công cộng vùng thủ đô được hoàn lại bằng tiền tệ địa phương, tối đa 60.000 won mỗi quý (240.000 won mỗi năm).', 'Từ ngày 1/7/2025, Siheung hỗ trợ thêm qua Siheung Pass+. Hãy hỏi tổng đài về số tiền hỗ trợ thêm của Siheung và việc người nước ngoài có được nhận hay không.', 'Trên cổng hỗ trợ phí giao thông trẻ em và thanh thiếu niên Gyeonggi (www.gbuspb.kr): đăng ký thành viên → xác minh nơi ở → đăng ký thẻ giao thông → đăng ký tiền tệ địa phương, sau đó sẽ tự động được đăng ký nhận. Có thể đăng ký quanh năm.', 'Cần chuẩn bị: chứng thư đơn giản, chung hoặc tài chính của bản thân hoặc người đại diện, thẻ giao thông của chính trẻ (tối đa 2 thẻ), tiền tệ địa phương (trẻ em có thể dùng tài khoản của người đại diện).', 'Theo Trung tâm Hỗ trợ Hội nhập Xã hội Người nhập cư Gyeonggi (2024), người nước ngoài cần giấy chứng nhận đăng ký người nước ngoài (đồng bào: giấy chứng nhận khai báo cư trú trong nước) cấp trong vòng 1 tháng.']::text[],
       NULL, 'Đăng ký quanh năm', NULL, NULL, NULL, 'Tổng công ty Giao thông Gyeonggi, hỗ trợ phí giao thông trẻ em 1577-8459 (ngày thường 09:00–18:00, nghỉ 12:00–13:00)', '경기도 어린이·청소년 교통비 지원 포털', FALSE
FROM guide WHERE code = 'living-youth-transport' ON CONFLICT (language, guide_id) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 1, '경기도 어린이·청소년 교통비 지원 포털 — 사업안내', 'https://www.gbuspb.kr/child/user/svc/userServiceInfoView.do' FROM guide WHERE code = 'living-youth-transport'
ON CONFLICT (guide_id, sort_order) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 2, '경기도 어린이·청소년 교통비 지원 포털 (시흥패스+ 안내)', 'https://www.gbuspb.kr/childUserMain.do' FROM guide WHERE code = 'living-youth-transport'
ON CONFLICT (guide_id, sort_order) DO NOTHING;
INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, 3, '경기도이민사회통합지원센터 — 경기도 어린이청소년 교통비 지원 신청 안내 (2024)', 'https://gmis.or.kr/ambassador/8?page=4' FROM guide WHERE code = 'living-youth-transport'
ON CONFLICT (guide_id, sort_order) DO NOTHING;

