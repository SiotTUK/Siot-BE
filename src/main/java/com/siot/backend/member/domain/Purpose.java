package com.siot.backend.member.domain;

/** F04 체류 목적 (단일 선택, 건너뛰면 저장하지 않고 OTHER 로 취급) */
public enum Purpose {
    /** 공부 (D-2·D-4) */
    STUDY,
    /** 일 (E-1~E-10·H-2·D-3·D-7~D-10) */
    WORK,
    /** 가족 (F-6·F-1·F-3·F-2) */
    FAMILY,
    /** 기타·모름 (F-4·F-5·H-1·G-1 등) */
    OTHER
}
