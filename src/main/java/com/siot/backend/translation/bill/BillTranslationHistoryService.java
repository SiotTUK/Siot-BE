package com.siot.backend.translation.bill;

import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * RUL-07 최근 고지서 번역 저장·조회와 납부 마감 알림 설정.
 * <ul>
 *   <li>읽을 수 있었던 결과(readable=true)만 저장한다. 사진은 저장하지 않고, 결과는 이미 개인정보를 가린 값이다</li>
 *   <li>회원당 최근 {@value #MAX_RECENT}개만 남긴다. 다만 마감 알림이 남은(기한 전) 번역은 알림이 갈 때까지 지우지 않는다</li>
 *   <li>마감 알림은 납부 기한이 있고 지나지 않은 번역만 켤 수 있다. 알림은 {@link BillDueNotifier} 가 만든다</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BillTranslationHistoryService {

    static final int MAX_RECENT = 10;

    private final BillTranslationRepository repository;
    private final JsonMapper jsonMapper;
    /** 한국 시간 (ClockConfig) */
    private final Clock clock;

    /**
     * 번역 결과를 최근 번역으로 저장한다. 읽지 못한 결과는 저장하지 않고 그대로 돌려준다.
     *
     * @param remind true 면 마감 알림도 켠다. 기한이 없거나 지났으면 오류 없이 꺼 둔다 (번역 자체는 성공했으므로)
     */
    @Transactional
    public BillTranslationResponse save(Long userId, BillTranslationResponse response, boolean remind) {
        if (!response.readable()) {
            return response;
        }
        BillTranslation saved = repository.save(BillTranslation.of(userId, response,
                write(response.withSaved(null, false, null))));
        LocalDate today = LocalDate.now(clock);
        if (remind && saved.canRemind(today)) {
            saved.changeReminder(true);
        }
        repository.flush(); // created_at 을 응답에 담는다
        prune(userId, today);
        return response.withSaved(saved.getId(), saved.isReminder(), saved.getCreatedAt());
    }

    /** 최근 번역 목록 (새것부터, 최대 {@value #MAX_RECENT}개) */
    public List<BillTranslationListItem> list(Long userId) {
        LocalDate today = LocalDate.now(clock);
        return repository.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .limit(MAX_RECENT)
                .map(translation -> item(translation, today))
                .toList();
    }

    /** 저장한 번역 결과 전체 */
    public BillTranslationResponse get(Long userId, Long id) {
        return response(find(userId, id));
    }

    /** 납부 마감 알림 켜기·끄기. 끄기는 언제나 되고, 켜기는 기한이 있고 지나지 않았을 때만 */
    @Transactional
    public BillTranslationResponse changeReminder(Long userId, Long id, boolean enabled) {
        BillTranslation translation = find(userId, id);
        if (enabled && !translation.canRemind(LocalDate.now(clock))) {
            throw new BusinessException(ErrorCode.BILL_REMINDER_UNAVAILABLE);
        }
        translation.changeReminder(enabled);
        return response(translation);
    }

    /** 번역 기록 삭제. 이미 만든 알림은 남고 번역 연결만 끊긴다 (notification.bill_translation_id SET NULL) */
    @Transactional
    public void delete(Long userId, Long id) {
        repository.delete(find(userId, id));
    }

    private BillTranslation find(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BILL_TRANSLATION_NOT_FOUND));
    }

    /** 최근 {@value #MAX_RECENT}개 밖의 번역을 지운다. 기한 전 마감 알림이 켜진 것은 남긴다 */
    private void prune(Long userId, LocalDate today) {
        List<BillTranslation> old = repository.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .skip(MAX_RECENT)
                .filter(translation -> !(translation.isReminder() && translation.canRemind(today)))
                .toList();
        if (!old.isEmpty()) {
            repository.deleteAll(old);
        }
    }

    private BillTranslationListItem item(BillTranslation translation, LocalDate today) {
        BillTranslationResponse.Summary summary = read(translation).summary();
        return new BillTranslationListItem(translation.getId(), translation.getLanguage(),
                translation.getDocumentType(), translation.getIssuer(), translation.getTaxType(),
                summary == null ? null : summary.what(), translation.getAmount(), translation.getDueDate(),
                translation.isReminder(), translation.canRemind(today), translation.getCreatedAt());
    }

    private BillTranslationResponse response(BillTranslation translation) {
        return read(translation).withSaved(translation.getId(), translation.isReminder(), translation.getCreatedAt());
    }

    private String write(BillTranslationResponse response) {
        try {
            return jsonMapper.writeValueAsString(response);
        } catch (JacksonException e) {
            throw new IllegalStateException("Bill translation result could not be serialized", e);
        }
    }

    private BillTranslationResponse read(BillTranslation translation) {
        try {
            return jsonMapper.readValue(translation.getResult(), BillTranslationResponse.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("Bill translation " + translation.getId() + " result is broken", e);
        }
    }
}
