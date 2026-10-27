package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.i18n.ErrorCode;
import cn.veryai.arcreactor.entity.OpenstackClusterEntity;
import cn.veryai.arcreactor.entity.RegionEntity;
import cn.veryai.arcreactor.entity.SysNetworkEntity;
import cn.veryai.arcreactor.entity.SysRouterEntity;
import cn.veryai.arcreactor.exceptions.BaseException;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import cn.veryai.arcreactor.repo.OpenstackClusterRepo;
import cn.veryai.arcreactor.repo.SysNetworkRepo;
import cn.veryai.arcreactor.repo.SysRouterRepo;
import cn.veryai.arcreactor.repo.RegionRepo;
import cn.veryai.arcreactor.web.params.SaveSysRouterParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openstack4j.model.network.Router;
import org.openstack4j.model.network.RouterInterface;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SysRouterService {
  private final SysRouterRepo sysRouterRepo;
  private final RegionRepo regionRepo;
  private final OpenstackClusterRepo openstackClusterRepo;
  private final SysNetworkRepo sysNetworkRepo;
  private final OpenStackClient openStackClient;

  public List<SysRouterEntity> list(String regionId) {
    if (regionId == null) return sysRouterRepo.findAll();
    requireRegion(regionId);
    return sysRouterRepo.findByRegionId(regionId);
  }

  public SysRouterEntity get(String id) {
    SysRouterEntity entity = sysRouterRepo.findById(id);
    if (entity == null) {
      throw new BaseException(ErrorCode.SYSTEM_ROUTER_NOT_FOUND);
    }
    return entity;
  }

  @Transactional
  public SysRouterEntity create(SaveSysRouterParam param) {
    RegionEntity regionEntity = regionRepo.findById(param.getRegionId());
    if (regionEntity == null) {
      throw new BaseException(ErrorCode.REGION_NOT_FOUND);
    }
    OpenstackClusterEntity openstackClusterEntity =
        openstackClusterRepo.findById(regionEntity.getClusterId());
    if (openstackClusterEntity == null) {
      throw new BaseException(ErrorCode.OPENSTACK_CLUSTER_NOT_FOUND);
    }
    String adminDefaultProjectId = openstackClusterEntity.getAdminDefaultProjectId();

    SysRouterEntity entity = toEntity(UUID.randomUUID().toString(), param);
    entity.setOsProjectId(adminDefaultProjectId);
    SysNetworkEntity externalNetwork = sysNetworkRepo.findById(entity.getExtNetId());
    if (externalNetwork == null
        || !Objects.equals(externalNetwork.getRegionId(), entity.getRegionId())
        || !externalNetwork.isRouterExternally()
        || externalNetwork.getOsNetId() == null
        || externalNetwork.getOsNetId().isBlank()) {
      throw new BaseException(ErrorCode.EXTERNAL_NETWORK_NOT_FOUND);
    }
    entity.setOsExtNetId(externalNetwork.getOsNetId());
    SysNetworkEntity sharedNetwork = sysNetworkRepo.findById(entity.getSharedNetId());
    if (sharedNetwork == null
        || !Objects.equals(sharedNetwork.getRegionId(), entity.getRegionId())
        || sharedNetwork.isRouterExternally()
        || sharedNetwork.getOsNetId() == null
        || sharedNetwork.getOsNetId().isBlank()) {
      throw new BaseException(ErrorCode.SHARED_NETWORK_NOT_FOUND);
    }
    entity.setOsSharedNetId(sharedNetwork.getOsNetId());
    try {
      if (param.isInitOpenstack()) {
        Router router =
            openStackClient.createRouter(
                entity.getRegionId(),
                adminDefaultProjectId,
                entity.getName(),
                entity.getOsExtNetId());
        entity.setOsRouterId(router.getId());
        RouterInterface routerInterface =
            openStackClient.attachRouterInterface(
                entity.getRegionId(),
                adminDefaultProjectId,
                entity.getOsRouterId(),
                sharedNetwork.getOsSubNetId());
      }
      sysRouterRepo.insert(entity);
      return entity;
    } catch (Exception exception) {
      log.error("create sys router error", exception);
      throw new BaseException(ErrorCode.SYSTEM_ROUTER_CREATE_FAILED, exception);
    }
  }

  @Transactional
  public void delete(String id) {
    try {
      if (sysRouterRepo.deleteById(id) == 0) {
        throw new BaseException(ErrorCode.SYSTEM_ROUTER_NOT_FOUND);
      }
    } catch (DataIntegrityViolationException exception) {
      throw new BaseException(ErrorCode.SYSTEM_ROUTER_STILL_REFERENCED);
    }
  }

  private void requireRegion(String regionId) {
    if (regionRepo.findById(regionId) == null) {
      throw new BaseException(ErrorCode.REGION_NOT_FOUND);
    }
  }

  private SysRouterEntity toEntity(String id, SaveSysRouterParam param) {
    SysRouterEntity entity = new SysRouterEntity();
    BeanUtils.copyProperties(param, entity);
    entity.setId(id);
    return entity;
  }
}
