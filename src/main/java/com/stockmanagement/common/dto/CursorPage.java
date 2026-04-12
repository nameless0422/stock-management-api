package com.stockmanagement.common.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.function.Function;

/**
 * 커서 기반 페이지네이션 응답 래퍼.
 *
 * <p>오프셋 방식({@code LIMIT ? OFFSET ?})의 대안으로, 페이지 번호가 클수록 성능이 저하되는
 * 문제를 해결한다. {@code WHERE id < :lastId ORDER BY id DESC LIMIT ?}로 앞 레코드를
 * 스캔하지 않고 커서 위치부터 즉시 접근한다.
 *
 * <p>사용 방법: 클라이언트는 첫 요청에 {@code lastId} 없이 호출하고, 이후 응답의
 * {@code nextCursor}를 다음 요청의 {@code lastId}로 전달한다.
 * {@code hasNext=false}이면 마지막 페이지다.
 *
 * @param <T> 응답 항목 타입
 */
@Getter
@RequiredArgsConstructor
public class CursorPage<T> {

    /** 이번 페이지 데이터 목록. */
    private final List<T> content;

    /**
     * 다음 페이지 조회를 위한 커서 값 (마지막 항목의 ID).
     * {@code hasNext=false}이면 {@code null}.
     */
    private final Long nextCursor;

    /** 다음 페이지 존재 여부. */
    private final boolean hasNext;

    /**
     * 커서 페이지를 생성한다.
     *
     * <p>리포지토리에서 {@code size + 1}건을 조회하고, {@code size + 1}번째 항목이
     * 존재하면 다음 페이지가 있다고 판단하여 해당 항목을 응답에서 제외한다.
     *
     * @param items       리포지토리에서 조회한 항목 목록 (최대 {@code size + 1}건)
     * @param size        클라이언트가 요청한 페이지 크기
     * @param idExtractor 항목에서 커서(ID)를 추출하는 함수
     */
    public static <T> CursorPage<T> of(List<T> items, int size, Function<T, Long> idExtractor) {
        boolean hasNext = items.size() > size;
        List<T> content = hasNext ? items.subList(0, size) : items;
        Long nextCursor = hasNext ? idExtractor.apply(content.get(content.size() - 1)) : null;
        return new CursorPage<>(content, nextCursor, hasNext);
    }
}
