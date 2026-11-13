package dev.plattnericus.cases.opening;

/** Places up to nine reels in a centered grid, sized for the number of reels currently shown. */
public final class OpeningLayout {
    private static final double GAP_X = .27, GAP_Y = .20;

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
        // Share of the view (70 degrees, 16:9) the grid may fill: a single reel stays compact, nine fill most of it.
        double widthBudget = Math.min(1.7, 1.6 + .45 * (columns - 1));
        double heightBudget = Math.min(.95, .8 + .25 * (rows - 1));
        double scale = Math.min(sceneScale, Math.min(distance * widthBudget / (columns * (width + GAP_X)),
                distance * heightBudget / (rows * (height + GAP_Y))));
        int row = index / columns;
        int inRow = Math.min(columns, count - row * columns);
        return new Cell((index % columns - (inRow - 1) / 2.0) * (width + GAP_X) * scale,
                ((rows - 1) / 2.0 - row) * (height + GAP_Y) * scale, scale);
    }
}
