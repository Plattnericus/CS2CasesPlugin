package dev.plattnericus.cases.render;

/** Final composited skin image plus the pre-wear paint used for pattern analysis. */
public record RenderedSkin(ArgbImage image, PaintSample paint) {
}
