package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.actor.fip.FloatingIpForwardings;
import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
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
import cn.veryai.arcreactor.web.params.EnableFloatingIpParam;
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
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public FloatingIpEntity create(SaveFloatingIpParam param) {
        RegionEntity region = regionRepo.findById(param.getRegionId());
        if (region == null) {
            throw new BaseException(ErrorCode.REGION_NOT_FOUND);
        }
        OpenstackClusterEntity cluster = openstackClusterRepo.findById(region.getClusterId());
        if (cluster == null) {
            throw new BaseException(ErrorCode.OPENSTACK_CLUSTER_NOT_FOUND);
        }
        String projectId = cluster.getAdminDefaultProjectId();
        if (StringUtils.isBlank(projectId)) {
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
        entity.setOsProjectId(projectId);
        entity.setOsFloatingIpId(param.getOsFloatingIpId());
        entity.setFloatingIp(param.getFloatingIp());
        entity.setPortMask(param.getPortMask());

        try {
            if (param.isInitOpenstack()) {
                NetFloatingIP floatingIp = openStackClient.createPortForwardingFloatingIP(
                        entity.getRegionId(), projectId, entity.getOsExtNetId());
                if (floatingIp == null
                        || StringUtils.isAnyBlank(floatingIp.getId(), floatingIp.getFloatingIpAddress())) {
                    throw new IllegalStateException("OpenStack returned an incomplete floating IP");
                }
                entity.setOsFloatingIpId(floatingIp.getId());
                entity.setFloatingIp(floatingIp.getFloatingIpAddress());
            }
            floatingIpRepo.insert(entity);
            Boolean enabled = floatingIpForwardings.enable(
                    new FloatingIpIdentity(entity.getRegionId(), entity.getOsFloatingIpId(),
                            entity.getFloatingIp(), entity.getOsProjectId()), entity.getPortMask(), null)
                    .toCompletableFuture().get();
            if (!Boolean.TRUE.equals(enabled)) {
                throw new IllegalStateException("Floating IP actor identity conflicts or it is already enabled");
            }
            // The reply reflects the persisted actor state; the database flag is updated by projection.
            entity.setEnabled(true);
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

    public void enable(String id, EnableFloatingIpParam param) {
        FloatingIpEntity entity = requireFloatingIp(id);
        boolean accepted = awaitControl(floatingIpForwardings.enable(
                new FloatingIpIdentity(entity.getRegionId(), entity.getOsFloatingIpId(),
                        entity.getFloatingIp(), entity.getOsProjectId()), null,
                param == null ? null : param.getForwardings()), ErrorCode.FLOATING_IP_ENABLE_FAILED);
        if (!accepted) throw new BaseException(ErrorCode.FLOATING_IP_ENABLE_REJECTED);
    }

    public void disable(String id) {
        FloatingIpEntity entity = requireFloatingIp(id);
        if (!awaitControl(floatingIpForwardings.disable(entity.getRegionId(), entity.getOsFloatingIpId()),
                ErrorCode.FLOATING_IP_DISABLE_FAILED)) {
            throw new BaseException(ErrorCode.FLOATING_IP_DISABLE_FAILED);
        }
    }

    private FloatingIpEntity requireFloatingIp(String id) {
        FloatingIpEntity entity = floatingIpRepo.findById(id);
        if (entity == null) throw new BaseException(ErrorCode.FLOATING_IP_NOT_FOUND);
        return entity;
    }

    private static boolean awaitControl(CompletionStage<Boolean> operation, ErrorCode failure) {
        try {
            return Boolean.TRUE.equals(operation.toCompletableFuture().get());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BaseException(failure, exception);
        } catch (ExecutionException exception) {
            throw new BaseException(failure, exception.getCause());
        }
    }
}
