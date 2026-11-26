package com.siot.backend.translation.bill;

import com.siot.backend.translation.BillReading;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * RUL-07 경고 규칙. AI 가 아니라 서버가 서식 종류·기한으로 붙인다.
 * <ul>
 *     <li>지방세 서식(납세고지서·독촉장·체납액 고지서): 납부지연가산세 3%, 45만 원 이상 월 0.66% (최대 60개월)</li>
 *     <li>독촉장: 기한까지 안 내면 압류될 수 있음</li>
 *     <li>독촉장·체납액 고지서: 지방세 체납 시 체류기간 연장 제한</li>
 *     <li>납기 내 기한이 오늘(서울)보다 이전: 기한이 지났다는 안내</li>
 * </ul>
 * 공과금·보험료(UTILITY)·기타 고지서는 연체 규정이 달라 가산세 경고를 붙이지 않는다.
 */
final class BillWarnings {

    record Warning(String code, String message) {
    }

    private BillWarnings() {
    }

    static List<Warning> of(BillReading reading, String language, LocalDate today) {
        List<String> codes = new ArrayList<>();
        String type = reading.documentType();
        if (BillReading.LOCAL_TAX_TYPES.contains(type)) {
            codes.add(BillMessages.LATE_PENALTY);
        }
        if (BillReading.DEMAND_NOTICE.equals(type)) {
            codes.add(BillMessages.SEIZURE);
        }
        if (BillReading.DEMAND_NOTICE.equals(type) || BillReading.ARREARS_NOTICE.equals(type)) {
            codes.add(BillMessages.STAY_EXTENSION);
        }
        if (reading.dueDate() != null && reading.dueDate().isBefore(today)) {
            codes.add(BillMessages.OVERDUE);
        }
        return codes.stream()
                .map(code -> new Warning(code, BillMessages.warning(code, language, reading.dueDate())))
                .toList();
    }
}
