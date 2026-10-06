package dev.plattnericus.cases.tools;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Generates the default grayscale pattern textures. Each texture is a luminance field that the
 * finish palette later maps to colors, so one texture serves many finishes. All textures except
 * {@code fade} tile seamlessly, because the renderer wraps pattern coordinates.
 */
final class PatternTextureGenerator {

    static final int N = 512;

    private final TileNoise noise = new TileNoise(0x5EEDL);
    private final TileNoise noise2 = new TileNoise(0xC0FFEEL);
    private final TileNoise noise3 = new TileNoise(0xBADA55L);

    Map<String, float[]> generateAll() {
        Map<String, Supplier<float[]>> generators = new LinkedHashMap<>();
        generators.put("camo", this::camo);
        generators.put("digital", this::digital);
        generators.put("stripes", this::stripes);
        generators.put("carbon", this::carbon);
        generators.put("hex", this::hex);
        generators.put("cells", () -> voronoi(7, 0.12, false));
        generators.put("shards", () -> voronoi(4, 0.06, true));
        generators.put("splatter", this::splatter);
        generators.put("tiger", this::tiger);
        generators.put("marble", this::marble);
        generators.put("case_hardened", this::caseHardened);
        generators.put("fade", this::fade);
        generators.put("doppler", this::doppler);
        generators.put("damascus", this::damascus);
        generators.put("web", this::web);
        generators.put("scales", this::scales);
        generators.put("circuit", this::circuit);
        generators.put("floral", this::floral);
        generators.put("waves", this::waves);
        generators.put("topo", this::topo);
        generators.put("flames", this::flames);
        generators.put("halftone", this::halftone);
        generators.put("brushed", this::brushed);
        generators.put("plaid", this::plaid);
        generators.put("mesh", this::mesh);
        generators.put("lightning", this::lightning);
        generators.put("glitch", this::glitch);
        generators.put("graffiti", this::graffiti);
        generators.put("swirl", this::swirl);
        generators.put("solid", this::solid);
        generators.put("tribal", this::tribal);
        generators.put("chevron", this::chevron);
        Map<String, float[]> out = new LinkedHashMap<>();
        generators.forEach((name, gen) -> out.put(name, gen.get()));
        return out;
    }

    // ---------------------------------------------------------------- helpers

    private interface PixelFn {
        double at(int x, int y);
    }

