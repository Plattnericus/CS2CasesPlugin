package dev.plattnericus.cases.opening;

/** Centered rows for one through nine reels, with uniform whole-scene enlargement. */
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
        // The unit layout fits a 70-degree, 4:3 camera, even near a wall. Scale the
        // entire layout afterwards so adding reels cannot cancel the requested enlargement.
        double scale = Math.min(1, Math.min(distance * 1.55 / (columns * (width + gapX)),
                distance * 1.02 / (rows * (height + gapY)))) * sceneScale;
        int row = index / columns;
        int inRow = Math.min(columns, count - row * columns);
        return new Cell((index % columns - (inRow - 1) / 2.0) * (width + gapX) * scale,
                ((rows - 1) / 2.0 - row) * (height + gapY) * scale, scale);
    }
}
