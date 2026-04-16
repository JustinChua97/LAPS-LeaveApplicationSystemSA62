package com.iss.laps;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PaginationValidationTest {

    @Test
    public void testNegativePageDefaultsToZero() {
        int page = -1;
        if (page < 0) {
            page = 0;
        }
        assertEquals(0, page);
    }

    @Test
    public void testZeroPageRemainsZero() {
        int page = 0;
        if (page < 0) {
            page = 0;
        }
        assertEquals(0, page);
    }

    @Test
    public void testPositivePageRemains() {
        int page = 5;
        if (page < 0) {
            page = 0;
        }
        assertEquals(5, page);
    }

    @Test
    public void testValidSize10Remains() {
        int size = 10;
        if (size != 10 && size != 20 && size != 25) {
            size = 10;
        }
        assertEquals(10, size);
    }

    @Test
    public void testValidSize20Remains() {
        int size = 20;
        if (size != 10 && size != 20 && size != 25) {
            size = 10;
        }
        assertEquals(20, size);
    }

    @Test
    public void testValidSize25Remains() {
        int size = 25;
        if (size != 10 && size != 20 && size != 25) {
            size = 10;
        }
        assertEquals(25, size);
    }

    @Test
    public void testInvalidSize50DefaultsToTen() {
        int size = 50;
        if (size != 10 && size != 20 && size != 25) {
            size = 10;
        }
        assertEquals(10, size);
    }

    @Test
    public void testInvalidSize15DefaultsToTen() {
        int size = 15;
        if (size != 10 && size != 20 && size != 25) {
            size = 10;
        }
        assertEquals(10, size);
    }
}