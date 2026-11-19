package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.actor.fip.FloatingIpForwardings;
import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import cn.veryai.arcreactor.actor.fip.model.FloatingIpState;
import cn.veryai.arcreactor.actor.fip.model.FloatingIpState.Status;
import cn.veryai.arcreactor.entity.FloatingIpEntity;
import cn.veryai.arcreactor.entity.OpenstackClusterEntity;
import cn.veryai.arcreactor.entity.RegionEntity;
import cn.veryai.arcreactor.entity.SysNetworkEntity;
import cn.veryai.arcreactor.exceptions.BaseException;
import cn.veryai.arcreactor.i18n.ErrorCode;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import cn.veryai.arcreactor.repo.FloatingIpRepo;
import cn.veryai.arcreactor.repo.OpenstackClusterRepo;
import cn.veryai.arcreactor.repo.RegionRepo;
import cn.veryai.arcreactor.repo.SysNetworkRepo;
import cn.veryai.arcreactor.web.params.SaveFloatingIpParam;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.openstack4j.model.network.NetFloatingIP;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class FloatingIpService {
    private final FloatingIpRepo floatingIpRepo;
    private final RegionRepo regionRepo;
    private final OpenstackClusterRepo openstackClusterRepo;
    private final SysNetworkRepo sysNetworkRepo;
    private final OpenStackClient openStackClient;
    private final FloatingIpForwardings floatingIpForwardings;
    private final PlatformTransactionManager transactionManager;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public FloatingIpEntity create(SaveFloatingIpParam param) {
        RegionEntity region = regionRepo.findById(param.getRegionId());
        if (region == null) {
            throw new BaseException(ErrorCode.REGION_NOT_FOUND);
        }
        OpenstackClusterEntity cluster = openstackClusterRepo.findById(region.getClusterId());
        if (cluster == null) {
            throw new BaseException(ErrorCode.OPENSTACK_CLUSTER_NOT_FOUND);
        }
        String osProjectId = cluster.getOsAdminDefaultProjectId();
        if (StringUtils.isBlank(osProjectId)) {
            throw new BaseException(ErrorCode.OPENSTACK_ADMIN_PROJECT_REQUIRED);
        }
        SysNetworkEntity externalNetwork = sysNetworkRepo.findById(param.getExtNetId());
        if (externalNetwork == null
                || !Objects.equals(externalNetwork.getRegionId(), param.getRegionId())
                || !externalNetwork.isRouterExternally()
                || StringUtils.isBlank(externalNetwork.getOsNetId())) {
            throw new BaseException(ErrorCode.EXTERNAL_NETWORK_NOT_FOUND);
        }
        if (!param.isInitOpenstack()
                && StringUtils.isAnyBlank(param.getOsFloatingIpId(), param.getFloatingIp())) {
            throw new BaseException(ErrorCode.FLOATING_IP_IDENTITY_REQUIRED);
        }

        FloatingIpEntity entity = new FloatingIpEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setName(param.getName());
        entity.setDescription(param.getDescription());
        entity.setRegionId(param.getRegionId());
        entity.setExtNetId(externalNetwork.getId());
        entity.setOsExtNetId(externalNetwork.getOsNetId());
        entity.setOsProjectId(osProjectId);
        entity.setOsFloatingIpId(param.getOsFloatingIpId());
        entity.setFloatingIp(param.getFloatingIp());
        entity.setDomain(StringUtils.trimToNull(param.getDomain()));

        try {
            List<Integer> externalPorts = List.of();
            if (param.isInitOpenstack()) {
                NetFloatingIP floatingIp = openStackClient.createPortForwardingFloatingIP(
                        entity.getRegionId(), osProjectId, entity.getOsExtNetId(), "veryai:" + entity.getId());
                if (floatingIp == null
                        || StringUtils.isAnyBlank(floatingIp.getId(), floatingIp.getFloatingIpAddress())) {
                    throw new IllegalStateException("OpenStack returned an incomplete floating IP");
                }
                entity.setOsFloatingIpId(floatingIp.getId());
                entity.setFloatingIp(floatingIp.getFloatingIpAddress());
            } else {
                externalPorts = openStackClient.listPortForwardingExternalPorts(
                                entity.getRegionId(), entity.getOsProjectId(), entity.getOsFloatingIpId())
                        .toCompletableFuture().get();
            }
            entity.setPortMask(Base64.getEncoder().encodeToString(FloatingIpState.portMask(externalPorts).toByteArray()));
            // Commit registration before calling the actor; an enablement failure must not roll it back.
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> floatingIpRepo.insert(entity));
            Boolean enabled = floatingIpForwardings.enable(
                    new FloatingIpIdentity(entity.getId(), entity.getRegionId(), entity.getOsFloatingIpId(),
                            entity.getFloatingIp(), entity.getDomain(), entity.getOsProjectId()), externalPorts)
                    .toCompletableFuture().get();
            if (!Boolean.TRUE.equals(enabled)) {
                throw new IllegalStateException("Floating IP actor identity mismatch");
            }
            // The reply reflects the persisted actor state; the database status is updated by projection.
            entity.setStatus(Status.ENABLED);
            return entity;
        } catch (DuplicateKeyException exception) {
            throw new BaseException(ErrorCode.FLOATING_IP_ALREADY_EXISTS, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.error("floating IP actor enablement interrupted", exception);
            throw new BaseException(ErrorCode.FLOATING_IP_CREATE_FAILED, exception);
        } catch (Exception exception) {
            log.error("create floating IP error", exception);
            throw new BaseException(ErrorCode.FLOATING_IP_CREATE_FAILED, exception);
        }
    }

    public void enable(String id) {
        FloatingIpEntity entity = requireFloatingIp(id);
        boolean accepted = Boolean.TRUE.equals(awaitResult(openStackClient.listPortForwardingExternalPorts(
                        entity.getRegionId(), entity.getOsProjectId(), entity.getOsFloatingIpId())
                .thenCompose(externalPorts -> floatingIpForwardings.enable(
                        new FloatingIpIdentity(entity.getId(), entity.getRegionId(), entity.getOsFloatingIpId(),
                                entity.getFloatingIp(), entity.getDomain(), entity.getOsProjectId()),
                        externalPorts)), ErrorCode.FLOATING_IP_ENABLE_FAILED));
        if (!accepted) throw new BaseException(ErrorCode.FLOATING_IP_ENABLE_REJECTED);
    }

    public void disable(String id) {
        FloatingIpEntity entity = requireFloatingIp(id);
        if (!Boolean.TRUE.equals(awaitResult(floatingIpForwardings.disable(entity.getId()),
                ErrorCode.FLOATING_IP_DISABLE_FAILED))) {
            throw new BaseException(ErrorCode.FLOATING_IP_DISABLE_FAILED);
        }
    }

    public void destroy(String id) {
        if (!Boolean.TRUE.equals(awaitResult(floatingIpForwardings.destroy(id), ErrorCode.FLOATING_IP_DESTROY_FAILED))) {
            throw new BaseException(ErrorCode.FLOATING_IP_DESTROY_REJECTED);
        }
    }

    public Status getStatus(String id) {
        FloatingIpState state = awaitResult(floatingIpForwardings.getState(id), ErrorCode.FLOATING_IP_STATUS_QUERY_FAILED);
        if (state.identity() == null) requireFloatingIp(id);
        return state.status();
    }

    private FloatingIpEntity requireFloatingIp(String id) {
        FloatingIpEntity entity = floatingIpRepo.findById(id);
        if (entity == null) throw new BaseException(ErrorCode.FLOATING_IP_NOT_FOUND);
        return entity;
    }

    private static <T> T awaitResult(CompletionStage<T> operation, ErrorCode failure) {
        try {
            return operation.toCompletableFuture().get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BaseException(failure, exception);
        } catch (ExecutionException exception) {
            throw new BaseException(failure, exception.getCause());
        }
    }
}
