package fptu.exe202.signify.signifybe.features.payment.application;

public record VerifiedPayOSWebhook(long orderCode, long amount, String currency, String reference,
                                   String paymentLinkId, String dataCode, String transactionDateTime,
                                   String envelopeCode, boolean envelopeSuccess) { }
