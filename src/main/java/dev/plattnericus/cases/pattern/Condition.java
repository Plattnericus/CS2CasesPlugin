package dev.plattnericus.cases.pattern;

import java.util.Map;

/** One comparison such as {@code playside.blue >= 0.62}. */
public record Condition(String metric, Op op, double value) {

    public enum Op {
        GE(">="), LE("<="), GT(">"), LT("<");

        final String symbol;

        Op(String symbol) {
            this.symbol = symbol;
        }
    }

    public boolean test(Map<String, Double> metrics) {
        Double v = metrics.get(metric);
        if (v == null) {
            return false;
        }
        return switch (op) {
            case GE -> v >= value;
            case LE -> v <= value;
            case GT -> v > value;
            case LT -> v < value;
        };
    }

    /** Parses "metric op number"; throws IllegalArgumentException with a readable message. */
    public static Condition parse(String text) {
        String t = text.trim();
        for (Op op : new Op[]{Op.GE, Op.LE, Op.GT, Op.LT}) {
            int idx = t.indexOf(op.symbol);
            if (idx > 0) {
                String metric = t.substring(0, idx).trim();
                String number = t.substring(idx + op.symbol.length()).trim();
                try {
                    return new Condition(metric, op, Double.parseDouble(number));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("'" + number + "' is not a number in condition '" + text + "'");
                }
            }
        }
        throw new IllegalArgumentException("condition '" + text + "' needs one of >=, <=, >, <");
    }

    @Override
    public String toString() {
        return metric + " " + op.symbol + " " + value;
    }
}
