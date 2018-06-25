package com.zerohouse.ngx;

public class Param {
    private String name;
    private String type;
    boolean required;

    Param(String name, String type, boolean required) {
        this.name = name;
        this.type = type;
        this.required = required;
    }

    public String getName() {
        return name;
    }

    String toParamString() {
        return String.format("%s%s: %s", this.name, this.required ? "" : "?", this.type);
    }
}
