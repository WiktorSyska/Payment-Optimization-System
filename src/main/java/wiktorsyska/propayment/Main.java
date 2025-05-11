package wiktorsyska.propayment;

import wiktorsyska.propayment.model.Order;
import wiktorsyska.propayment.model.PaymentMethod;
import wiktorsyska.propayment.service.PaymentOptimizer;
import wiktorsyska.propayment.service.PaymentReport;
import wiktorsyska.propayment.util.JsonReader;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if(args.length < 2) {
            System.err.println("Two paths needed");
            System.exit(1);
        }

        String ordersFilePath = args[0];
        String paymentMetodsFilePath = args[1];

        try {
            JsonReader reader = new JsonReader();
            List<Order> orders = reader.readOrders(ordersFilePath);
            List<PaymentMethod> paymentMethods = reader.readPaymentMethods(paymentMetodsFilePath);

            Map<String, PaymentMethod> paymentMethodsMap = paymentMethods.stream()
                    .collect(Collectors.toMap(PaymentMethod::getId, method -> method));

            PaymentOptimizer optimizer = new PaymentOptimizer(orders, paymentMethodsMap);
            Map<String, BigDecimal> paymentSummary = optimizer.optimize();

            PaymentReport report = new PaymentReport(paymentSummary);
            report.printReport();

        } catch (Exception e) {

            System.err.println("There was an error" + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
