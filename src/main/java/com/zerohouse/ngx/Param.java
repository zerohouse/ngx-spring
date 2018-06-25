package com.zerohouse.ngx;

public class Param {
    String name;
    String type;
    boolean required;

    Param(String name, String type, boolean required) {
        this.name = name;
        this.type = type;
        this.required = required;
    }

}
