package com.siot.backend.checklist.domain;

import com.siot.backend.global.common.SupportedLanguages;

import java.util.Map;

/**
 * 체크리스트·생활 변화 할 일이 회원에게 보이는 이유 (BE #42, 시안 v2 '자차가 있어서 보여요').
 * 표시 조건(ChecklistService 의 규칙표)이 본 온보딩 답이다. 처음 입국·거주 중 구분만으로 보이거나
 * 누구에게나 보이는 항목은 이유가 없다(null = 공통).
 * 응답에는 코드(reason)와 요청 언어의 짧은 문구(reasonLabel)를 함께 준다. 코드는 프론트엔드가 분기에 쓰므로 바꾸지 않는다.
 * 문구는 온보딩 선택지(자차가 있어요 → 자차가 있어서)와 같은 말을 쓴다.
 */
public enum ChecklistDisplayReason {
    /** 거주 정보: 자차 (차·오토바이) */
    CAR("자차가 있어서", "Because you own a vehicle", "因为您有自己的车", "Vì bạn có xe riêng"),
    /** 거주 정보: 자녀 */
    KIDS("자녀가 있어서", "Because you have children", "因为您有子女", "Vì bạn có con"),
    /** 거주 정보: 자가 (집·토지) */
    HOUSE("자가라서", "Because you own property", "因为您有自己的房产", "Vì bạn có nhà đất riêng"),
    /** 거주 정보: 재외동포 (F-4) */
    F4("재외동포(F-4)라서", "Because you are an overseas Korean (F-4)", "因为您是在外同胞（F-4）", "Vì bạn là kiều bào (F-4)"),
    /** 체류 만료일을 입력함 (CL-05 체류기간 연장) */
    STAY_EXPIRY("체류 만료일을 입력해서", "Because you entered your stay expiry date", "因为您填写了滞留到期日",
            "Vì bạn đã nhập ngày hết hạn lưu trú"),
    /** 체류 목적: 공부 */
    PURPOSE_STUDY("공부하러 와서", "Because you came to study", "因为您来学习", "Vì bạn đến để học"),
    /** 체류 목적: 일 */
    PURPOSE_WORK("일하러 와서", "Because you came to work", "因为您来工作", "Vì bạn đến để làm việc"),
    /** 체류 목적: 가족 */
    PURPOSE_FAMILY("가족과 살아서", "Because you live with your family", "因为您和家人一起生活", "Vì bạn sống cùng gia đình"),
    /** 체류 목적: 기타·모름 (목적을 고르지 않은 회원도 여기) */
    PURPOSE_OTHER("체류 목적이 기타·모름이라서", "Because your purpose of stay is other or not sure",
            "因为您的滞留目的是其他或不确定", "Vì mục đích lưu trú của bạn là khác hoặc chưa rõ");

    private final Map<String, String> labels;

    ChecklistDisplayReason(String ko, String en, String zh, String vi) {
        this.labels = Map.of("ko", ko, "en", en, "zh", zh, "vi", vi);
    }

    /** 요청 언어의 문구 (지원하지 않는 언어면 한국어) */
    public String label(String language) {
        return labels.get(SupportedLanguages.orDefault(language));
    }
}
