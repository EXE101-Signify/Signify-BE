package fptu.exe202.signify.signifybe.features.ai.infrastructure;

/** A valid image that produced no usable alphabet prediction. */
public final class AiFrameRejectedException extends RuntimeException {
    private final boolean noHand;

    public AiFrameRejectedException(boolean noHand) {
        this.noHand = noHand;
    }

    public boolean isNoHand() {
        return noHand;
    }
}
