package wiktorsyska.propayment.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.TreeMap;

public class PaymentReport {
    private final Map<String, BigDecimal> paymentSummary;

    public PaymentReport(Map<String, BigDecimal> paymentSummary) {
        this.paymentSummary = new TreeMap<>(paymentSummary);
    }

    public String generateReport() {
        StringBuilder report = new StringBuilder();
        for (Map.Entry<String, BigDecimal> entry : paymentSummary.entrySet()) {
            if (entry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                report.append(entry.getKey())
                        .append(" ")
                        .append(entry.getValue().setScale(2, RoundingMode.HALF_UP))
                        .append("\n");
            }
        }
        return report.toString();
    }

    public void printReport() {
        System.out.println(generateReport());
    }
}
