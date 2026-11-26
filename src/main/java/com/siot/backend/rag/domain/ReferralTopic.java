package com.siot.backend.rag.domain;

/** CB-11: 챗봇이 판단하지 않고 담당 기관으로 안내하는 질문 종류 (chat_message.referral_topics) */
public enum ReferralTopic {
    /** 비자 발급·체류 연장·자격 변경이 가능한지 */
    VISA,
    /** 불법·처벌·소송 등 법률 판단 */
    LEGAL,
    /** 증상·진단·약 등 의료 판단 */
    MEDICAL,
    /** 내 경우 세율·환급액 등 개인 세금 판단 */
    TAX,
    /** 해고·임금 체불 등 노동 분쟁 판단 */
    LABOR,
    /** 일하다 다친 경우 산업재해 인정 여부 */
    WORK_INJURY
}
