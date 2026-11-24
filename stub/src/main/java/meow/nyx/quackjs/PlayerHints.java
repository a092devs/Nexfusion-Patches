package meow.nyx.quackjs;

public final class PlayerHints {
    private final String signatureExpression;
    private final String nClass;
    private final int signatureTimestamp;

    public PlayerHints(String signatureExpression, String nClass, int signatureTimestamp) {
        this.signatureExpression = signatureExpression;
        this.nClass = nClass;
        this.signatureTimestamp = signatureTimestamp;
    }

    public String getSignatureExpression() {
        return signatureExpression;
    }

    public String getNClass() {
        return nClass;
    }

    public int getSignatureTimestamp() {
        return signatureTimestamp;
    }

    @Override
    public String toString() {
        return "PlayerHints(" + signatureExpression + ", " + nClass + ", " + signatureTimestamp + ")";
    }
}
