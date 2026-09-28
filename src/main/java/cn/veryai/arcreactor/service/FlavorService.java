package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.entity.FlavorEntity;
import cn.veryai.arcreactor.entity.GpuTypeEntity;
import cn.veryai.arcreactor.entity.HypervisorTypeEntity;
import cn.veryai.arcreactor.exceptions.BaseException;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import cn.veryai.arcreactor.repo.FlavorRepo;
import cn.veryai.arcreactor.repo.HypervisorTypeRepo;
import cn.veryai.arcreactor.repo.CpuTypeRepo;
import cn.veryai.arcreactor.repo.RamTypeRepo;
import cn.veryai.arcreactor.repo.DiskTypeRepo;
import cn.veryai.arcreactor.repo.GpuTypeRepo;
import cn.veryai.arcreactor.repo.RegionRepo;
import cn.veryai.arcreactor.web.params.SaveFlavorParam;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.openstack4j.model.compute.Flavor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlavorService {
  private final FlavorRepo flavorRepo;
  private final HypervisorTypeRepo hypervisorTypeRepo;
  private final CpuTypeRepo cpuTypeRepo;
  private final RamTypeRepo ramTypeRepo;
  private final DiskTypeRepo diskTypeRepo;
  private final GpuTypeRepo gpuTypeRepo;
  private final RegionRepo regionRepo;
  private final OpenStackClient openStackClient;

  public List<FlavorEntity> list(String regionId) {
    if (regionId == null) {
      return flavorRepo.findAll();
    }
    return flavorRepo.findByRegionId(regionId);
  }

  public FlavorEntity get(String id) {
    FlavorEntity entity = flavorRepo.findById(id);
    if (entity == null) {
      throw new BaseException(BaseException.ERROR_CODE, "Flavor not found");
    }
    return entity;
  }

  @Transactional
  public FlavorEntity create(SaveFlavorParam param) {
    if (regionRepo.findById(param.getRegionId()) == null) {
      throw new BaseException(BaseException.ERROR_CODE, "regionId does not exist");
    }
    HypervisorTypeEntity hypervisorTypeEntity =
        hypervisorTypeRepo.findById(param.getHypervisorType());
    if (hypervisorTypeEntity == null) {
      throw new BaseException(BaseException.ERROR_CODE, "hypervisorType does not exist");
    }
    if (cpuTypeRepo.findById(param.getCpuType()) == null) {
      throw new BaseException(BaseException.ERROR_CODE, "cpuType does not exist");
    }
    if (ramTypeRepo.findById(param.getRamType()) == null) {
      throw new BaseException(BaseException.ERROR_CODE, "ramType does not exist");
    }
    if (diskTypeRepo.findById(param.getDiskType()) == null) {
      throw new BaseException(BaseException.ERROR_CODE, "diskType does not exist");
    }
    if (StringUtils.isNotBlank(param.getGpuType())
        && gpuTypeRepo.findById(param.getGpuType()) == null) {
      throw new BaseException(BaseException.ERROR_CODE, "gpuType does not exist");
    }

    Flavor flavor;
    try {
      flavor =
          openStackClient.createFlavor(
              param.getRegionId(),
              param.getName(),
              param.getVcpus(),
              param.getRam(),
              param.getDisk());
    } catch (Exception exception) {
      throw new BaseException(
          BaseException.ERROR_CODE, "规格名称" + param.getName() + " 创建失败！" + exception.getMessage());
    }
    FlavorEntity flavorEntity = new FlavorEntity();
    flavorEntity.setId(flavor.getId());
    flavorEntity.setOsFlavorId(flavor.getId());
    flavorEntity.setName(flavor.getName());
    flavorEntity.setDescription(param.getDescription());
    flavorEntity.setVcpus(flavor.getVcpus());
    flavorEntity.setRam(flavor.getRam() / 1024);
    flavorEntity.setDisk(flavor.getDisk());

    flavorEntity.setHypervisorType(param.getHypervisorType());
    flavorEntity.setCpuType(param.getCpuType());
    flavorEntity.setRamType(param.getRamType());
    flavorEntity.setDiskType(param.getDiskType());
    flavorEntity.setRegionId(param.getRegionId());
    flavorEntity.setEnabled(param.isEnabled());

    if (StringUtils.isNotBlank(param.getGpuType())) {
      flavorEntity.setGpuType(param.getGpuType());
      flavorEntity.setGpus(param.getGpus());
      GpuTypeEntity gpuType = gpuTypeRepo.findById(param.getGpuType());
      flavorEntity.setVram(gpuType.getVram() * flavorEntity.getGpus());
      Map<String, String> metadata = new HashMap<>();
      metadata.put(
          "pci_passthrough:alias", gpuType.getPciPassthroughAlias() + ":" + flavorEntity.getGpus());
      metadata.put("hw:cpu_mode", "host-passthrough");
      metadata.put("hw:cpu_policy", "dedicated");
      metadata.put("hw:mem_page_size", "1GB");
      metadata.put("hw:cpu_thread_policy", "prefer");
      metadata.put("hw:numa_nodes", String.valueOf(hypervisorTypeEntity.getNumaNodeCnt()));
      metadata.put("hw:cpu_sockets", String.valueOf(hypervisorTypeEntity.getSocketCnt()));
      openStackClient.updateFlavorMetaData(
          flavorEntity.getRegionId(), flavorEntity.getOsFlavorId(), metadata);
    }
    flavorRepo.insert(flavorEntity);
    return flavorEntity;
  }

  @Transactional
  public void delete(String id) {
    FlavorEntity flavorEntity = get(id);
    if (flavorEntity == null) {
      throw new BaseException(BaseException.ERROR_CODE, "规格不存在");
    }
    flavorRepo.deleteById(id);
    openStackClient.deleteFlavor(
        flavorEntity.getRegionId(), flavorEntity.getOsFlavorId(), flavorEntity.getOsFlavorId());
  }
}
