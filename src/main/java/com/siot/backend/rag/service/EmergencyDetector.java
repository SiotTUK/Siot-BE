package com.siot.backend.rag.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * CB-09: 화재·부상·범죄 등 응급 관련 단어가 있으면 답변 맨 위에 119/112 를 보여 준다.
 * 놓치는 것보다 한 번 더 보여 주는 편이 낫기 때문에 넓게 잡는다 (예: "화재보험" 도 해당).
 */
public final class EmergencyDetector {

    /** 한국어·중국어·베트남어: 부분 일치 */
    private static final List<String> PHRASES = List.of(
            // ko
            "화재", "불이 났", "불났", "불이났", "연기가", "가스 냄새", "가스가 새",
            "다쳤", "부상", "피가 나", "피를 흘", "출혈", "응급", "구급차", "쓰러", "숨을 못", "의식이 없", "심장마비",
            "범죄", "강도", "폭행", "때려", "맞았", "성폭력", "성추행", "납치", "협박", "도둑", "스토킹", "살려",
            // zh
            "火灾", "着火", "失火", "起火", "受伤", "流血", "急救", "救护车", "昏倒", "晕倒", "犯罪", "抢劫", "殴打", "被打",
            "性侵", "绑架", "威胁", "小偷", "救命",
            // vi
            "cháy", "hỏa hoạn", "bị thương", "chảy máu", "cấp cứu", "xe cứu thương", "ngất", "bất tỉnh", "tội phạm",
            "cướp", "hành hung", "bị đánh", "xâm hại", "bắt cóc", "đe dọa", "trộm", "cứu tôi", "cứu với");

    /** 영어: 단어 경계 ("fired" 는 해당하지 않도록) */
    private static final Pattern ENGLISH = Pattern.compile(
            "\\b(fire|on fire|smoke|gas leak|injured|injury|hurt|bleeding|emergency|ambulance|unconscious|"
                    + "heart attack|crime|robbery|robbed|assault(ed)?|attacked|beaten|rape|kidnap(ped)?|threat(ened)?|"
                    + "stalking|stolen|thief|help me)\\b",
            Pattern.CASE_INSENSITIVE);

    private EmergencyDetector() {
    }

    public static boolean isEmergency(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String phrase : PHRASES) {
            if (lower.contains(phrase)) {
                return true;
            }
        }
        return ENGLISH.matcher(text).find();
    }
}
