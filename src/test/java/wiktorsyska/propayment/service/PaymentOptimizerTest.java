package wiktorsyska.propayment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import wiktorsyska.propayment.model.Order;
import wiktorsyska.propayment.model.PaymentMethod;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class PaymentOptimizerTest {

    private List<Order> orders;
    private Map<String, PaymentMethod> paymentMethods;
    private PaymentOptimizer optimizer;

    @BeforeEach
    void setUp() {
        //Example payment methods
        paymentMethods = new HashMap<>();

        PaymentMethod points = new PaymentMethod();
        points.setId("PUNKTY");
        points.setDiscount(15);
        points.setLimit(new BigDecimal("100.00"));
        paymentMethods.put("PUNKTY", points);

        PaymentMethod mZysk = new PaymentMethod();
        mZysk.setId("mZysk");
        mZysk.setDiscount(10);
        mZysk.setLimit(new BigDecimal("180.00"));
        paymentMethods.put("mZysk", mZysk);

        PaymentMethod bosBankrut = new PaymentMethod();
        bosBankrut.setId("BosBankrut");
        bosBankrut.setDiscount(5);
        bosBankrut.setLimit(new BigDecimal("200.00"));
        paymentMethods.put("BosBankrut", bosBankrut);
    }

    @Test
    void testOptimizeForSampleData() {
        orders = new ArrayList<>();

        Order order1 = new Order();
        order1.setId("ORDER1");
        order1.setValue(new BigDecimal("100.00"));
        order1.setPromotions(List.of("mZysk"));
        orders.add(order1);

        Order order2 = new Order();
        order2.setId("ORDER2");
        order2.setValue(new BigDecimal("200.00"));
        order2.setPromotions(List.of("BosBankrut"));
        orders.add(order2);

        Order order3 = new Order();
        order3.setId("ORDER3");
        order3.setValue(new BigDecimal("150.00"));
        order3.setPromotions(List.of("mZysk", "BosBankrut"));
        orders.add(order3);

        Order order4 = new Order();
        order4.setId("ORDER4");
        order4.setValue(new BigDecimal("50.00"));
        order4.setPromotions(Collections.emptyList());
        orders.add(order4);

        optimizer = new PaymentOptimizer(orders, paymentMethods);
        Map<String, BigDecimal> result = optimizer.optimize();

        assertEquals(new BigDecimal("165.00"), result.get("mZysk"), "Płatność mZysk powinna wynosić 165.00");
        assertEquals(new BigDecimal("190.00"), result.get("BosBankrut"), "Płatność BosBankrut powinna wynosić 190.00");
        assertEquals(new BigDecimal("100.00"), result.get("PUNKTY"), "Płatność PUNKTY powinna wynosić 100.00");
    }

    @Test
    void testOrderWithoutPromotions() {
        Order order = new Order();
        order.setId("ORDER5");
        order.setValue(new BigDecimal("30.00"));
        order.setPromotions(Collections.emptyList());

        optimizer = new PaymentOptimizer(List.of(order), paymentMethods);
        Map<String, BigDecimal> result = optimizer.optimize();

        assertTrue(result.get("PUNKTY").compareTo(new BigDecimal("3.00")) >= 0);
    }

    @Test
    void testFullPointsPayment() {
        Order order = new Order();
        order.setId("ORDER6");
        order.setValue(new BigDecimal("80.00"));
        order.setPromotions(Collections.emptyList());

        optimizer = new PaymentOptimizer(List.of(order), paymentMethods);
        Map<String, BigDecimal> result = optimizer.optimize();

        assertEquals(new BigDecimal("68.00"), result.get("PUNKTY"));
    }

    @Test
    void testZeroValueOrder() {
        Order order = new Order();
        order.setId("ORDER7");
        order.setValue(BigDecimal.ZERO);
        order.setPromotions(List.of("mZysk"));

        optimizer = new PaymentOptimizer(List.of(order), paymentMethods);
        Map<String, BigDecimal> result = optimizer.optimize();

        assertTrue(result.isEmpty() || result.values().stream().allMatch(v -> v.compareTo(BigDecimal.ZERO) == 0));
    }

    @Test
    void testMultiplePaymentMethods() {
        paymentMethods.get("mZysk").setLimit(new BigDecimal("50.00"));
        paymentMethods.get("BosBankrut").setLimit(new BigDecimal("50.00"));

        Order order = new Order();
        order.setId("ORDER8");
        order.setValue(new BigDecimal("150.00"));
        order.setPromotions(List.of("mZysk", "BosBankrut"));

        optimizer = new PaymentOptimizer(List.of(order), paymentMethods);
        Map<String, BigDecimal> result = optimizer.optimize();

        assertTrue(result.containsKey("PUNKTY"));
        assertTrue(result.containsKey("mZysk") || result.containsKey("BosBankrut"));
    }


    @Test
    void testMultipleOrdersOptimization() {
        List<Order> testOrders = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            Order order = new Order();
            order.setId("ORDER_" + i);
            order.setValue(new BigDecimal(i * 10));
            order.setPromotions(i % 2 == 0 ? List.of("mZysk") : Collections.emptyList());
            testOrders.add(order);
        }

        optimizer = new PaymentOptimizer(testOrders, paymentMethods);
        Map<String, BigDecimal> result = optimizer.optimize();

        assertEquals(new BigDecimal("100.00"), result.get("PUNKTY"));
    }

    @Test
    void testBorderlinePointsValue() {
        BigDecimal orderValue = new BigDecimal("100.00");
        paymentMethods.get("PUNKTY").setLimit(orderValue.multiply(new BigDecimal("0.1")));

        Order order = new Order();
        order.setId("ORDER10");
        order.setValue(orderValue);
        order.setPromotions(Collections.emptyList());

        optimizer = new PaymentOptimizer(List.of(order), paymentMethods);
        Map<String, BigDecimal> result = optimizer.optimize();

        assertEquals(new BigDecimal("10.00"), result.get("PUNKTY"));
    }

    @Test
    void testPostProcessUnusedPoints() {
        Order order1 = new Order();
        order1.setId("ORDER11");
        order1.setValue(new BigDecimal("100.00"));
        order1.setPromotions(List.of("mZysk"));

        Order order2 = new Order();
        order2.setId("ORDER12");
        order2.setValue(new BigDecimal("50.00"));
        order2.setPromotions(Collections.emptyList());

        optimizer = new PaymentOptimizer(List.of(order1, order2), paymentMethods);

        optimizer.updatePaymentMethodLimits(order1, Map.of("mZysk", new BigDecimal("90.00")));
        optimizer.updatePaymentMethodLimits(order2, Map.of("PUNKTY", new BigDecimal("5.00"), "mZysk", new BigDecimal("40.00")));

        optimizer.postProcessUnusedPoints();

        assertEquals(new BigDecimal("100.00"),
                paymentMethods.get("PUNKTY").getUsed());
    }
}
