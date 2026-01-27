package com.demo.passport;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DateNormalizeTest {

    @Test
    public void date_normalize_iso_to_yymmdd() {
        assertEquals("900101", MainActivity.normalizeMrzDate("1990-01-01"));
    }

    @Test
    public void date_normalize_yyyymmdd_to_yymmdd() {
        assertEquals("900101", MainActivity.normalizeMrzDate("19900101"));
    }

    @Test
    public void date_normalize_yymmdd_passthrough() {
        assertEquals("900101", MainActivity.normalizeMrzDate("900101"));
    }
}
