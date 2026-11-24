package cn.veryai.arcreactor.actor;

import cn.veryai.arcreactor.actor.fip.FloatingIpForwardings;
import cn.veryai.arcreactor.actor.hypervisor.HypervisorPlacement;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.SchedulerService;
import cn.veryai.arcreactor.actor.instance.CloudInitRenderer;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import cn.veryai.arcreactor.repo.HypervisorHostRepo;
import cn.veryai.arcreactor.actor.instance.InstanceDependencyPreparer;
import cn.veryai.arcreactor.service.FundWalletService;
import cn.veryai.arcreactor.service.RentalPriceService;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class PekkoConfig {
  @Bean
  ActorSystem<Void> actorSystem() {
    return ActorSystem.create(Behaviors.empty(), "cloud-resource-manager");
  }

  @Bean
  HypervisorPlacement hypervisorPlacement(PekkoRuntime pekkoRuntime, HypervisorHostRepo hostRepo) {
    return new SchedulerService(pekkoRuntime, hostRepo, pekkoRuntime.getBlockingExecutor(),
            pekkoRuntime.getConfig().getSchedulerCandidateQueryLimit(),
            pekkoRuntime.getConfig().getSchedulerMaxClaimAttempts());
  }

  @Bean
  SmartInitializingSingleton pekkoInitializer(
      PekkoRuntime pekkoRuntime,
      OpenStackClient openStackClient,
      CloudInitRenderer cloudInitRenderer,
      FloatingIpForwardings floatingIpForwardings,
      InstanceDependencyPreparer dependencyPreparer,
      HypervisorPlacement hypervisorPlacement,
      FundWalletService fundWalletService,
      RentalPriceService rentalPriceService) {
    // Clients depend on the runtime, so register actors only after their beans have been constructed.
    return () -> pekkoRuntime.initialize(openStackClient, cloudInitRenderer,
        floatingIpForwardings, dependencyPreparer, hypervisorPlacement, fundWalletService, rentalPriceService);
  }
}
