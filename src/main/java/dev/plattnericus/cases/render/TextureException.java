package dev.plattnericus.cases.render;

/** Thrown when a PNG layer is missing, unreadable or unusable. The message names the file and problem. */
public final class TextureException extends Exception {

    private static final long serialVersionUID = 1L;

    public TextureException(String message) {
        super(message);
    }
}
