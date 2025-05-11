package wiktorsyska.propayment.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import wiktorsyska.propayment.model.Order;
import wiktorsyska.propayment.model.PaymentMethod;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class JsonReader {

    private final ObjectMapper objectMapper;

    public JsonReader() {
        this.objectMapper = new ObjectMapper();
    }

    public List<Order> readOrders(String filePath) throws IOException {
        return objectMapper.readValue(
                new File(filePath),
                new TypeReference<>() {}
        );
    }

    public List<PaymentMethod> readPaymentMethods(String filePath) throws IOException {
        return objectMapper.readValue(
                new File(filePath),
                new TypeReference<>() {}
        );
    }

}
