package cn.veryai.arcreactor.actor.fip.projection.mybatis;

import cn.veryai.arcreactor.actor.fip.event.ForwardingBecameReady;
import cn.veryai.arcreactor.actor.fip.event.ForwardingCreationFailed;
import cn.veryai.arcreactor.actor.fip.event.ForwardingCreationRequested;
import cn.veryai.arcreactor.actor.fip.event.ForwardingDeleted;
import cn.veryai.arcreactor.actor.fip.event.ForwardingDeletionFailed;
import cn.veryai.arcreactor.actor.fip.event.ForwardingDeletionStarted;
import cn.veryai.arcreactor.actor.fip.event.ForwardingPortAllocated;
import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import java.sql.Timestamp;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FloatingIpProjectionMapper {
  int updateState(
      @Param("identity") FloatingIpIdentity identity,
      @Param("enabled") boolean enabled,
      @Param("portMask") byte[] portMask,
      @Param("version") long version);

  Long findResourceVersion(@Param("identity") FloatingIpIdentity identity);

  int upsertForwardingCreated(
      @Param("event") ForwardingCreationRequested event,
      @Param("version") long version,
      @Param("updatedAt") Timestamp updatedAt);

  int updateForwardingAllocated(
      @Param("event") ForwardingPortAllocated event,
      @Param("version") long version,
      @Param("updatedAt") Timestamp updatedAt);

  int updateForwardingReady(
      @Param("event") ForwardingBecameReady event,
      @Param("version") long version,
      @Param("updatedAt") Timestamp updatedAt);

  int updateForwardingCreationFailed(
      @Param("event") ForwardingCreationFailed event,
      @Param("version") long version,
      @Param("updatedAt") Timestamp updatedAt);

  int updateForwardingDeletionStarted(
      @Param("event") ForwardingDeletionStarted event,
      @Param("version") long version,
      @Param("updatedAt") Timestamp updatedAt);

  int updateForwardingDeletionFailed(
      @Param("event") ForwardingDeletionFailed event,
      @Param("version") long version,
      @Param("updatedAt") Timestamp updatedAt);

  int upsertForwardingDeleted(
      @Param("event") ForwardingDeleted event,
      @Param("version") long version,
      @Param("updatedAt") Timestamp updatedAt,
      @Param("deletedAt") Timestamp deletedAt);

  Long findForwardingVersion(
      @Param("identity") FloatingIpIdentity identity,
      @Param("claimId") String claimId);
}
