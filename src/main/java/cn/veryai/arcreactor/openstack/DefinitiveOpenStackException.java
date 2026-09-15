package cn.veryai.arcreactor.openstack;

/** A rejected operation that is known not to have succeeded. */
public final class DefinitiveOpenStackException extends RuntimeException {
    public DefinitiveOpenStackException(String message) {
        super(message);
    }

    public DefinitiveOpenStackException(String message, Throwable cause) {
        super(message, cause);
    }
}
