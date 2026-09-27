package io.github.aakashnaidum.voting.model;

import java.util.LinkedHashMap;

/** Simple string-keyed row for list pages (JSP EL reads map keys directly). */
public class Row extends LinkedHashMap<String, Object> {
    private static final long serialVersionUID = 1L;

    public Row with(String key, Object value) {
        put(key, value);
        return this;
    }
}
