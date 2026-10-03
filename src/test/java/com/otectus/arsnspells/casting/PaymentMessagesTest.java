package com.otectus.arsnspells.casting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The shortage message shows required and available amounts without overstating either. */
class PaymentMessagesTest {
    @Test
    void wholeAmountsHaveNoFractionAndPartsRoundDown() {
        assertEquals("250", PaymentMessages.amount(250.0));
        assertEquals("180.5", PaymentMessages.amount(180.55));
        assertEquals("0", PaymentMessages.amount(0.04));
        assertEquals("32767", PaymentMessages.amount(32767.001));
        assertEquals("12.5", PaymentMessages.amount(12.5));
    }
}
