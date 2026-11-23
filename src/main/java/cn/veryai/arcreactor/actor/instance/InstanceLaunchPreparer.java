package cn.veryai.arcreactor.actor.instance;

import cn.veryai.arcreactor.actor.instance.model.InstanceSpec;
import cn.veryai.arcreactor.actor.instance.model.InstanceWorkflowState;
import cn.veryai.arcreactor.actor.instance.model.PreparedNetwork;
import cn.veryai.arcreactor.actor.instance.model.PreparedShare;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import cn.veryai.arcreactor.openstack.OpenStackOperationException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/** Prepares cloud-init and the Nova creation request without performing workflow or cloud operations. */
final class InstanceLaunchPreparer {
  private static final Executor CLOUD_INIT_EXECUTOR =
      task -> Thread.ofVirtual().name("instance-cloud-init").start(task);

  private final String instanceId;
  private final CloudInitRenderer cloudInitRenderer;

  InstanceLaunchPreparer(String instanceId, CloudInitRenderer cloudInitRenderer) {
    this.instanceId = Objects.requireNonNull(instanceId);
    this.cloudInitRenderer = Objects.requireNonNull(cloudInitRenderer);
  }

  CompletionStage<OpenStackClient.CreateServerRequest> prepare(InstanceWorkflowState state) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        return cloudInitRenderer.render(cloudInitModel(state));
      } catch (RuntimeException error) {
        throw new OpenStackOperationException("Cloud-init preparation failed before Nova submission", error);
      }
    }, CLOUD_INIT_EXECUTOR)
        .thenApply(userData -> createServerRequest(state, userData));
  }

  private static Map<String, Object> cloudInitModel(InstanceWorkflowState state) {
    var cloudInit = state.spec().cloudInit();
    String cidr = state.spec().network().userNetwork().ipv4CIDR();
    PreparedShare share = Objects.requireNonNull(state.preparedShare(), "preparedShare");
    PreparedNetwork network = Objects.requireNonNull(state.networkPreparation(), "networkPreparation").resources();
    if (!hasText(network.osTrunkId()) || !hasText(network.osSubPortId())
        || !hasText(network.subMacAddress()) || !hasText(network.subFixedIp())) {
      throw new IllegalStateException("Instance ports and trunk must be ready before rendering cloud-init");
    }
    return Map.ofEntries(
        Map.entry("share_access_key", share.accessKey()),
        Map.entry("share_export_location", share.exportLocationPath()),
        Map.entry("share_access_to", share.accessTo()),
        Map.entry("segmentId", state.spec().network().segmentationId()),
        Map.entry("macaddress", network.subMacAddress()),
        Map.entry("ipv4", network.subFixedIp() + cidr.substring(cidr.lastIndexOf('/'))),
        Map.entry("username", cloudInit.username()),
        Map.entry("password", cloudInit.password()),
        Map.entry("jupyter_token", cloudInit.jupyterToken()),
        Map.entry("hostRoutes", cloudInit.hostRoutes()));
  }

  private OpenStackClient.CreateServerRequest createServerRequest(InstanceWorkflowState state, String userData) {
    InstanceSpec spec = state.spec();
    return new OpenStackClient.CreateServerRequest(
        new OpenStackClient.ServerTarget(spec.clusterId(), state.osProjectId(), spec.region()),
        instanceId,
        state.requestId(),
        spec.osImageId(),
        spec.osFlavorId(),
        state.hostname(),
        spec.metadata(),
        state.networkPreparation().resources().osParentPortId(),
        userData);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
