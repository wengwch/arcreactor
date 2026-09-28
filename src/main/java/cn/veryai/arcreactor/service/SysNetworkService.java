package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.entity.OpenstackClusterEntity;
import cn.veryai.arcreactor.entity.RegionEntity;
import cn.veryai.arcreactor.entity.SysNetworkEntity;
import cn.veryai.arcreactor.enums.SysNetworkType;
import cn.veryai.arcreactor.exceptions.BaseException;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import cn.veryai.arcreactor.repo.OpenstackClusterRepo;
import cn.veryai.arcreactor.repo.RegionRepo;
import cn.veryai.arcreactor.repo.SysNetworkRepo;
import cn.veryai.arcreactor.web.params.SaveSysNetworkParam;
import lombok.extern.slf4j.Slf4j;
import org.openstack4j.model.network.Network;
import org.openstack4j.model.network.Subnet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class SysNetworkService {
    @Autowired
    private SysNetworkRepo sysNetworkRepo;
    @Autowired
    private RegionRepo regionRepo;

    @Autowired
    private OpenstackClusterRepo openstackClusterRepo;

    @Autowired
    private OpenStackClient openStackClient;

    public List<SysNetworkEntity> list(String regionId) {
        if (regionId == null) return sysNetworkRepo.findAll();
        requireRegion(regionId);
        return sysNetworkRepo.findByRegionId(regionId);
    }

    public SysNetworkEntity get(String id) {
        SysNetworkEntity entity = sysNetworkRepo.findById(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "System network not found");
        }
        return entity;
    }

    @Transactional
    public SysNetworkEntity create(SaveSysNetworkParam param) {
        RegionEntity regionEntity = regionRepo.findById(param.getRegionId());
        if (regionEntity == null) {
            throw new BaseException(BaseException.ERROR_CODE, "Region not found");
        }
        OpenstackClusterEntity openstackClusterEntity = openstackClusterRepo.findById(regionEntity.getClusterId());
        if (openstackClusterEntity == null) {
            throw new BaseException(BaseException.ERROR_CODE, "Openstack cluster not found");
        }
        String adminDefaultProjectId = openstackClusterEntity.getAdminDefaultProjectId();

        try {
            SysNetworkEntity entity = new SysNetworkEntity();
            entity.setId(UUID.randomUUID().toString());
            entity.setName(param.getName());
            entity.setDescription(param.getDescription());
            entity.setSysNetworkType(param.getSysNetworkType());
            entity.setPhysicalNetwork(param.getPhysicalNetwork());
            entity.setRegionId(param.getRegionId());
            entity.setDns(param.getDns());
            entity.setShared(true);
            if (param.getSysNetworkType() == SysNetworkType.PUBLIC || param.getSysNetworkType() == SysNetworkType.LOCAL) {
                entity.setRouterExternally(true);
            }
            entity.setIpv4CIDR(param.getIpv4CIDR());
            entity.setGateway(param.getGateway());
            entity.setSegmentId(param.getSegmentId());
            entity.setOsProjectId(adminDefaultProjectId);
            entity.setOsNetworkType(param.getSysNetworkType().getNetworkType());
            entity.setOsNetId(param.getOsNetId());
            entity.setOsSubNetId(param.getOsSubNetId());
            if (param.isInitOpenstack()) {
                Network network =
                        openStackClient.createNetwork(
                                entity.getRegionId(),
                                adminDefaultProjectId,
                                entity.getName(),
                                entity.getOsNetworkType(),
                                entity.isRouterExternally(),
                                entity.isShared(),
                                entity.getPhysicalNetwork(),
                                entity.getSegmentId());
                Subnet subnet =
                        openStackClient.createSubnet(
                                entity.getRegionId(),
                                adminDefaultProjectId,
                                network.getId(),
                                entity.getName() + "_subnet",
                                entity.getIpv4CIDR(),
                                entity.getDns(),
                                entity.getGateway(),
                                entity.getHostRoute());
                entity.setOsNetId(network.getId());
                entity.setOsSubNetId(subnet.getId());
            }
            sysNetworkRepo.insert(entity);
            return entity;
        } catch (Exception exception) {
            log.error("create sys network error", exception);
            throw new BaseException(BaseException.ERROR_CODE, "系统异常");
        }
    }

    @Transactional
    public void delete(String id) {
        try {
            if (sysNetworkRepo.deleteById(id) == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "System network not found");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "System network is still referenced");
        }
    }

    private void requireRegion(String regionId) {
        if (regionRepo.findById(regionId) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "regionId does not exist");
        }
    }
}
