package wiktorsyska.propayment.service;

import wiktorsyska.propayment.model.Order;
import wiktorsyska.propayment.model.PaymentMethod;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class PaymentOptimizer {
    private final List<Order> orders;
    private final Map<String, PaymentMethod> paymentMethods;
    private final Map<String, BigDecimal> paymentSummary = new HashMap<>();
    private final Map<Order, Map<String, BigDecimal>> orderPayments = new HashMap<>();

    private static final String POINTS_ID = "PUNKTY";
    private static final BigDecimal TEN = new BigDecimal("10");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int POINTS_PARTIAL_DISCOUNT = 10;
    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    /**
     * Constructs a PaymentOptimizer with the given orders and payment methods.
     *
     * @param orders the list of orders to optimize payments for
     * @param paymentMethods the available payment methods with their limits and discounts
     */
    public PaymentOptimizer(List<Order> orders, Map<String, PaymentMethod> paymentMethods) {
        this.orders = new ArrayList<>(orders);
        this.paymentMethods = new HashMap<>(paymentMethods);
        paymentMethods.keySet().forEach(key -> paymentSummary.put(key, BigDecimal.ZERO));
    }

    /**
     * Optimizes payments for all orders to maximize discounts.
     *
     * @return a map of payment method IDs to total amounts used
     */
    public Map<String, BigDecimal> optimize() {
        sortOrdersByPotentialDiscount();

        for (Order order : orders) {
            processOrder(order);
        }

        postProcessUnusedPoints();

        return new HashMap<>(paymentSummary);
    }

    /**
     * Sorts orders by their potential discount ratio (discount/value).
     * Orders with higher discount potential are processed first.
     */
    private void sortOrdersByPotentialDiscount() {
        orders.sort((o1, o2) -> {
            BigDecimal maxDiscount1 = calculateMaxDiscount(o1);
            BigDecimal maxDiscount2 = calculateMaxDiscount(o2);

            BigDecimal ratio1 = o1.getValue().compareTo(BigDecimal.ZERO) > 0 ?
                    maxDiscount1.divide(o1.getValue(), 4, ROUNDING_MODE) : BigDecimal.ZERO;
            BigDecimal ratio2 = o2.getValue().compareTo(BigDecimal.ZERO) > 0 ?
                    maxDiscount2.divide(o2.getValue(), 4, ROUNDING_MODE) : BigDecimal.ZERO;

            return ratio2.compareTo(ratio1);
        });
    }

    /**
     * Processes a single order to determine the optimal payment method(s).
     *
     * @param order the order to process
     */
    private void processOrder(Order order) {
        BigDecimal orderValue = order.getValue();
        if (orderValue.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        Map<String, BigDecimal> bestPaymentPlan = new HashMap<>();
        BigDecimal lowestCost = orderValue;

        // Strategy 1: Pay fully with points
        if (canPayFullWithPoints(orderValue)) {
            PaymentMethod pointsMethod = paymentMethods.get(POINTS_ID);
            BigDecimal discountedValue = applyDiscount(orderValue, pointsMethod.getDiscount());

            Map<String, BigDecimal> paymentPlan = new HashMap<>();
            paymentPlan.put(POINTS_ID, discountedValue);

            BigDecimal totalCost = calculateTotalCost(paymentPlan);
            if (totalCost.compareTo(lowestCost) < 0) {
                lowestCost = totalCost;
                bestPaymentPlan = new HashMap<>(paymentPlan);
            }
        }

        // Strategy 2: Pay fully with a bank card
        List<String> promotions = order.getPromotions() != null ? order.getPromotions() : new ArrayList<>();
        for (String promotion : promotions) {
            if (!POINTS_ID.equals(promotion) && paymentMethods.containsKey(promotion)) {
                PaymentMethod cardMethod = paymentMethods.get(promotion);

                if (cardMethod.getAvailableLimit().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                BigDecimal discountedValue = applyDiscount(orderValue, cardMethod.getDiscount());

                if (cardMethod.getAvailableLimit().compareTo(discountedValue) >= 0) {
                    Map<String, BigDecimal> paymentPlan = new HashMap<>();
                    paymentPlan.put(promotion, discountedValue);

                    BigDecimal totalCost = calculateTotalCost(paymentPlan);
                    if (totalCost.compareTo(lowestCost) < 0) {
                        lowestCost = totalCost;
                        bestPaymentPlan = new HashMap<>(paymentPlan);
                    }
                }
            }
        }

        // Strategy 3: Partial payment with points + card
        if (paymentMethods.containsKey(POINTS_ID)) {
            PaymentMethod pointsMethod = paymentMethods.get(POINTS_ID);
            BigDecimal availablePoints = pointsMethod.getAvailableLimit();
            if (availablePoints.compareTo(BigDecimal.ZERO) > 0) {
                Map<String, BigDecimal> currentBestPlan = new HashMap<>(bestPaymentPlan);
                testMixedPaymentStrategies(orderValue, availablePoints, promotions, bestPaymentPlan, lowestCost);
                if (!bestPaymentPlan.equals(currentBestPlan)) {
                    lowestCost = calculateTotalCost(bestPaymentPlan);
                }
            }
        }

        // Strategy 4: Payment with multiple methods (if no single method is sufficient)
        if (bestPaymentPlan.isEmpty()) {
            bestPaymentPlan = optimizeMultiplePaymentMethods(orderValue, promotions);
        }

        updatePaymentMethodLimits(order, bestPaymentPlan);
    }

    /**
     * Tests different combinations of partial points and card payments.
     *
     * @param orderValue the total value of the order
     * @param availablePoints the available points balance
     * @param promotions the list of applicable promotions
     * @param bestPaymentPlan reference to the current best payment plan
     * @param lowestCost reference to the current lowest cost found
     */
    private void testMixedPaymentStrategies(BigDecimal orderValue, BigDecimal availablePoints,
                                            List<String> promotions, Map<String, BigDecimal> bestPaymentPlan,
                                            BigDecimal lowestCost) {
        // Minimum amount to pay with points (10% of order value)
        BigDecimal minPointsPayment = orderValue.multiply(TEN).divide(HUNDRED, SCALE, ROUNDING_MODE);
        // Maximum amount to pay with points (lesser of available points and order value)
        BigDecimal maxPointsPayment = availablePoints.min(orderValue);
        // Check different proportions, starting from minimum required points amount
        BigDecimal pointsStep = orderValue.multiply(new BigDecimal("0.1")).max(new BigDecimal("0.01"));
        // First apply 10% discount to entire order for partial points payment
        BigDecimal discountedOrderValue = applyDiscount(orderValue, POINTS_PARTIAL_DISCOUNT);

        for (BigDecimal pointsPayment = minPointsPayment;
             pointsPayment.compareTo(maxPointsPayment) <= 0;
             pointsPayment = pointsPayment.add(pointsStep)) {

            if (pointsPayment.compareTo(minPointsPayment) < 0) {
                continue;
            }

            if (pointsPayment.compareTo(availablePoints) > 0) {
                break;
            }

            BigDecimal remainingValue = discountedOrderValue.subtract(pointsPayment);
            if (remainingValue.compareTo(BigDecimal.ZERO) < 0) {
                remainingValue = BigDecimal.ZERO;
            }

            for (String cardId : paymentMethods.keySet()) {
                if (!POINTS_ID.equals(cardId) && promotions.contains(cardId)) {
                    lowestCost = getBigDecimal(bestPaymentPlan, lowestCost, pointsPayment, remainingValue, cardId);
                }
            }

            for (String cardId : paymentMethods.keySet()) {
                if (!POINTS_ID.equals(cardId) && !promotions.contains(cardId)) {
                    lowestCost = getBigDecimal(bestPaymentPlan, lowestCost, pointsPayment, remainingValue, cardId);
                }
            }
        }
    }

    /**
     * Helper method to evaluate a payment plan and update the best plan if better.
     */
    private BigDecimal getBigDecimal(Map<String, BigDecimal> bestPaymentPlan, BigDecimal lowestCost,
                                     BigDecimal pointsPayment, BigDecimal remainingValue, String cardId) {
        PaymentMethod cardMethod = paymentMethods.get(cardId);

        if (cardMethod.getAvailableLimit().compareTo(remainingValue) >= 0) {
            Map<String, BigDecimal> paymentPlan = new HashMap<>();
            paymentPlan.put(POINTS_ID, pointsPayment);
            paymentPlan.put(cardId, remainingValue);

            BigDecimal totalCost = pointsPayment.add(remainingValue);
            if (totalCost.compareTo(lowestCost) < 0) {
                lowestCost = totalCost;
                bestPaymentPlan.clear();
                bestPaymentPlan.putAll(paymentPlan);
            }
        }
        return lowestCost;
    }

    /**
     * Optimizes payment using multiple methods when no single method is sufficient.
     *
     * @param orderValue the total value of the order
     * @param promotions the list of applicable promotions
     * @return the optimal payment plan using multiple methods
     */
    private Map<String, BigDecimal> optimizeMultiplePaymentMethods(BigDecimal orderValue, List<String> promotions) {
        Map<String, BigDecimal> paymentPlan = new HashMap<>();
        BigDecimal remainingValue = orderValue;

        List<PaymentMethod> sortedMethods = new ArrayList<>();
        BigDecimal minimumPointsNeeded = orderValue.multiply(TEN).divide(HUNDRED, SCALE, ROUNDING_MODE);

        boolean canUseMinimumPoints = false;
        boolean hasEnoughPointsForPartial = false;

        if (paymentMethods.containsKey(POINTS_ID)) {
            PaymentMethod pointsMethod = paymentMethods.get(POINTS_ID);
            BigDecimal availablePoints = pointsMethod.getAvailableLimit();

            if (availablePoints.compareTo(minimumPointsNeeded) >= 0) {
                canUseMinimumPoints = true;
                hasEnoughPointsForPartial = true;
            }
        }

        if (canUseMinimumPoints) {
            BigDecimal discountedValue = applyDiscount(orderValue, POINTS_PARTIAL_DISCOUNT);
            paymentPlan.put(POINTS_ID, minimumPointsNeeded);
            remainingValue = discountedValue.subtract(minimumPointsNeeded);
        }

        if (paymentMethods.containsKey(POINTS_ID) && !canUseMinimumPoints) {
            sortedMethods.add(paymentMethods.get(POINTS_ID));
        }

        for (String promotionId : promotions) {
            if (!POINTS_ID.equals(promotionId) && paymentMethods.containsKey(promotionId)) {
                sortedMethods.add(paymentMethods.get(promotionId));
            }
        }

        for (PaymentMethod method : paymentMethods.values()) {
            if (!POINTS_ID.equals(method.getId()) && !promotions.contains(method.getId()) &&
                    !sortedMethods.contains(method)) {
                sortedMethods.add(method);
            }
        }

        sortedMethods.sort((m1, m2) -> Integer.compare(m2.getDiscount(), m1.getDiscount()));

        for (PaymentMethod method : sortedMethods) {
            if (remainingValue.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            String methodId = method.getId();
            BigDecimal availableLimit = method.getAvailableLimit();

            if (POINTS_ID.equals(methodId) && canUseMinimumPoints) {
                BigDecimal usedPoints = paymentPlan.getOrDefault(POINTS_ID, BigDecimal.ZERO);
                availableLimit = availableLimit.subtract(usedPoints);
            }

            if (availableLimit.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal amountToPay;

            if ((paymentPlan.isEmpty() || (paymentPlan.size() == 1 && canUseMinimumPoints)) &&
                    promotions.contains(methodId) && !POINTS_ID.equals(methodId)) {
                amountToPay = applyDiscount(remainingValue, method.getDiscount());
            } else if (POINTS_ID.equals(methodId) && !hasEnoughPointsForPartial) {
                amountToPay = applyDiscount(remainingValue, method.getDiscount());
            } else {
                amountToPay = remainingValue;
            }

            amountToPay = amountToPay.min(availableLimit);

            BigDecimal currentAmount = paymentPlan.getOrDefault(methodId, BigDecimal.ZERO);
            paymentPlan.put(methodId, currentAmount.add(amountToPay));

            if ((paymentPlan.size() <= 2 && canUseMinimumPoints) &&
                    promotions.contains(methodId) && !POINTS_ID.equals(methodId)) {
                BigDecimal originalAmount = amountToPay.multiply(HUNDRED)
                        .divide(HUNDRED.subtract(new BigDecimal(method.getDiscount())), SCALE, ROUNDING_MODE);
                remainingValue = remainingValue.subtract(originalAmount);
            } else {
                remainingValue = remainingValue.subtract(amountToPay);
            }
        }

        return paymentPlan;
    }

    /**
     * Processes any unused points by replacing partial card payments where possible.
     */
    void postProcessUnusedPoints() {
        if (!paymentMethods.containsKey(POINTS_ID)) {
            return;
        }

        PaymentMethod pointsMethod = paymentMethods.get(POINTS_ID);
        BigDecimal unusedPoints = pointsMethod.getAvailableLimit();

        if (unusedPoints.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        for (int i = orders.size() - 1; i >= 0; i--) {
            Order order = orders.get(i);
            Map<String, BigDecimal> currentPayment = orderPayments.get(order);

            if (currentPayment == null || currentPayment.isEmpty()) {
                continue;
            }

            if (currentPayment.size() == 1 && currentPayment.containsKey(POINTS_ID)) {
                continue;
            }

            for (String methodId : new ArrayList<>(currentPayment.keySet())) {
                if (!POINTS_ID.equals(methodId)) {
                    BigDecimal cardPayment = currentPayment.get(methodId);
                    BigDecimal pointsToUse = cardPayment.min(unusedPoints);

                    if (pointsToUse.compareTo(BigDecimal.ZERO) > 0) {
                        currentPayment.put(methodId, cardPayment.subtract(pointsToUse));
                        BigDecimal currentPoints = currentPayment.getOrDefault(POINTS_ID, BigDecimal.ZERO);
                        currentPayment.put(POINTS_ID, currentPoints.add(pointsToUse));

                        if (currentPayment.get(methodId).compareTo(BigDecimal.ZERO) == 0) {
                            currentPayment.remove(methodId);
                        }

                        unusedPoints = unusedPoints.subtract(pointsToUse);
                        recalculatePaymentLimits();

                        if (unusedPoints.compareTo(BigDecimal.ZERO) <= 0) {
                            return;
                        }
                    }
                }
            }
        }
    }

    /**
     * Recalculates payment method limits based on current order payments.
     */
    private void recalculatePaymentLimits() {
        paymentMethods.values().forEach(m -> m.setUsed(BigDecimal.ZERO));
        paymentSummary.clear();
        paymentMethods.keySet().forEach(k -> paymentSummary.put(k, BigDecimal.ZERO));

        for (Map<String, BigDecimal> payment : orderPayments.values()) {
            for (Map.Entry<String, BigDecimal> entry : payment.entrySet()) {
                String methodId = entry.getKey();
                BigDecimal amount = entry.getValue();

                if (paymentMethods.containsKey(methodId)) {
                    paymentMethods.get(methodId).setUsed(
                            paymentMethods.get(methodId).getUsed().add(amount)
                    );
                    paymentSummary.put(methodId,
                            paymentSummary.get(methodId).add(amount)
                    );
                }
            }
        }
    }

    /**
     * Updates payment method limits and tracks order payments.
     *
     * @param order the order being processed
     * @param paymentPlan the payment plan for the order
     */
    void updatePaymentMethodLimits(Order order, Map<String, BigDecimal> paymentPlan) {
        orderPayments.put(order, new HashMap<>(paymentPlan));

        for (Map.Entry<String, BigDecimal> entry : paymentPlan.entrySet()) {
            String methodId = entry.getKey();
            BigDecimal amount = entry.getValue();

            if (amount.compareTo(BigDecimal.ZERO) > 0 && paymentMethods.containsKey(methodId)) {
                PaymentMethod method = paymentMethods.get(methodId);
                method.setUsed(method.getUsed().add(amount));
                paymentSummary.put(methodId, paymentSummary.getOrDefault(methodId, BigDecimal.ZERO).add(amount));
            }
        }
    }

    /**
     * Calculates the total cost of a payment plan.
     *
     * @param paymentPlan the payment plan to calculate
     * @return the total cost of the payment plan
     */
    private BigDecimal calculateTotalCost(Map<String, BigDecimal> paymentPlan) {
        return paymentPlan.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculates the maximum possible discount for an order.
     *
     * @param order the order to calculate for
     * @return the maximum possible discount amount
     */
    private BigDecimal calculateMaxDiscount(Order order) {
        BigDecimal maxDiscount = BigDecimal.ZERO;
        BigDecimal orderValue = order.getValue();

        if (orderValue.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        if (paymentMethods.containsKey(POINTS_ID)) {
            PaymentMethod pointsMethod = paymentMethods.get(POINTS_ID);
            BigDecimal pointsDiscount = orderValue.multiply(BigDecimal.valueOf(pointsMethod.getDiscount()))
                    .divide(HUNDRED, SCALE, ROUNDING_MODE);

            if (pointsMethod.getAvailableLimit().compareTo(
                    applyDiscount(orderValue, pointsMethod.getDiscount())) >= 0) {
                maxDiscount = pointsDiscount;
            }
        }

        for (String promotion : order.getPromotions()) {
            if (paymentMethods.containsKey(promotion) && !POINTS_ID.equals(promotion)) {
                PaymentMethod method = paymentMethods.get(promotion);
                BigDecimal cardDiscount = orderValue.multiply(BigDecimal.valueOf(method.getDiscount()))
                        .divide(HUNDRED, SCALE, ROUNDING_MODE);

                if (method.getAvailableLimit().compareTo(
                        applyDiscount(orderValue, method.getDiscount())) >= 0) {
                    if (cardDiscount.compareTo(maxDiscount) > 0) {
                        maxDiscount = cardDiscount;
                    }
                }
            }
        }

        if (paymentMethods.containsKey(POINTS_ID)) {
            PaymentMethod pointsMethod = paymentMethods.get(POINTS_ID);
            BigDecimal minPointsNeeded = orderValue.multiply(TEN).divide(HUNDRED, SCALE, ROUNDING_MODE);

            if (pointsMethod.getAvailableLimit().compareTo(minPointsNeeded) >= 0) {
                BigDecimal partialPointsDiscount = orderValue.multiply(BigDecimal.valueOf(POINTS_PARTIAL_DISCOUNT))
                        .divide(HUNDRED, SCALE, ROUNDING_MODE);

                if (partialPointsDiscount.compareTo(maxDiscount) > 0) {
                    maxDiscount = partialPointsDiscount;
                }
            }
        }

        return maxDiscount;
    }

    /**
     * Checks if an order can be paid fully with points.
     *
     * @param value the order value
     * @return true if the order can be paid fully with points, false otherwise
     */
    private boolean canPayFullWithPoints(BigDecimal value) {
        if (!paymentMethods.containsKey(POINTS_ID)) {
            return false;
        }

        PaymentMethod pointsMethod = paymentMethods.get(POINTS_ID);
        int discount = pointsMethod.getDiscount();
        BigDecimal discountedValue = applyDiscount(value, discount);

        return pointsMethod.getAvailableLimit().compareTo(discountedValue) >= 0;
    }

    /**
     * Applies a percentage discount to a value.
     *
     * @param value the original value
     * @param discountPercent the discount percentage
     * @return the discounted value
     */
    private BigDecimal applyDiscount(BigDecimal value, int discountPercent) {
        BigDecimal discount = value.multiply(BigDecimal.valueOf(discountPercent))
                .divide(HUNDRED, SCALE, ROUNDING_MODE);
        return value.subtract(discount).setScale(SCALE, ROUNDING_MODE);
    }
}