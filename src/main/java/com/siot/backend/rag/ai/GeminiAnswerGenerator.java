package com.siot.backend.rag.ai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gemini 로 답변 생성. 검색된 조각만 근거로 쓰고, 근거에 없으면 {@link AnswerGenerator#NO_ANSWER} 를 돌려받는다.
 */
public class GeminiAnswerGenerator implements AnswerGenerator {

    private static final Map<String, String> LANGUAGE_NAMES = Map.of(
            "ko", "Korean", "en", "English", "zh", "Simplified Chinese", "vi", "Vietnamese");

    static final String SYSTEM_PROMPT = """
            You are the assistant of "Welcome Siheung", a service that helps foreign residents of Siheung, Korea.

            Rules:
            1. Answer ONLY with information found in the reference passages provided by the user message.
               Each passage starts with a header line naming its document and pages.
               Do not add facts, numbers, fees, deadlines or links that are not in the passages.
            2. If the passages do not contain the answer, reply with exactly: NO_ANSWER
            3. Write the whole answer in %s, even though the passages are in Korean.
               Translate the facts you use; keep phone numbers, amounts and dates exactly as written.
               A Korean office or website name may follow its translated name in parentheses.
            4. Do not judge an individual's visa eligibility, legal, medical, tax or labor situation. For such
               questions, explain what the passages say and tell the person to confirm with the responsible office.
               The app shows that office's phone number separately.
            5. Be concise and practical: short paragraphs or bullet points, plain words.
            6. The app shows the sources under your answer. Do not write citation markers such as [1] or [1, 2],
               passage numbers, the word "passage", or any internal ID. If you need to say where something is
               written, name the document (and page) as given in the passage header.
            7. Earlier conversation turns may be given only so you understand what the question refers to.
               Facts in your answer must still come from the reference passages.
            8. The reference passages were retrieved for the current question only. Never present them as the
               basis of an earlier answer, and do not guess what an earlier answer was based on.
            """;

    /** CB-15: 한국어가 아닌 언어로 답해야 하는데 한국어로 답했을 때 한 번 더 보내는 요청 */
    static final String RETRY_IN_LANGUAGE = "Your answer was written in Korean. Rewrite the same answer in %s only.";

    private final ChatModel model;

    public GeminiAnswerGenerator(ChatModel model) {
        this.model = model;
    }

    @Override
    public String generate(String question, String language, List<RetrievedChunk> context,
                           List<ChatTurn> history) {
        String answerLanguage = LANGUAGE_NAMES.getOrDefault(language, "Korean");
        StringBuilder passages = new StringBuilder();
        // CB-17: 번호("[1]")를 붙이면 모델이 답변에 그대로 따라 쓰므로 문서 이름·쪽으로 구분한다
        for (RetrievedChunk chunk : context) {
            passages.append(Citations.label(chunk)).append('\n')
                    .append(chunk.content().strip()).append("\n\n");
        }
        String userText = "Reference passages:\n\n" + passages + "Question:\n" + question
                + "\n\n(Answer in " + answerLanguage + ".)";

        // CB-07: 앞선 대화는 실제 대화 메시지로 넘기고, 마지막 메시지에 근거 조각과 현재 질문을 담는다
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(SYSTEM_PROMPT.formatted(answerLanguage)));
        // 근거 없음·오류였던 턴(answer == null)을 "NO_ANSWER" 라는 AI 답변으로 넣으면 모델이 그 응답을 따라 하게 되고,
        // 일시적인 AI 오류도 "답 없음" 처럼 보인다. 질문만 다음 사용자 메시지 앞에 붙인다 (같은 역할 메시지가 연속되지 않게).
        StringBuilder unanswered = new StringBuilder();
        for (ChatTurn turn : history) {
            if (turn.answer() == null) {
                unanswered.append("- ").append(turn.question()).append('\n');
                continue;
            }
            messages.add(UserMessage.from(withUnanswered(unanswered, turn.question())));
            messages.add(AiMessage.from(turn.answer()));
        }
        messages.add(UserMessage.from(withUnanswered(unanswered, userText)));
        try {
            String answer = model.chat(messages).aiMessage().text();
            if (answer == null) {
                return NO_ANSWER;
            }
            answer = answer.strip();
            // CB-15: 근거가 한국어라 한국어로 답하는 경우가 있어, 한 번만 같은 답을 질문 언어로 다시 받는다
            if (!"ko".equals(language) && !answer.startsWith(NO_ANSWER) && isMostlyKorean(answer)) {
                messages.add(AiMessage.from(answer));
                messages.add(UserMessage.from(RETRY_IN_LANGUAGE.formatted(answerLanguage)));
                String retried = model.chat(messages).aiMessage().text();
                if (retried != null && !retried.isBlank()) {
                    answer = retried.strip();
                }
            }
            // 그래도 번호가 새어 나오면 지운다 (사용자는 그 번호가 무엇인지 알 수 없다)
            return Citations.stripPassageNumbers(answer);
        } catch (RuntimeException e) {
            throw new AiUnavailableException("Gemini chat failed", e);
        }
    }

    /** 글자(한글·한자·라틴) 중 한글이 절반을 넘으면 한국어 답으로 본다 (괄호 속 기관 이름 정도로는 넘지 않음) */
    static boolean isMostlyKorean(String text) {
        int hangul = 0;
        int letters = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            Character.UnicodeScript script = Character.UnicodeScript.of(cp);
            if (script == Character.UnicodeScript.HANGUL) {
                hangul++;
                letters++;
            } else if (script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.LATIN) {
                letters++;
            }
        }
        return letters > 0 && hangul * 2 > letters;
    }

    /** 답하지 못한 앞 질문들을 맥락으로 앞에 붙이고 비운다 */
    private static String withUnanswered(StringBuilder unanswered, String text) {
        if (unanswered.isEmpty()) {
            return text;
        }
        String result = "Earlier questions in this conversation (not answered):\n" + unanswered + "\n" + text;
        unanswered.setLength(0);
        return result;
    }
}
