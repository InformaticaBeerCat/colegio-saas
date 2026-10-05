package cl.colegiosaas.site;

/** El diseño no pasa la revisión (contraste AA o fuentes): no se guarda. */
public class DesignRejectedException extends RuntimeException {

    private final transient DesignReview review;

    public DesignRejectedException(DesignReview review) {
        super(String.join(". ", review.messages()));
        this.review = review;
    }

    public DesignReview review() {
        return review;
    }
}