    private static float[] field(PixelFn fn) {
        float[] f = new float[N * N];
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                f[y * N + x] = (float) clamp(fn.at(x, y));
            }
        }
        return f;
    }

    static double clamp(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static double smoothstep(double a, double b, double v) {
        double t = clamp((v - a) / (b - a));
        return t * t * (3 - 2 * t);
    }

    /** Rank-based equalisation so palette steps cover equal areas. */
    static float[] equalize(float[] f) {
        float[] sorted = f.clone();
        Arrays.sort(sorted);
        float[] out = new float[f.length];
        for (int i = 0; i < f.length; i++) {
            int idx = Arrays.binarySearch(sorted, f[i]);
            if (idx < 0) {
                idx = -idx - 1;
            }
            out[i] = idx / (float) (f.length - 1);
        }
        return out;
    }

    private static double wrapDelta(double d) {
        d = d % N;
        if (d > N / 2.0) {
            d -= N;
        } else if (d < -N / 2.0) {
            d += N;
        }
        return d;
    }

    private double warped(int x, int y, double strength, int cells) {
        double wx = noise2.fbm(x, y, N, cells, 3, 0.5) - 0.5;
        double wy = noise3.fbm(x, y, N, cells, 3, 0.5) - 0.5;
        return noise.fbm(x + wx * strength, y + wy * strength, N, cells, 5, 0.55);
    }

    // ---------------------------------------------------------------- textures

    private float[] camo() {
        return equalize(field((x, y) -> noise.fbm(x, y, N, 4, 5, 0.5)));
    }

    private float[] digital() {
        int block = 16;
        return equalize(field((x, y) -> {
            int bx = (x / block) * block + block / 2;
            int by = (y / block) * block + block / 2;
            return noise.fbm(bx, by, N, 4, 3, 0.5) * 0.85 + noise.cell(x / block, y / block, 3) * 0.15;
        }));
    }

    private float[] stripes() {
        return field((x, y) -> {
            double t = (x + y) / (double) N * 6 + (noise.fbm(x, y, N, 2, 3, 0.5) - 0.5) * 1.2;
            return 0.5 + 0.5 * Math.sin(2 * Math.PI * t);
        });
    }

    private float[] carbon() {
        int c = 16;
        return field((x, y) -> {
            int cx = x / c;
            int cy = y / c;
            double lx = (x % c) / (double) c;
            double ly = (y % c) / (double) c;
            boolean horizontal = ((cx + cy) & 1) == 0;
            double t = horizontal ? ly : lx;
            double thread = Math.sin(Math.PI * t);
            double weave = horizontal ? Math.sin(Math.PI * lx) : Math.sin(Math.PI * ly);
            return 0.15 + 0.6 * thread * (0.6 + 0.4 * weave) + noise.fbm(x, y, N, 64, 1, 0.5) * 0.1;
        });
    }

    private float[] hex() {
        int cols = 16;
        int rows = 18;
        double w = (double) N / cols;
        double h = (double) N / rows;
        return field((x, y) -> {
            int row = (int) Math.floor(y / h);
            double best = Double.MAX_VALUE;
            double second = Double.MAX_VALUE;
            int bestCol = 0;
            int bestRow = 0;
            for (int dr = -1; dr <= 1; dr++) {
                int r = row + dr;
                double offset = (Math.floorMod(r, rows) % 2) * w / 2;
                int col = (int) Math.floor((x - offset) / w);
                for (int dc = -1; dc <= 1; dc++) {
                    int cc = col + dc;
                    double cxp = cc * w + offset + w / 2;
                    double cyp = r * h + h / 2;
                    double dx = wrapDelta(x - cxp);
                    double dy = wrapDelta(y - cyp) * 1.08;
                    double d = Math.sqrt(dx * dx + dy * dy);
                    if (d < best) {
                        second = best;
                        best = d;
                        bestCol = Math.floorMod(cc, cols);
                        bestRow = Math.floorMod(r, rows);
                    } else if (d < second) {
                        second = d;
                    }
                }
            }
            double edge = smoothstep(0.5, 2.5, second - best);
            double v = 0.25 + 0.75 * noise.cell(bestCol, bestRow, 11);
            return v * edge;
        });
    }

    private float[] voronoi(int grid, double edgeWidth, boolean flatShards) {
        double cell = (double) N / grid;
        double[][] px = new double[grid][grid];
        double[][] py = new double[grid][grid];
        double[][] val = new double[grid][grid];
        for (int i = 0; i < grid; i++) {
            for (int j = 0; j < grid; j++) {
                px[i][j] = (i + 0.15 + 0.7 * noise.cell(i, j, 1)) * cell;
                py[i][j] = (j + 0.15 + 0.7 * noise.cell(i, j, 2)) * cell;
                val[i][j] = noise.cell(i, j, flatShards ? 5 : 9);
            }
        }
        return field((x, y) -> {
            int gx = (int) (x / cell);
            int gy = (int) (y / cell);
            double best = Double.MAX_VALUE;
            double second = Double.MAX_VALUE;
            double v = 0;
            for (int di = -2; di <= 2; di++) {
                for (int dj = -2; dj <= 2; dj++) {
                    int i = Math.floorMod(gx + di, grid);
                    int j = Math.floorMod(gy + dj, grid);
                    double dx = wrapDelta(x - px[i][j]);
                    double dy = wrapDelta(y - py[i][j]);
                    double d = Math.sqrt(dx * dx + dy * dy);
                    if (d < best) {
                        second = best;
                        best = d;
                        v = val[i][j];
                    } else if (d < second) {
                        second = d;
                    }
                }
            }
            double edge = smoothstep(0, edgeWidth * cell * 0.25, second - best);
            double shade = flatShards ? 0 : (1 - best / cell) * 0.15;
            return (v * 0.85 + shade) * (0.25 + 0.75 * edge);
        });
    }

    private float[] splatter() {
        float[] f = new float[N * N];
        Arrays.fill(f, 0f);
        java.util.SplittableRandom r = new java.util.SplittableRandom(42);
        for (int s = 0; s < 70; s++) {
            double cx = r.nextDouble() * N;
            double cy = r.nextDouble() * N;
            double radius = 6 + Math.pow(r.nextDouble(), 2) * 46;
            float level = (float) (0.33 + 0.33 * r.nextInt(3));
            int salt = s;
            stamp(f, cx, cy, radius * 1.6, (dx, dy) -> {
                double ang = Math.atan2(dy, dx);
                double wobble = 1 + 0.35 * Math.sin(ang * 5 + salt) + 0.2 * Math.sin(ang * 11 + salt * 3);
                double d = Math.sqrt(dx * dx + dy * dy);
                return d < radius * wobble ? level : -1;
            });
            int drops = 3 + r.nextInt(6);
            for (int k = 0; k < drops; k++) {
                double a = r.nextDouble() * Math.PI * 2;
                double dist = radius * (1.3 + r.nextDouble());
                double dr = 1.5 + r.nextDouble() * radius * 0.18;
                stamp(f, cx + Math.cos(a) * dist, cy + Math.sin(a) * dist, dr + 1, (dx, dy) ->
                        dx * dx + dy * dy < dr * dr ? level : -1);
            }
        }
        return f;
    }

    private interface Stamp {
        double at(double dx, double dy);
    }

    private static void stamp(float[] f, double cx, double cy, double extent, Stamp s) {
        int x0 = (int) Math.floor(cx - extent);
        int x1 = (int) Math.ceil(cx + extent);
        int y0 = (int) Math.floor(cy - extent);
        int y1 = (int) Math.ceil(cy + extent);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                double v = s.at(x - cx, y - cy);
                if (v >= 0) {
                    f[Math.floorMod(y, N) * N + Math.floorMod(x, N)] = (float) v;
                }
            }
        }
    }

    private float[] tiger() {
        return field((x, y) -> {
            double t = x / (double) N * 9 + (noise.fbm(x, y, N, 3, 4, 0.55) - 0.5) * 3.2;
            double s = Math.sin(2 * Math.PI * t);
            double taper = noise2.fbm(x, y, N, 4, 2, 0.5);
            return smoothstep(0.35 - taper * 0.5, 0.55 - taper * 0.5, s);
        });
    }

    private float[] marble() {
        return equalize(field((x, y) -> {
            double w = warped(x, y, 260, 3);
            return 0.5 + 0.5 * Math.sin(2 * Math.PI * (w * 3.2 + x / (double) N));
        }));
    }

    private float[] caseHardened() {
        return equalize(field((x, y) -> {
            double base = warped(x, y, 180, 3);
            double fine = noise3.fbm(x, y, N, 16, 3, 0.5);
            return base * 0.85 + fine * 0.15;
        }));
    }

    private float[] fade() {
        return field((x, y) -> {
            double t = x / (double) (N - 1);
            double wobble = (noise.fbm(x, y, N, 4, 2, 0.5) - 0.5) * 0.04;
            return t + wobble;
        });
    }

    private float[] doppler() {
        return equalize(field((x, y) -> {
            double w = warped(x, y, 320, 4);
            double grain = noise3.fbm(x, y, N, 64, 2, 0.5);
            return w * 0.9 + grain * 0.1;
        }));
    }

    private float[] damascus() {
        return field((x, y) -> {
            double t = y / (double) N * 22 + (noise.fbm(x, y, N, 3, 4, 0.5) - 0.5) * 5;
            return 0.5 + 0.5 * Math.sin(2 * Math.PI * t);
        });
    }

    private float[] web() {
        double[][] centers = {{110, 120}, {380, 300}, {200, 430}, {440, 60}};
        return field((x, y) -> {
            double v = 0;
            for (double[] c : centers) {
                double dx = wrapDelta(x - c[0]);
                double dy = wrapDelta(y - c[1]);
                double r = Math.sqrt(dx * dx + dy * dy);
                if (r > 200) {
                    continue;
                }
                double a = Math.atan2(dy, dx);
                int spokes = 14;
                double sector = 2 * Math.PI / spokes;
                double local = ((a % sector) + sector) % sector;
                double spokeDist = Math.min(local, sector - local) * r;
                double sag = Math.cos(local - sector / 2) / Math.cos(sector / 2);
                double ring = (r * sag) % 22;
                double ringDist = Math.min(ring, 22 - ring);
                double line = Math.max(1 - smoothstep(0.4, 1.6, spokeDist), 1 - smoothstep(0.4, 1.6, ringDist));
                v = Math.max(v, line * (1 - smoothstep(150, 200, r)));
            }
            return v;
        });
    }

    private float[] scales() {
        double s = N / 16.0;
        double rowStep = s * 0.5;
        int rows = (int) Math.round(N / rowStep);
        return field((x, y) -> {
            int row = (int) Math.floor(y / rowStep);
            // lower rows overlap the rows above them, so test them first
            for (int r = row + 2; r >= row - 1; r--) {
                double off = (Math.floorMod(r, rows) % 2) * s / 2;
                int col = (int) Math.floor((x - off) / s);
                for (int dc = -1; dc <= 1; dc++) {
                    double cx = (col + dc) * s + off + s / 2;
                    double cy = r * rowStep;
                    double dx = wrapDelta(x - cx);
                    double dy = wrapDelta(y - cy);
                    if (dy > 0) {
                        continue;
                    }
                    double d = Math.sqrt(dx * dx + dy * dy) / (s * 0.72);
                    if (d < 1) {
                        double rim = d > 0.86 ? 0.25 : 0;
                        return clamp(0.3 + 0.65 * d - rim);
                    }
                }
            }
            return 0.15;
        });
    }

    private float[] circuit() {
        int c = 32;
        int cells = N / c;
        return field((x, y) -> {
            int cx = x / c;
            int cy = y / c;
            double lx = x % c;
            double ly = y % c;
            boolean h = noise.cell(cx, cy, 21) > 0.45;
            boolean v = noise.cell(cx, cy, 22) > 0.55;
            boolean hRight = noise.cell(Math.floorMod(cx + 1, cells), cy, 21) > 0.45;
            boolean vDown = noise.cell(cx, Math.floorMod(cy + 1, cells), 22) > 0.55;
            double trace = 0;
            if (h && Math.abs(ly - c / 2.0) < 2.2 && lx >= c / 2.0 - 2) {
                trace = 1;
            }
            if (hRight && Math.abs(ly - c / 2.0) < 2.2 && lx <= c / 2.0 + 2) {
                trace = Math.max(trace, h ? 1 : 0);
            }
            if (v && Math.abs(lx - c / 2.0) < 2.2 && ly >= c / 2.0 - 2) {
                trace = 1;
            }
            double dx = lx - c / 2.0;
            double dy = ly - c / 2.0;
            double pad = Math.sqrt(dx * dx + dy * dy);
            if ((h && v) || (h && vDown)) {
                if (pad < 5.5) {
                    trace = pad < 2.5 ? 0.45 : 1;
                }
            }
            return trace > 0 ? trace : 0.12 + noise.fbm(x, y, N, 32, 2, 0.5) * 0.12;
        });
    }

    private float[] floral() {
        int grid = 6;
        double cell = (double) N / grid;
        return field((x, y) -> {
            double v = 0.12 + noise.fbm(x, y, N, 8, 2, 0.5) * 0.1;
            int gx = (int) (x / cell);
            int gy = (int) (y / cell);
            for (int di = -1; di <= 1; di++) {
                for (int dj = -1; dj <= 1; dj++) {
                    int i = Math.floorMod(gx + di, grid);
                    int j = Math.floorMod(gy + dj, grid);
                    double cx = (i + 0.25 + 0.5 * noise.cell(i, j, 31)) * cell;
                    double cy = (j + 0.25 + 0.5 * noise.cell(i, j, 32)) * cell;
                    double dx = wrapDelta(x - cx);
                    double dy = wrapDelta(y - cy);
                    double r = Math.sqrt(dx * dx + dy * dy);
                    double a = Math.atan2(dy, dx) + noise.cell(i, j, 33) * 6;
                    double size = cell * (0.28 + 0.14 * noise.cell(i, j, 34));
                    double petal = size * (0.55 + 0.45 * Math.abs(Math.cos(a * 2.5)));
                    if (r < size * 0.22) {
                        v = 1;
                    } else if (r < petal) {
                        v = Math.max(v, 0.5 + 0.3 * (1 - r / petal));
                    }
                }
            }
            return v;
        });
    }

    private float[] waves() {
        return field((x, y) -> {
            double t = y / (double) N * 7 + 0.18 * Math.sin(2 * Math.PI * x / N * 3) + (noise.fbm(x, y, N, 3, 3, 0.5) - 0.5) * 0.6;
            return 0.5 + 0.5 * Math.sin(2 * Math.PI * t);
        });
    }

    private float[] topo() {
        return field((x, y) -> {
            double f = noise.fbm(x, y, N, 3, 4, 0.5) * 16;
            double frac = f - Math.floor(f);
            double line = 1 - smoothstep(0.03, 0.11, Math.min(frac, 1 - frac));
            return Math.max(line, 0.18 + 0.1 * (f / 16));
        });
    }

    private float[] flames() {
        return equalize(field((x, y) -> {
            double rise = 0.5 - 0.5 * Math.cos(2 * Math.PI * y / N);
            double tongues = noise.fbm(x, y * 0.6, N, 6, 4, 0.55);
            return rise * 0.55 + tongues * 0.45;
        }));
    }

    private float[] halftone() {
        int c = 16;
        return field((x, y) -> {
            int cx = x / c;
            int cy = y / c;
            double size = noise.fbm(cx * c + c / 2.0, cy * c + c / 2.0, N, 3, 3, 0.5);
            double dx = (x % c) - c / 2.0;
            double dy = (y % c) - c / 2.0;
            double r = Math.sqrt(dx * dx + dy * dy);
            double radius = size * c * 0.62;
            return r < radius ? 0.95 : 0.1;
        });
    }

    private float[] brushed() {
        return equalize(field((x, y) -> {
            double streak = noise.value(x * 4.0 / N, y * 256.0 / N, 4) * 0.4
                    + noise2.value(x * 8.0 / N, y * 128.0 / N, 8) * 0.4
                    + noise3.value(x * 32.0 / N, y * 512.0 / N, 32) * 0.2;
            return streak;
        }));
    }

    private float[] plaid() {
        return field((x, y) -> {
            double sx = smoothstep(0.2, 0.3, 0.5 + 0.5 * Math.sin(2 * Math.PI * x / N * 8))
                    * 0.6 + (Math.abs(Math.sin(2 * Math.PI * x / N * 32)) > 0.9 ? 0.4 : 0);
            double sy = smoothstep(0.2, 0.3, 0.5 + 0.5 * Math.sin(2 * Math.PI * y / N * 8))
                    * 0.6 + (Math.abs(Math.sin(2 * Math.PI * y / N * 32)) > 0.9 ? 0.4 : 0);
            return (sx + sy) / 2;
        });
    }

    private float[] mesh() {
        float[] camo = camo();
        return field((x, y) -> {
            double a = ((x + y) % 24) / 24.0;
            double b = ((x - y + N) % 24) / 24.0;
            boolean line = a < 0.12 || b < 0.12;
            double base = camo[y * N + x] * 0.7;
            return line ? 1 : base;
        });
    }

    private float[] lightning() {
        float[] f = new float[N * N];
        for (int i = 0; i < f.length; i++) {
            f[i] = (float) (noise.fbm(i % N, i / N, N, 4, 3, 0.5) * 0.25);
        }
        java.util.SplittableRandom r = new java.util.SplittableRandom(7);
        for (int bolt = 0; bolt < 9; bolt++) {
            double x = r.nextDouble() * N;
            double y = r.nextDouble() * N;
            double dir = r.nextDouble() * Math.PI * 2;
            int len = 60 + r.nextInt(90);
            for (int s = 0; s < len; s++) {
                dir += (r.nextDouble() - 0.5) * 0.9;
                double nx = x + Math.cos(dir) * 3;
                double ny = y + Math.sin(dir) * 3;
                double width = 2.2 * (1 - s / (double) len) + 0.6;
                for (double t = 0; t <= 1; t += 0.34) {
                    double px = x + (nx - x) * t;
                    double py = y + (ny - y) * t;
                    stamp(f, px, py, width + 3, (dx, dy) -> {
                        double d = Math.sqrt(dx * dx + dy * dy);
                        if (d < width) {
                            return 1;
                        }
                        return -1;
                    });
                }
                x = nx;
                y = ny;
            }
        }
        return f;
    }

    private float[] glitch() {
        return field((x, y) -> {
            int band = (int) (noise.value(0.5, y / 8.0, N / 8) * 1000) % 7;
            double shift = noise.cell(band, y / 12, 41) * N;
            double base = noise.fbm((x + shift) % N, y, N, 8, 2, 0.5);
            double bars = noise.cell((int) ((x + shift) % N) / 32, y / 6, 42) > 0.82 ? 1 : 0;
            return Math.max(base * 0.7, bars);
        });
    }

    private float[] graffiti() {
        return field((x, y) -> {
            double v = noise.fbm(x, y, N, 4, 4, 0.5);
            double fill = smoothstep(0.52, 0.54, v);
            double outline = 1 - smoothstep(0.004, 0.012, Math.abs(v - 0.505));
            double inner = smoothstep(0.6, 0.62, v);
            double base = 0.08 + noise2.fbm(x, y, N, 8, 2, 0.5) * 0.1;
            double out = base;
            if (fill > 0) {
                out = 0.55 + inner * 0.2;
            }
            return Math.max(out, outline);
        });
    }

    private float[] swirl() {
        return equalize(field((x, y) -> {
            double a = noise.fbm(x, y, N, 3, 4, 0.5);
            double b = noise2.fbm(x, y, N, 2, 3, 0.5);
            return 0.5 + 0.5 * Math.sin(12 * a + 7 * b);
        }));
    }

    private float[] solid() {
        return field((x, y) -> 0.45 + noise.fbm(x, y, N, 16, 3, 0.5) * 0.1);
    }

    private float[] tribal() {
        return field((x, y) -> {
            double w = warped(x, y, 120, 2);
            double s = Math.abs(Math.sin(2 * Math.PI * w * 4));
            return smoothstep(0.55, 0.62, s) * 0.85 + 0.1;
        });
    }

    private float[] chevron() {
        return field((x, y) -> {
            double period = N / 8.0;
            double tri = Math.abs(((x % period) / period) - 0.5) * 2;
            double t = y / (double) N * 10 + tri * 1.2;
            return 0.5 + 0.5 * Math.signum(Math.sin(2 * Math.PI * t)) * 0.9;
        });
    }
}
