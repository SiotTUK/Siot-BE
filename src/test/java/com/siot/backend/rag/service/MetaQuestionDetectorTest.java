package com.siot.backend.rag.service;

import com.siot.backend.rag.service.MetaQuestionDetector.MetaQuestion;
import com.siot.backend.rag.service.MetaQuestionDetector.Type;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** 메타 질문 회귀 세트: 대화 기록·과거 답변·근거·설명 질문은 검색하지 않고, 일반 질문은 그대로 검색한다 */
class MetaQuestionDetectorTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            맨 처음 질문이 뭐야?                                  | PAST_QUESTION | 1
            내가 처음 물어본 게 뭐야?                             | PAST_QUESTION | 1
            두 번째 질문은 뭐였어?                                | PAST_QUESTION | 2
            3번째 질문이 뭐였지?                                  | PAST_QUESTION | 3
            내가 뭐 물어봤어?                                     | PAST_QUESTION | 0
            첫 번째 질문에 뭐라고 답했어?                         | PAST_ANSWER   | 1
            첫 번째 답변은 뭐였어?                                | PAST_ANSWER   | 1
            아까 뭐라고 했어?                                     | PAST_ANSWER   | 0
            첫 번째 질문의 답변 근거는 뭐야?                      | SOURCE        | 1
            첫 번째 답변의 출처는?                                | SOURCE        | 1
            아까 알려준 내용은 어디에 나와 있어?                  | SOURCE        | 0
            그 자료의 몇 페이지야?                                | SOURCE        | 0
            근거는?                                               | SOURCE        | 0
            출처가 어디야?                                        | SOURCE        | 0
            두 번째 답변은 어떤 자료를 참고했어?                  | SOURCE        | 2
            왜 그렇게 생각했어?                                   | EXPLAIN       | 0
            왜 그렇게 답했어?                                     | EXPLAIN       | 0
            첫 번째 답변에서 종량제 봉투를 언급한 이유는?         | EXPLAIN       | 1
            무슨 근거로 그렇게 말했어?                            | EXPLAIN       | 0
            What was my first question?                           | PAST_QUESTION | 1
            What did you say?                                     | PAST_ANSWER   | 0
            What was your second answer?                          | PAST_ANSWER   | 2
            What is the source of your first answer?              | SOURCE        | 1
            Where did you get that from?                          | SOURCE        | 0
            Sources?                                              | SOURCE        | 0
            Why did you say that?                                 | EXPLAIN       | 0
            我的第一个问题是什么？                                | PAST_QUESTION | 1
            你刚才说了什么？                                      | PAST_ANSWER   | 0
            第一个回答的依据是什么？                              | SOURCE        | 1
            你为什么这么说？                                      | EXPLAIN       | 0
            Câu hỏi đầu tiên của tôi là gì?                       | PAST_QUESTION | 1
            Bạn đã trả lời gì?                                    | PAST_ANSWER   | 0
            Câu trả lời đầu tiên lấy nguồn từ đâu?                | SOURCE        | 1
            Tại sao bạn lại nói vậy?                              | EXPLAIN       | 0
            """)
    void detectsMetaQuestions(String question, Type type, int turn) {
        assertThat(MetaQuestionDetector.detect(question)).contains(new MetaQuestion(type, turn));
    }

    /** 일반 질문을 메타 질문으로 잘못 보면 답을 받지 못한다 */
    @ParameterizedTest
    @ValueSource(strings = {
            "쓰레기는 어떻게 버리나요?",
            "나 학생비자인데 도중에 결혼하면 어떻게 해?",
            "투명 페트병은 어떻게 버려?",
            "외국인등록 근거 법령이 뭐야?",
            "제가 받을 수 있는 지원금의 법적 근거가 뭐예요?",
            "처음 한국에 왔는데 외국인등록은 어디서 해요?",
            "자주 묻는 질문은 뭐야?",
            "버스 시간표는 어디에 나와 있어?",
            "비자 연장은 왜 그렇게 오래 걸려요?",
            "여권 사본은 몇 장 필요해요?",
            "Can you tell me what evidence I need for a visa extension?",
            "Where is the immigration office?",
            "What documents do I need for my first visa?",
            "签证延期需要什么材料？",
            "Tôi cần giấy tờ gì để gia hạn visa?"
    })
    void leavesOrdinaryQuestionsToSearch(String question) {
        assertThat(MetaQuestionDetector.detect(question)).isEmpty();
    }
}
