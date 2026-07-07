package com.marnickseidel.moneyleaks.util;

import com.marnickseidel.moneyleaks.model.enums.PaymentMethod;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentMethodClassifierTest {

    private final PaymentMethodClassifier classifier = new PaymentMethodClassifier();

    @Test
    void classifiesSepaDirectDebitFromDescription() {
        assertEquals(PaymentMethod.DIRECT_DEBIT, classifier.classify(
                "Europese incasso: Netflix abonnement", null, null));
    }

    @Test
    void classifiesIngPaymentTerminalAsCardPos() {
        assertEquals(PaymentMethod.CARD_POS, classifier.classify(
                "ALDI GNG009 GRONINGEN GRONINGEN", "Payment terminal", null));
    }

    @Test
    void classifiesAsnIncassoCodeAsDirectDebit() {
        assertEquals(PaymentMethod.DIRECT_DEBIT, classifier.classify(
                "Europese incasso: CZZorgverzekering", null, "EIC"));
    }

    @Test
    void classifiesAsnCardCodeAsCardPos() {
        assertEquals(PaymentMethod.CARD_POS, classifier.classify(
                "Jumbo Ciboga GRONINGEN NLD", null, "BA"));
    }

    @Test
    void classifiesMultiSafepayAsOnlinePayment() {
        assertEquals(PaymentMethod.ONLINE_PAYMENT, classifier.classify(
                "WATCHBANDJES SHOP NL VIA MULTISAFEPAY", "Payment terminal", null));
    }
}
