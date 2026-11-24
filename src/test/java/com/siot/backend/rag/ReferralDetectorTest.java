package com.siot.backend.rag;

import com.siot.backend.rag.domain.ReferralTopic;
import com.siot.backend.rag.service.ReferralDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ReferralDetectorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "제 상황이면 비자 연장 가능할까요?",
            "E-9 비자에서 F-2 로 자격 변경 될까요?",
            "Can I get a work visa with my degree?",
            "Am I eligible for permanent residence?",
            "我可以延期签证吗？",
            "Tôi có thể gia hạn thị thực không?",
            "제 경우 비자 연장 신청하면 거절될까요?",
            "Will I be approved for a visa if I apply online?"
    })
    void detectsVisaJudgment(String text) {
        assertThat(ReferralDetector.detect(text)).contains(ReferralTopic.VISA);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "사장님이 월급을 안 주는데 고소할 수 있나요?",
            "이거 불법인가요?",
            "Is it illegal to work part-time on a student visa?",
            "我需要请律师吗？",
            "Tôi có bị xử phạt không?"
    })
    void detectsLegalJudgment(String text) {
        assertThat(ReferralDetector.detect(text)).contains(ReferralTopic.LEGAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "열이 나고 기침이 나는데 무슨 병인가요?",
            "이 약 먹어도 되나요?",
            "I have a fever and headache, what should I take?",
            "我头痛，应该吃什么药？",
            "Tôi bị đau bụng, có nên uống thuốc không?"
    })
    void detectsMedicalJudgment(String text) {
        assertThat(ReferralDetector.detect(text)).contains(ReferralTopic.MEDICAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // CB01-TAX-09·10
            "저는 단일세율이랑 일반세율 중에 뭐가 더 유리한가요?",
            "For me, which is better: the flat rate or the regular rate?",
            "对我来说，单一税率和一般税率哪个更有利？",
            "Đối với tôi, thuế suất cố định hay thuế suất thông thường có lợi hơn?",
            "제 경우 이번 연말정산에서 환급을 얼마나 받을까요?",
            "In my case, how much refund will I get from this year-end settlement?",
            "我这次年终结算能退多少税？",
            "Trường hợp của tôi, đợt quyết toán này tôi được hoàn bao nhiêu thuế?"
    })
    void detectsTaxJudgment(String text) {
        assertThat(ReferralDetector.detect(text)).contains(ReferralTopic.TAX);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // CB01-WORK-09
            "사장님이 갑자기 나가라고 했는데 이거 부당해고인가요?",
            "My boss suddenly told me to leave. Is this unfair dismissal?",
            "老板突然叫我走人，这是不当解雇吗？",
            "Ông chủ đột nhiên bảo tôi nghỉ việc, đây có phải sa thải trái phép không?",
            "3달째 월급을 못 받았어요",
            "My employer hasn't paid me for two months"
    })
    void detectsLaborJudgment(String text) {
        assertThat(ReferralDetector.detect(text)).contains(ReferralTopic.LABOR);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // CB01-WORK-10
            "일하다가 다쳤는데 산재로 인정받을 수 있을까요?",
            "I got hurt at work. Can it be recognized as an industrial accident?",
            "我工作时受伤了，能被认定为工伤吗？",
            "Tôi bị thương khi làm việc, có được công nhận là tai nạn lao động không?"
    })
    void detectsWorkInjury(String text) {
        assertThat(ReferralDetector.detect(text)).contains(ReferralTopic.WORK_INJURY);
    }

    /** CB-01 세금·노동 정보 질문 (TAX-01~08, WORK-01~08 일부) 에는 붙이지 않는다 */
    @ParameterizedTest
    @ValueSource(strings = {
            "주민세는 언제, 얼마나 내나요?",
            "When do I pay the resident tax, and how much is it?",
            "居民税什么时候交，交多少？",
            "Thuế cư trú nộp khi nào và bao nhiêu?",
            "외국인 근로자는 19% 단일세율을 적용받을 수 있나요?",
            "Can foreign workers apply the 19% flat tax rate?",
            "外国劳动者可以适用19%单一税率吗？",
            "Người lao động nước ngoài có được áp dụng thuế suất cố định 19% không?",
            "외국인도 거주자가 될 수 있나요? 기준이 뭔가요?",
            "Can a foreigner be a tax resident? What is the standard?",
            "연말정산은 언제 하나요?",
            "회사를 그만두면 연말정산은 어떻게 되나요?",
            "What happens to my year-end tax settlement if I quit my job?",
            "辞职的话年终结算怎么办？",
            "Nếu nghỉ việc thì quyết toán thuế cuối năm thế nào?",
            "E-9 근로자는 회사를 몇 번까지 옮길 수 있나요?",
            "Lao động E-9 được đổi nơi làm việc tối đa bao nhiêu lần?",
            "퇴직금은 어떻게 보장되나요? 출국만기보험이 뭔가요?",
            "How is my severance pay guaranteed? What is departure guarantee insurance?",
            "연장근로는 일주일에 최대 몇 시간이고 수당은 얼마인가요?",
            "What is the maximum overtime per week, and how much is overtime pay?"
    })
    void ignoresTaxAndWorkInformationQuestions(String text) {
        assertThat(ReferralDetector.detect(text)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "이 임대차 계약서에 그대로 서명해도 괜찮을까요?",
            "Should I sign this contract?",
            "这份租房合同我直接签字可以吗？",
            "Tôi ký hợp đồng thuê nhà này luôn có được không?",
            "Chủ nhà không trả lại tiền đặt cọc, nếu kiện thì tôi có thắng không?"
    })
    void detectsContractAndLawsuitJudgment(String text) {
        assertThat(ReferralDetector.detect(text)).contains(ReferralTopic.LEGAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Will my visa be extended?", "我的签证能延长吗？", "以我的情况能换永住权吗？"})
    void detectsVisaJudgmentWithoutFirstPersonVerb(String text) {
        assertThat(ReferralDetector.detect(text)).contains(ReferralTopic.VISA);
    }

    @Test
    void detectsSeveralTopicsInOrder() {
        assertThat(ReferralDetector.detect("불법체류면 비자 받을 수 있나요? 처벌받나요?"))
                .containsExactly(ReferralTopic.VISA, ReferralTopic.LEGAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "외국인등록은 어떻게 하나요?",
            "자동차세는 언제 내요?",
            "How do I extend my stay?",
            "비자 연장 서류는 뭐가 필요해요?",
            "체류기간 연장 신청은 어디서 할 수 있나요?",
            "비자 연장 신청은 언제 하면 될까요?",
            "비자 연장할 때 서류는 뭐가 있을까요?",
            "체류기간 연장 온라인 신청 가능한가요?",
            "비자 연장은 온라인으로 할 수 있나요?",
            "Can I apply for a visa extension online?",
            "驾照可以延期吗？",
            "운전면허 연장 가능해요?",
            "Can I get an extension on my phone bill?",
            "怎么办理外国人登录？",
            "Điều kiện tham gia sự kiện là gì?",
            "Nếu không đủ điều kiện thì sao?",
            "Đăng ký hợp đồng điện thoại online được không?",
            "Can I sign up for the Korean class online?",
            "哪里能买到感冒的食物吗",
            ""
    })
    void ignoresInformationQuestions(String text) {
        assertThat(ReferralDetector.detect(text)).isEmpty();
    }
}
