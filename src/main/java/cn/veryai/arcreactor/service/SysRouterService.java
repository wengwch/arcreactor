package cn.veryai.arcreactor.service;

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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SysRouterService {
  private final SysRouterRepo repo;
  private final RegionRepo regionRepo;
  private final OpenstackClusterRepo openstackClusterRepo;
  private final SysNetworkRepo sysNetworkRepo;
  private final OpenStackClient openStackClient;

  public List<SysRouterEntity> list(String regionId) {
    if (regionId == null) return repo.findAll();
    requireRegion(regionId);
    return repo.findByRegionId(regionId);
  }

  public SysRouterEntity get(String id) {
    SysRouterEntity entity = repo.findById(id);
    if (entity == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "System router not found");
    }
    return entity;
  }

  @Transactional
  public SysRouterEntity create(SaveSysRouterParam param) {
    RegionEntity regionEntity = regionRepo.findById(param.getRegionId());
    if (regionEntity == null) {
      throw new BaseException(BaseException.ERROR_CODE, "Region not found");
    }
    OpenstackClusterEntity openstackClusterEntity =
        openstackClusterRepo.findById(regionEntity.getClusterId());
    if (openstackClusterEntity == null) {
      throw new BaseException(BaseException.ERROR_CODE, "Openstack cluster not found");
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
      throw new BaseException(BaseException.ERROR_CODE, "External network not found");
    }
    entity.setOsExtNetId(externalNetwork.getOsNetId());
    SysNetworkEntity sharedNetwork = sysNetworkRepo.findById(entity.getSharedNetId());
    if (sharedNetwork == null
        || !Objects.equals(sharedNetwork.getRegionId(), entity.getRegionId())
        || !sharedNetwork.isRouterExternally()
        || sharedNetwork.getOsNetId() == null
        || sharedNetwork.getOsNetId().isBlank()) {
      throw new BaseException(BaseException.ERROR_CODE, "Shared network not found");
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
      repo.insert(entity);
      return entity;
    } catch (Exception exception) {
      log.error("create sys router error", exception);
      throw new BaseException(BaseException.ERROR_CODE, "系统异常");
    }
  }

  @Transactional
  public void delete(String id) {
    try {
      if (repo.deleteById(id) == 0) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "System router not found");
      }
    } catch (DataIntegrityViolationException exception) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "System router is still referenced");
    }
  }

  private void requireRegion(String regionId) {
    if (regionRepo.findById(regionId) == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "regionId does not exist");
    }
  }

  private SysRouterEntity toEntity(String id, SaveSysRouterParam param) {
    SysRouterEntity entity = new SysRouterEntity();
    BeanUtils.copyProperties(param, entity);
    entity.setId(id);
    return entity;
  }
}
