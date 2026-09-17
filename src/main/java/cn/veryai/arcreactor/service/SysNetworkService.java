package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.entity.OpenstackClusterEntity;
import cn.veryai.arcreactor.entity.RegionEntity;
import cn.veryai.arcreactor.entity.SysNetworkEntity;
import cn.veryai.arcreactor.enums.SysNetworkType;
import cn.veryai.arcreactor.repo.OpenstackClusterRepo;
import cn.veryai.arcreactor.repo.SysNetworkRepo;
import cn.veryai.arcreactor.repo.RegionRepo;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import cn.veryai.arcreactor.web.params.SaveSysNetworkParam;
import lombok.RequiredArgsConstructor;
import org.openstack4j.model.network.Network;
import org.openstack4j.model.network.Subnet;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class SysNetworkService {
    @Autowired
    private SysNetworkRepo repo;
    @Autowired
    private RegionRepo regionRepo;

    @Autowired
    private OpenstackClusterRepo openstackClusterRepo;

    @Autowired
    private OpenStackClient openStackClient;

    public List<SysNetworkEntity> list(String regionId) {
        if (regionId == null) return repo.findAll();
        requireRegion(regionId);
        return repo.findByRegionId(regionId);
    }

    public SysNetworkEntity get(String id) {
        SysNetworkEntity entity = repo.findById(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "System network not found");
        }
        return entity;
    }

    @Transactional
    public SysNetworkEntity create(SaveSysNetworkParam param) {
        RegionEntity regionEntity = regionRepo.findById(param.getRegionId());
        if (regionEntity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Region not found");
        }
        OpenstackClusterEntity openstackClusterEntity = openstackClusterRepo.findById(regionEntity.getClusterId());
        if (openstackClusterEntity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Openstack cluster not found");
        }
        String adminDefaultProjectId = openstackClusterEntity.getAdminDefaultProjectId();

        SysNetworkEntity entity = new SysNetworkEntity();
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
        try {
            repo.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "System network ID already exists");
        }
        return entity;
    }

    @Transactional
    public SysNetworkEntity update(String id, SaveSysNetworkParam param) {
        get(id);
        SysNetworkEntity entity = toEntity(id, param);
        requireRegion(entity.getRegionId());
        try {
            int updated = repo.update(entity);
            // MySQL may report zero for an unchanged row; distinguish it from a missing row.
            if (updated == 0) get(id);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "System network ID already exists");
        }
        return entity;
    }

    @Transactional
    public void delete(String id) {
        try {
            if (repo.deleteById(id) == 0) {
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

    private SysNetworkEntity toEntity(String id, SaveSysNetworkParam param) {
        SysNetworkEntity entity = new SysNetworkEntity();
        BeanUtils.copyProperties(param, entity);
        entity.setId(id);
        return entity;
    }
}
