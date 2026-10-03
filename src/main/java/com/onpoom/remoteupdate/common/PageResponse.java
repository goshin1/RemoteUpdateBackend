package com.onpoom.remoteupdate.common;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * 페이징 응답. Spring 의 Page 객체를 그대로 JSON 으로 내보내면 구조가 버전마다 바뀔 수 있어 필요한 값만 담는다.
 * page 는 0부터 시작.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
