package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.exceptions.BaseException;
import cn.veryai.arcreactor.i18n.ErrorCode;
import cn.veryai.arcreactor.actor.PekkoRuntime;
import cn.veryai.arcreactor.actor.hypervisor.command.DrainHypervisor;
import cn.veryai.arcreactor.actor.hypervisor.command.HypervisorCommand;
import cn.veryai.arcreactor.actor.hypervisor.reply.ActionReply;
import cn.veryai.arcreactor.entity.HypervisorHostEntity;
import cn.veryai.arcreactor.repo.HypervisorHostRepo;
import cn.veryai.arcreactor.repo.CpuTypeRepo;
import cn.veryai.arcreactor.repo.RamTypeRepo;
import cn.veryai.arcreactor.repo.DiskTypeRepo;
import cn.veryai.arcreactor.repo.GpuTypeRepo;
import cn.veryai.arcreactor.repo.HypervisorTypeRepo;
import cn.veryai.arcreactor.repo.RegionRepo;
import cn.veryai.arcreactor.web.params.UpdateHypervisorHostParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HypervisorHostService {
  private final HypervisorHostRepo hypervisorHostRepo;
  private final CpuTypeRepo cpuTypeRepo;
  private final RamTypeRepo ramTypeRepo;
  private final DiskTypeRepo diskTypeRepo;
  private final GpuTypeRepo gpuTypeRepo;
  private final HypervisorTypeRepo hypervisorTypeRepo;
  private final RegionRepo regionIdRepo;
  private final PekkoRuntime pekkoRuntime;

  public List<HypervisorHostEntity> list() {
    return hypervisorHostRepo.findAll();
  }

  public HypervisorHostEntity get(String id) {
    HypervisorHostEntity entity = hypervisorHostRepo.findById(id);
    if (entity == null) {
      throw new BaseException(ErrorCode.HYPERVISOR_HOST_NOT_FOUND);
    }
    return entity;
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void setMaintenancePlan(String id, LocalDateTime expectedMaintenanceTime) {
    get(id);
    var system = pekkoRuntime.getActorSystem();
    ActionReply reply;
    try {
      reply =
          AskPattern.<HypervisorCommand, ActionReply>ask(
                  pekkoRuntime.hypervisorActor(id),
                  replyTo -> new DrainHypervisor(expectedMaintenanceTime, replyTo),
                  pekkoRuntime.getAskTimeout(),
                  system.scheduler())
              .toCompletableFuture()
              .get();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new BaseException(ErrorCode.HYPERVISOR_DRAIN_INTERRUPTED, exception);
    } catch (ExecutionException exception) {
      Throwable cause = exception.getCause();
      ErrorCode errorCode =
          cause instanceof TimeoutException
              ? ErrorCode.HYPERVISOR_DRAIN_TIMEOUT
              : ErrorCode.HYPERVISOR_DRAIN_UNCONFIRMED;
      throw new BaseException(errorCode, cause);
    }
    if (!reply.success()) {
      log.warn("Hypervisor drain rejected: hypervisorId={}, detail={}", id, reply.detail());
      throw new BaseException(ErrorCode.HYPERVISOR_DRAIN_REJECTED);
    }

    // The durable drain event carries the plan; the projection updates the host table.
  }

  @Transactional
  public HypervisorHostEntity update(String id, UpdateHypervisorHostParam param) {
    HypervisorHostEntity entity = get(id);
    BeanUtils.copyProperties(param, entity);
    validate(entity);
    try {
      int updated = hypervisorHostRepo.update(entity);
      // MySQL may report zero for an unchanged row; distinguish it from a missing row.
      if (updated == 0) get(id);
    } catch (DuplicateKeyException exception) {
      throw new BaseException(ErrorCode.HYPERVISOR_HOST_ALREADY_EXISTS);
    } catch (DataIntegrityViolationException exception) {
      throw new BaseException(ErrorCode.RESOURCE_CONSTRAINT_VIOLATION);
    }
    return entity;
  }

  @Transactional
  public void delete(String id) {
    try {
      if (hypervisorHostRepo.deleteById(id) == 0) {
        throw new BaseException(ErrorCode.HYPERVISOR_HOST_NOT_FOUND);
      }
    } catch (DataIntegrityViolationException exception) {
      throw new BaseException(ErrorCode.HYPERVISOR_HOST_STILL_REFERENCED);
    }
  }

  private void validate(HypervisorHostEntity entity) {
    if (entity.getCpuType() != null && cpuTypeRepo.findById(entity.getCpuType()) == null) {
      throw new BaseException(ErrorCode.CPU_TYPE_NOT_FOUND);
    }
    if (entity.getRamType() != null && ramTypeRepo.findById(entity.getRamType()) == null) {
      throw new BaseException(ErrorCode.RAM_TYPE_NOT_FOUND);
    }
    if (entity.getDiskType() != null && diskTypeRepo.findById(entity.getDiskType()) == null) {
      throw new BaseException(ErrorCode.DISK_TYPE_NOT_FOUND);
    }
    if (entity.getGpuType() != null && gpuTypeRepo.findById(entity.getGpuType()) == null) {
      throw new BaseException(ErrorCode.GPU_TYPE_NOT_FOUND);
    }
    if (entity.getHypervisorType() != null
        && hypervisorTypeRepo.findById(entity.getHypervisorType()) == null) {
      throw new BaseException(ErrorCode.HYPERVISOR_TYPE_NOT_FOUND);
    }
    if (entity.getRegionId() != null && regionIdRepo.findById(entity.getRegionId()) == null) {
      throw new BaseException(ErrorCode.REGION_NOT_FOUND);
    }
    if (entity.getUsedVcpus() > entity.getVcpus()
        || entity.getUsedRam() > entity.getRam()
        || entity.getUsedDisk() > entity.getDisk()
        || entity.getUsedGpus() > entity.getGpus()) {
      throw new BaseException(ErrorCode.HOST_RESOURCE_USAGE_EXCEEDED);
    }
  }
}
