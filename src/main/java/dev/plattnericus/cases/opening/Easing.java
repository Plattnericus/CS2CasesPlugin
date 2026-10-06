package dev.plattnericus.cases.opening;

import java.util.function.DoubleUnaryOperator;

/** Ease-out curves for the reel. All map [0,1] to [0,1] with f(0)=0, f(1)=1. */
public enum Easing {
    // Integrated t(1-t)^3 velocity: a short acceleration followed by a long, smooth brake.
    CINEMATIC(t -> t * t * (10 + t * (-20 + t * (15 - 4 * t)))),
    CUBIC(t -> 1 - Math.pow(1 - t, 3)),
    QUART(t -> 1 - Math.pow(1 - t, 4)),
    QUINT(t -> 1 - Math.pow(1 - t, 5)),
    EXPO(t -> t >= 1 ? 1 : 1 - Math.pow(2, -10 * t));

    private final DoubleUnaryOperator fn;

    Easing(DoubleUnaryOperator fn) {
        this.fn = fn;
    }

    public double apply(double t) {
        return fn.applyAsDouble(Math.max(0, Math.min(1, t)));
    }

    public static Easing parse(String name) {
        for (Easing e : values()) {
            if (e.name().equalsIgnoreCase(name)) {
                return e;
            }
        }
        return CUBIC;
    }
}
