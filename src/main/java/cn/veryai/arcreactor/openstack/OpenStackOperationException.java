package cn.veryai.arcreactor.openstack;

/** Reports a definitive failure from a high-level OpenStack service operation. */
public final class OpenStackOperationException extends RuntimeException {
    public OpenStackOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
