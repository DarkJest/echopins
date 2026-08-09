package dev.echopins.domain.error;

/**
 * A failure that has a defined, translatable player-facing meaning.
 *
 * <p>Application services throw this instead of returning error codes so that a validation
 * failure cannot be accidentally ignored. The message carries technical context for the log; the
 * player only ever sees the {@link EchoPinError}.
 */
public class EchoPinException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient EchoPinError error;
    private final long argument;
    private final boolean hasArgument;

    public EchoPinException(EchoPinError error, String technicalMessage) {
        super(technicalMessage);
        this.error = error;
        this.argument = 0L;
        this.hasArgument = false;
    }

    /**
     * Carries a numeric detail to the player alongside the error, for example how many
     * milliseconds remain on a cooldown.
     *
     * <p>Messages whose translation contains a placeholder <em>must</em> be raised this way.
     * Minecraft does not fail loudly on a missing argument - it catches the formatting error and
     * renders the raw template - so the player would simply be shown a literal {@code %s}.
     */
    public EchoPinException(EchoPinError error, long argument, String technicalMessage) {
        super(technicalMessage);
        this.error = error;
        this.argument = argument;
        this.hasArgument = true;
    }

    public EchoPinException(EchoPinError error, String technicalMessage, Throwable cause) {
        super(technicalMessage, cause);
        this.error = error;
        this.argument = 0L;
        this.hasArgument = false;
    }

    public EchoPinError error() {
        return error == null ? EchoPinError.UNKNOWN : error;
    }

    public boolean hasArgument() {
        return hasArgument;
    }

    /** Only meaningful when {@link #hasArgument()} is true. */
    public long argument() {
        return argument;
    }
}
