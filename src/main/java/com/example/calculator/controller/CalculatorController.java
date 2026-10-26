package com.example.calculator.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class CalculatorController {

    public static class CalculationRequest {
        private String expression;

        public CalculationRequest() {}

        public CalculationRequest(String expression) {
            this.expression = expression;
        }

        public String getExpression() {
            return expression;
        }

        public void setExpression(String expression) {
            this.expression = expression;
        }
    }

    public static class CalculationResponse {
        private String result;
        private String fraction;
        private String error;

        public CalculationResponse() {}

        public CalculationResponse(String result, String fraction, String error) {
            this.result = result;
            this.fraction = fraction;
            this.error = error;
        }

        public String getResult() {
            return result;
        }

        public void setResult(String result) {
            this.result = result;
        }

        public String getFraction() {
            return fraction;
        }

        public void setFraction(String fraction) {
            this.fraction = fraction;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }

    @PostMapping("/calculate")
    public ResponseEntity<CalculationResponse> calculate(@RequestBody CalculationRequest request) {
        if (request == null || request.getExpression() == null || request.getExpression().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new CalculationResponse(null, null, "Expression cannot be empty"));
        }

        try {
            double numericResult = evaluateExpression(request.getExpression());
            if (Double.isInfinite(numericResult) || Double.isNaN(numericResult)) {
                return ResponseEntity.ok(new CalculationResponse(null, null, "Cannot divide by zero"));
            }

            BigDecimal bd = BigDecimal.valueOf(numericResult);
            bd = bd.setScale(10, RoundingMode.HALF_UP).stripTrailingZeros();
            String resultStr = bd.toPlainString();
            if (resultStr.equals("-0")) {
                resultStr = "0";
            }

            String fractionStr = toFraction(numericResult);
            return ResponseEntity.ok(new CalculationResponse(resultStr, fractionStr, null));
        } catch (ArithmeticException ae) {
            return ResponseEntity.ok(new CalculationResponse(null, null, ae.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.ok(new CalculationResponse(null, null, "Syntax Error"));
        }
    }

    private double evaluateExpression(String input) {
        String normalized = normalizeExpression(input);
        List<String> tokens = tokenize(normalized);
        if (tokens.isEmpty()) {
            return 0.0;
        }
        Parser parser = new Parser(tokens);
        return parser.parseExpression();
    }

    private String normalizeExpression(String input) {
        String sanitized = input.replace("×", "*")
                                .replace("÷", "/")
                                .replace("−", "-")
                                .replaceAll("\\s+", "");

        sanitized = sanitized.replaceAll("[+\\-*/]+$", "");

        StringBuilder clean = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < sanitized.length(); i++) {
            char c = sanitized.charAt(i);
            if (c == '(') {
                depth++;
                clean.append(c);
            } else if (c == ')') {
                if (depth > 0) {
                    depth--;
                    clean.append(c);
                }
            } else {
                clean.append(c);
            }
        }
        while (depth > 0) {
            clean.append(')');
            depth--;
        }

        String result = clean.toString();
        result = result.replaceAll("([0-9.]|%)\\(", "$1*(");
        result = result.replaceAll("\\)([0-9.(])", ")*$1");
        result = result.replaceAll("%([0-9.])", "%*$1");

        return result;
    }

    private List<String> tokenize(String expr) {
        List<String> tokens = new ArrayList<>();
        int len = expr.length();
        int i = 0;

        while (i < len) {
            char c = expr.charAt(i);

            if (Character.isDigit(c) || c == '.') {
                StringBuilder sb = new StringBuilder();
                while (i < len && (Character.isDigit(expr.charAt(i)) || expr.charAt(i) == '.')) {
                    sb.append(expr.charAt(i));
                    i++;
                }
                tokens.add(sb.toString());
            } else if (c == '+' || c == '-' || c == '*' || c == '/' || c == '(' || c == ')' || c == '%') {
                tokens.add(String.valueOf(c));
                i++;
            } else {
                throw new IllegalArgumentException("Invalid character: " + c);
            }
        }
        return tokens;
    }

    private static class Parser {
        private final List<String> tokens;
        private int pos = 0;

        public Parser(List<String> tokens) {
            this.tokens = tokens;
        }

        private String peek() {
            return pos < tokens.size() ? tokens.get(pos) : null;
        }

        private String consume() {
            return pos < tokens.size() ? tokens.get(pos++) : null;
        }

        public double parseExpression() {
            double result = parseTerm();
            while (pos < tokens.size()) {
                String token = peek();
                if ("+".equals(token)) {
                    consume();
                    result += parseTerm();
                } else if ("-".equals(token)) {
                    consume();
                    result -= parseTerm();
                } else {
                    break;
                }
            }
            return result;
        }

        private double parseTerm() {
            double result = parseFactor();
            while (pos < tokens.size()) {
                String token = peek();
                if ("*".equals(token)) {
                    consume();
                    result *= parseFactor();
                } else if ("/".equals(token)) {
                    consume();
                    double divisor = parseFactor();
                    if (divisor == 0.0) {
                        throw new ArithmeticException("Cannot divide by zero");
                    }
                    result /= divisor;
                } else if ("(".equals(token) || (token != null && !token.isEmpty() && (Character.isDigit(token.charAt(0)) || token.charAt(0) == '.'))) {
                    result *= parseFactor();
                } else {
                    break;
                }
            }
            return result;
        }

        private double parseFactor() {
            double factor;
            String token = peek();
            if (token == null) {
                return 0.0;
            }

            if ("+".equals(token)) {
                consume();
                factor = parseFactor();
            } else if ("-".equals(token)) {
                consume();
                factor = -parseFactor();
            } else if ("(".equals(token)) {
                consume();
                factor = parseExpression();
                if (pos < tokens.size() && ")".equals(peek())) {
                    consume();
                }
            } else {
                consume();
                try {
                    factor = Double.parseDouble(token);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid number: " + token);
                }
            }

            while (pos < tokens.size() && "%".equals(peek())) {
                consume();
                factor = factor / 100.0;
            }

            return factor;
        }
    }

    private String toFraction(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return null;
        }

        long sign = value < 0 ? -1 : 1;
        double absVal = Math.abs(value);
        long wholePart = (long) absVal;
        double fractionalPart = absVal - wholePart;

        if (fractionalPart < 1e-9) {
            return String.valueOf(sign * wholePart);
        }

        double tolerance = 1.0E-7;
        double h1 = 1, h2 = 0, k1 = 0, k2 = 1;
        double b = fractionalPart;

        do {
            double a = Math.floor(b);
            double aux = h1;
            h1 = a * h1 + h2;
            h2 = aux;
            aux = k1;
            k1 = a * k1 + k2;
            k2 = aux;
            b = 1.0 / (b - a);
        } while (Math.abs(fractionalPart - h1 / k1) > fractionalPart * tolerance && k1 < 100000);

        long num = (long) h1;
        long den = (long) k1;

        if (den == 0) {
            return String.valueOf(sign * wholePart);
        }

        long totalNum = (wholePart * den + num) * sign;
        return totalNum + "/" + den;
    }
}
