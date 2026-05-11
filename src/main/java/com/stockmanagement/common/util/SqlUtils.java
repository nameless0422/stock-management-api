package com.stockmanagement.common.util;

/**
 * SQL LIKE 패턴 이스케이프 유틸리티.
 *
 * <p>JPQL {@code ESCAPE '!'} 용도와 Criteria API {@code ESCAPE '\'} 용도를 모두 지원한다.
 */
public final class SqlUtils {

    private SqlUtils() {}

    /**
     * LIKE 패턴 와일드카드를 이스케이프한다.
     *
     * @param value   이스케이프할 문자열
     * @param escape  이스케이프 문자 ('!' 또는 '\\')
     */
    public static String escapeLike(String value, char escape) {
        String esc = String.valueOf(escape);
        return value.replace(esc, esc + esc)
                    .replace("%", esc + "%")
                    .replace("_", esc + "_");
    }

    /** JPQL {@code ESCAPE '!'} 기준 이스케이프. */
    public static String escapeLike(String value) {
        return escapeLike(value, '!');
    }
}
