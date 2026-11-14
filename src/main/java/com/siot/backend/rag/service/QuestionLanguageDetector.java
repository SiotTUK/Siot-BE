package com.siot.backend.rag.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * CB-15: 질문 언어(ko / en / zh / vi)를 글자 종류로 판별한다. 판별하지 못하면 빈 값 → 화면(대화) 언어를 쓴다.
 * <ul>
 *   <li>한글이 가장 많으면 ko, 한자가 가장 많으면 zh (일본어 가나가 섞이면 판별하지 않음)</li>
 *   <li>라틴 문자가 가장 많을 때: 베트남어 성조·모음 글자가 있으면 vi.
 *       성조 없이 쓴 베트남어 단어(toi, khong ...)가 있으면 vi, 영어 기능어(how, what, the ...)가 있으면 en.
 *       둘 다 없으면 판별하지 않는다 (성조 없이 쓴 베트남어를 영어로 잘못 판별하지 않도록)</li>
 * </ul>
 * 개인정보를 가리기 전의 원문으로 판별한다 (가린 자리에 들어가는 "[전화번호]" 같은 한국어 표시 때문에).
 */
public final class QuestionLanguageDetector {

    /** 베트남어에만 쓰는 모음·성조 글자 (NFC 기준) */
    private static final String VIETNAMESE_LETTERS =
            "ăâđêôơưàảãáạằẳẵắặầẩẫấậèẻẽéẹềểễếệìỉĩíịòỏõóọồổỗốộờởỡớợùủũúụừửữứựỳỷỹýỵ";

    /** 성조 없이 쓴 베트남어에 자주 나오는 단어 */
    private static final Set<String> VIETNAMESE_WORDS = Set.of(
            "toi", "khong", "duoc", "nhu", "nao", "dau", "bao", "nhieu", "cua", "xin", "chao", "minh",
            "gi", "sao", "phai", "lam", "nguoi", "nuoc", "ngoai", "giay", "dang", "ky");

    /** 영어 기능어 */
    private static final Set<String> ENGLISH_WORDS = Set.of(
            "the", "is", "are", "am", "how", "what", "where", "when", "why", "who", "which", "can", "could",
            "do", "does", "did", "i", "my", "me", "you", "your", "to", "for", "of", "in", "on", "with", "need",
            "want", "should", "have", "has", "get", "there", "this", "that", "it", "an", "and", "or", "not",
            "please", "about", "if", "will", "would");

    private static final Pattern NON_LETTERS = Pattern.compile("[^\\p{IsLatin}]+");

    private QuestionLanguageDetector() {
    }

    public static Optional<String> detect(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC);
        int hangul = 0;
        int han = 0;
        int kana = 0;
        int latin = 0;
        boolean vietnameseLetter = false;
        for (int i = 0; i < normalized.length(); ) {
            int cp = normalized.codePointAt(i);
            i += Character.charCount(cp);
            switch (Character.UnicodeScript.of(cp)) {
                case HANGUL -> hangul++;
                case HAN -> han++;
                case HIRAGANA, KATAKANA -> kana++;
                case LATIN -> {
                    latin++;
                    if (VIETNAMESE_LETTERS.indexOf(Character.toLowerCase(cp)) >= 0) {
                        vietnameseLetter = true;
                    }
                }
                default -> {
                }
            }
        }
        if (kana > 0) {
            return Optional.empty();   // 일본어 (지원하지 않음)
        }
        if (hangul == 0 && han == 0 && latin == 0) {
            return Optional.empty();   // 숫자·기호만
        }
        if (hangul >= han && hangul >= latin) {
            return Optional.of("ko");
        }
        if (han >= latin) {
            return Optional.of("zh");
        }
        if (vietnameseLetter) {
            return Optional.of("vi");
        }
        String[] words = NON_LETTERS.split(normalized.toLowerCase(Locale.ROOT));
        boolean english = false;
        for (String word : words) {
            if (VIETNAMESE_WORDS.contains(word)) {
                return Optional.of("vi");
            }
            english |= ENGLISH_WORDS.contains(word);
        }
        return english ? Optional.of("en") : Optional.empty();
    }
}
