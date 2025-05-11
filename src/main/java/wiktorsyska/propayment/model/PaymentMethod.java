package wiktorsyska.propayment.model;

import java.math.BigDecimal;

public class PaymentMethod {
    private String id;
    private int discount;
    private BigDecimal limit;
    private BigDecimal used = BigDecimal.ZERO;

    public PaymentMethod() {}
    public void setId(String id) {
        this.id = id;
    }

    public void setDiscount(int discount) {
        this.discount = discount;
    }

    public void setLimit(BigDecimal limit) {
        this.limit = limit;
    }

    public String getId() {
        return id;
    }

    public int getDiscount() {
        return discount;
    }

    public BigDecimal getUsed() {
        return used;
    }

    public void setUsed(BigDecimal used) {
        this.used = used;
    }

    public BigDecimal getAvailableLimit() {
        return limit.subtract(used);
    }
}
