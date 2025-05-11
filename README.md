
# Payment Optimization System


This project is implementing an intelligent payment maximization platform for e-commerce orders. The platform analyzes available payment modes (including loyalty points and bank cards with multiple promotions) and calculates the optimal way to pay for an order configured to obtain the highest discounts while considering payment method restrictions.


## Features

- Processes multiple orders with different payment method promotions
- Supports four payment strategies:

    1. Full payment with loyalty points
    2. Full payment with promotional bank cards
    3. Partial payment with points (minimum 10%) + remaining with cards
    4. Multiple payment methods when no single method suffices

- Maximizes discounts while respecting payment method limits
- Handles edge cases like zero-value orders and insufficient funds


## Requirements

- Java 17 or later
- Maven (for building)
- Jackson library (for JSON processing)
## Usage/Examples
Run the application with two JSON files as arguments:
```cmd
java -jar target/payment-optimizer.jar orders.json paymentmethods.json
```
#### Input Files

orders.json - Contains order information:

```json
[
  {
    "id": "ORDER1",
    "value": "100.00",
    "promotions": ["mZysk"]
  },
  ...
]
```
paymentmethods.json - Contains payment method details:

```json
[
  {
    "id": "PUNKTY",
    "discount": "15",
    "limit": "100.00"
  },
  ...
]
```
#### Output
```output
BosBankrut 190.00
PUNKTY 100.00
mZysk 165.00
```


## Key Classes

- **PaymentOptimizer** - Core optimization logic
- **JsonReader** - Reads input JSON files
- **PaymentReport** - Generates the output report
- **Order** - Represents an order with promotions
- **PaymentMethod** - Represents a payment method with limits and discounts
## Business Rules
- Orders can be paid:
    - Fully with one traditional payment method
    - Fully with loyalty points
    - Partially with points (minimum 10%) and partially with cards
- Discount rules:
    - Full payment with bank card applies card's discount
    - Partial payment (≥10%) with points gives 10% discount on entire order
    - Full payment with points applies points discount (15%)

- Optimization priorities:
    - Maximize total discount
    - Prefer using points over cards when discounts are equal
    - Fully utilize available points when possible

## Testing
The project includes JUnit tests covering:

- Basic scenarios from the specification
- Edge cases (zero values, empty promotions)
- Payment method combinations

Run tests with:

```
mvn test
```
## Build Configuration

The project uses Maven with the following key dependencies:

- Jackson (JSON processing)
- JUnit (testing)
- Maven Assembly Plugin (for creating fat jar)
## Authors

- [@WiktorSyska](https://www.github.com/WiktorSyska)

