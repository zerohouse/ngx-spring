package com.zerohouse.ngx;

import org.junit.Test;

import static org.junit.Assert.*;

public class NgxGeneratorTest {

    @Test
    public void isAssign() {
        System.out.println(Enum.class.isAssignableFrom(aBC.class));
    }

    enum aBC {}

}