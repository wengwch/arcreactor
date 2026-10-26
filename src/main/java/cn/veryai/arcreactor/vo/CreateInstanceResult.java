package cn.veryai.arcreactor.vo;

/** The workflow has been accepted; provisioning continues asynchronously. */
public record CreateInstanceResult(String instanceId, String requestId, String username,
        String password, String jupyterToken) {
    @Override
    public String toString() {
        return "CreateInstanceResult[instanceId=" + instanceId + ", requestId=" + requestId
                + ", username=" + username + ", password=<redacted>, jupyterToken=<redacted>]";
    }
}
