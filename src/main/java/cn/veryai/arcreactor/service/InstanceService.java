package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.actor.PekkoRuntime;
import cn.veryai.arcreactor.actor.instance.CloudInitRenderer;
import cn.veryai.arcreactor.actor.instance.command.CreateInstance;
import cn.veryai.arcreactor.actor.instance.command.GetInstanceState;
import cn.veryai.arcreactor.actor.instance.command.WorkflowCommand;
import cn.veryai.arcreactor.actor.instance.model.CloudInitSpec;
import cn.veryai.arcreactor.actor.instance.model.ForwardingRequirement;
import cn.veryai.arcreactor.actor.instance.model.InstanceSpec;
import cn.veryai.arcreactor.actor.instance.model.InstanceWorkflowState;
import cn.veryai.arcreactor.actor.instance.model.NetworkRequirement;
import cn.veryai.arcreactor.actor.instance.model.ShareRequirement;
import cn.veryai.arcreactor.actor.instance.reply.InstanceReply;
import cn.veryai.arcreactor.actor.user.model.NetworkSpec;
import cn.veryai.arcreactor.actor.user.model.ShareSpec;
import cn.veryai.arcreactor.config.InstanceCreationProperties;
import cn.veryai.arcreactor.entity.FlavorEntity;
import cn.veryai.arcreactor.entity.FloatingIpEntity;
import cn.veryai.arcreactor.entity.HypervisorTypeEntity;
import cn.veryai.arcreactor.entity.ImageEntity;
import cn.veryai.arcreactor.entity.RegionEntity;
import cn.veryai.arcreactor.exceptions.BaseException;
import cn.veryai.arcreactor.i18n.ErrorCode;
import cn.veryai.arcreactor.repo.FlavorRepo;
import cn.veryai.arcreactor.repo.FloatingIpRepo;
import cn.veryai.arcreactor.repo.HypervisorTypeRepo;
import cn.veryai.arcreactor.repo.ImageRepo;
import cn.veryai.arcreactor.repo.OpenstackClusterRepo;
import cn.veryai.arcreactor.repo.RegionRepo;
import cn.veryai.arcreactor.vo.CreateInstanceResult;
import cn.veryai.arcreactor.vo.InstanceView;
import cn.veryai.arcreactor.web.params.CreateInstanceParam;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.openstack4j.model.manila.Share;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstanceService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RegionRepo regionRepo;
    private final OpenstackClusterRepo clusterRepo;
    private final FlavorRepo flavorRepo;
    private final ImageRepo imageRepo;
    private final HypervisorTypeRepo hypervisorTypeRepo;
    private final FloatingIpRepo floatingIpRepo;
    private final InstanceCreationProperties creationProperties;
    private final CloudInitRenderer cloudInitRenderer;
    private final PekkoRuntime pekkoRuntime;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CompletionStage<CreateInstanceResult> create(CreateInstanceParam param) {
        RegionEntity region = regionRepo.findById(param.getRegionId());
        if (region == null) throw new BaseException(ErrorCode.REGION_NOT_FOUND);
        if (!region.isEnabled()) throw new BaseException(ErrorCode.INSTANCE_REGION_DISABLED);
        if (clusterRepo.findById(region.getClusterId()) == null) {
            throw new BaseException(ErrorCode.OPENSTACK_CLUSTER_NOT_FOUND);
        }

        FlavorEntity flavor = flavorRepo.findById(param.getFlavorId());
        if (flavor == null) throw new BaseException(ErrorCode.FLAVOR_NOT_FOUND);
        ImageEntity image = imageRepo.findById(param.getImageId());
        if (image == null) throw new BaseException(ErrorCode.IMAGE_NOT_FOUND);
        HypervisorTypeEntity hypervisorType = hypervisorTypeRepo.findById(param.getHypervisorType());
        if (hypervisorType == null) throw new BaseException(ErrorCode.HYPERVISOR_TYPE_NOT_FOUND);
        validateResources(region, flavor, image, hypervisorType);

        String instanceId = UUID.randomUUID().toString();
        String requestId = UUID.randomUUID().toString();
        CreationDefaults defaults = creationDefaults(param, region);
        InstanceSpec spec = new InstanceSpec(region.getClusterId(), region.getId(), param.getName(),
                image.getOsImageId(), flavor.getOsFlavorId(), flavor.getVcpus(), flavor.getRam(), flavor.getDisk(), flavor.getGpus(),
                StringUtils.isBlank(param.getDescription()) ? Map.of() : Map.of("description", param.getDescription()),
                new ShareRequirement(param.getUserId(), defaults.resourceId(), defaults.share()),
                defaults.forwarding(), defaults.network(), defaults.cloudInit(), param.getHypervisorType());
        return submit(requestId, instanceId, spec);
    }

    public CompletionStage<InstanceView> get(String instanceId) {
        return AskPattern.<WorkflowCommand, InstanceWorkflowState>ask(
                        pekkoRuntime.instanceActor(instanceId),
                        GetInstanceState::new,
                        pekkoRuntime.getInstanceAskTimeout(),
                        pekkoRuntime.getActorSystem().scheduler())
                .thenApply(state -> {
                    if (state.requestId() == null) throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
                    return InstanceView.from(state);
                });
    }

    private CompletionStage<CreateInstanceResult> submit(String requestId, String instanceId, InstanceSpec spec) {
        return AskPattern.<WorkflowCommand, InstanceReply>ask(
                pekkoRuntime.instanceActor(instanceId),
                replyTo -> new CreateInstance(requestId, instanceId, spec, replyTo),
                pekkoRuntime.getInstanceAskTimeout(),
                pekkoRuntime.getActorSystem().scheduler()).handle((reply, error) -> {
            if (error != null) {
                Throwable cause = error;
                while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) {
                    cause = cause.getCause();
                }
                throw new BaseException(cause instanceof TimeoutException
                        ? ErrorCode.INSTANCE_CREATE_TIMEOUT : ErrorCode.INSTANCE_CREATE_UNCONFIRMED, cause);
            }
            if (!reply.accepted()) {
                log.warn("Instance creation rejected: instanceId={}, requestId={}, detail={}",
                        instanceId, requestId, reply.detail());
                throw new BaseException(ErrorCode.INSTANCE_CREATE_REJECTED);
            }
            CloudInitSpec credentials = spec.cloudInit();
            return new CreateInstanceResult(instanceId, requestId, credentials.username(),
                    credentials.password(), credentials.jupyterToken());
        });
    }

    private void validateResources(RegionEntity region, FlavorEntity flavor, ImageEntity image,
            HypervisorTypeEntity hypervisorType) {
        if (!Objects.equals(region.getId(), flavor.getRegionId())
                || !Objects.equals(region.getId(), image.getRegionId())) {
            throw new BaseException(ErrorCode.INSTANCE_RESOURCE_REGION_MISMATCH);
        }
        if (!Objects.equals(hypervisorType.getId(), flavor.getHypervisorType())) {
            throw new BaseException(ErrorCode.INSTANCE_FLAVOR_TYPE_MISMATCH);
        }
        if (!hypervisorType.isEnabled()) {
            throw new BaseException(ErrorCode.INSTANCE_HYPERVISOR_TYPE_DISABLED);
        }
        if (!flavor.isEnabled() || StringUtils.isBlank(flavor.getOsFlavorId())
                || flavor.getVcpus() <= 0 || flavor.getRam() <= 0 || flavor.getDisk() < 0 || flavor.getGpus() < 0) {
            throw new BaseException(ErrorCode.INSTANCE_FLAVOR_UNAVAILABLE);
        }
        if (!image.isEnabled() || StringUtils.isBlank(image.getOsImageId())) {
            throw new BaseException(ErrorCode.INSTANCE_IMAGE_UNAVAILABLE);
        }
    }

    private CreationDefaults creationDefaults(CreateInstanceParam param, RegionEntity region) {
        // A user's instances reuse the same regional share and network specification.
        String resourceId = param.getUserId();
        List<String> floatingIpIds = floatingIpRepo.findByRegionId(region.getId()).stream()
                .filter(FloatingIpEntity::isEnabled)
                .filter(ip -> StringUtils.isNoneBlank(ip.getOsFloatingIpId(), ip.getFloatingIp(), ip.getOsProjectId()))
                .map(FloatingIpEntity::getOsFloatingIpId).distinct().toList();
        if (floatingIpIds.isEmpty()) throw new BaseException(ErrorCode.INSTANCE_FLOATING_IP_UNAVAILABLE);
        ShareSpec share;
        NetworkRequirement network;
        CloudInitSpec cloudInit;
        ForwardingRequirement forwarding;
        try {
            cloudInitRenderer.validateConfiguration();
            forwarding = new ForwardingRequirement(floatingIpIds, creationProperties.getForwardingPorts());
            share = new ShareSpec(region.getId(), "share-" + resourceId, "Managed user share",
                            creationProperties.getShareSize(), Share.Protocol.CEPHFS,
                            creationProperties.getShareType(), "user-" + resourceId);
            network = new NetworkRequirement(creationProperties.getSegmentationId(),
                    new NetworkSpec(region.getId(), "network-" + resourceId,
                            creationProperties.getNetworkCidr(), creationProperties.getNetworkDns(),
                            creationProperties.getNetworkGateway()),
                    creationProperties.getParentPortBandwidthLimit(), creationProperties.getSubPortBandwidthLimit());
            cloudInit = new CloudInitSpec(creationProperties.getSshUsername(), "123456", secret(32),
                    creationProperties.getHostRoutes());
        } catch (IllegalArgumentException | NullPointerException | CloudInitRenderer.RenderingException exception) {
            throw new BaseException(ErrorCode.INSTANCE_CREATION_CONFIG_INVALID, exception);
        }
        return new CreationDefaults(resourceId, share, network, cloudInit, forwarding);
    }

    private record CreationDefaults(String resourceId, ShareSpec share, NetworkRequirement network,
            CloudInitSpec cloudInit, ForwardingRequirement forwarding) {}

    private static String secret(int byteCount) {
        byte[] value = new byte[byteCount];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
