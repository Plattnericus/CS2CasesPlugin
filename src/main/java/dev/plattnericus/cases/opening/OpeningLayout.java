package dev.plattnericus.cases.opening;

/** Centered rows with one fixed wheel size that also fits the complete nine-wheel grid. */
public final class OpeningLayout {
    private OpeningLayout() { }
    public record Cell(double right, double up, double scale) { }

    public static Cell cell(int index, int count, double width, double height, double distance) {
        return cell(index, count, width, height, distance, 1);
    }

    public static Cell cell(int index, int count, double width, double height, double distance, double sceneScale) {
        if (count < 1 || count > 9 || index < 0 || index >= count
                || !Double.isFinite(width) || !Double.isFinite(height) || !Double.isFinite(distance)
                || !Double.isFinite(sceneScale) || sceneScale <= 0
                || width <= 0 || height <= 0 || distance <= 0) throw new IllegalArgumentException("invalid reel layout");
        int columns = (int) Math.ceil(Math.sqrt(count));
        int rows = (count + columns - 1) / columns;
        double gapX = .24, gapY = .20;
        // Size against the largest supported grid, not the number currently visible.
        // This leaves camera margins at 70 degrees / 4:3 and prevents a single wheel
        // growing to three times its batch size. The configured size is an upper bound.
        double scale = Math.min(sceneScale, Math.min(distance * 1.55 / (3 * (width + gapX)),
                distance * 1.02 / (3 * (height + gapY))));
        int row = index / columns;
        int inRow = Math.min(columns, count - row * columns);
        return new Cell((index % columns - (inRow - 1) / 2.0) * (width + gapX) * scale,
                ((rows - 1) / 2.0 - row) * (height + gapY) * scale, scale);
    }
}
