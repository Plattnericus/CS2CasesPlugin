package dev.plattnericus.cases.opening;

/** Camera-safe, centered rows for one through nine independent reels. */
public final class OpeningLayout {
    private OpeningLayout() { }
    public record Cell(double right, double up, double scale) { }

    public static Cell cell(int index, int count, double width, double height, double distance) {
        if (count < 1 || count > 9 || index < 0 || index >= count
                || !Double.isFinite(width) || !Double.isFinite(height) || !Double.isFinite(distance)
                || width <= 0 || height <= 0 || distance <= 0) throw new IllegalArgumentException("invalid reel layout");
        int columns = (int) Math.ceil(Math.sqrt(count));
        int rows = (count + columns - 1) / columns;
        double gapX = .24, gapY = .20;
        // Fit even a 70-degree, 4:3 camera, including a wall pulling the scene closer.
        double scale = Math.min(1, Math.min(distance * 1.55 / (columns * (width + gapX)),
                distance * 1.02 / (rows * (height + gapY))));
        int row = index / columns;
        int inRow = Math.min(columns, count - row * columns);
        return new Cell((index % columns - (inRow - 1) / 2.0) * (width + gapX) * scale,
                ((rows - 1) / 2.0 - row) * (height + gapY) * scale, scale);
    }
}
