-- MySQL dump 10.13  Distrib 26.10.0, for macos27.0 (arm64)
--
-- Host: 127.0.0.1    Database: arc_reactor
-- ------------------------------------------------------
-- Server version	26.7.0

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
SET @MYSQLDUMP_TEMP_LOG_BIN = @@SESSION.SQL_LOG_BIN;
SET @@SESSION.SQL_LOG_BIN= 0;

--
-- GTID state at the beginning of the backup 
--

SET @@GLOBAL.GTID_PURGED=/*!80000 '+'*/ 'c371de03-9d05-11f1-b450-66b6aab91af9:1-287';

--
-- Table structure for table `cpu_type`
--

DROP TABLE IF EXISTS `cpu_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cpu_type` (
  `id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_zh_0900_as_cs;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `cpu_type`
--

LOCK TABLES `cpu_type` WRITE;
/*!40000 ALTER TABLE `cpu_type` DISABLE KEYS */;
INSERT INTO `cpu_type` VALUES ('demo-cpu-32c','x86_64 32-core CPU (example)'),('demo-cpu-64c','x86_64 64-core CPU (example)');
/*!40000 ALTER TABLE `cpu_type` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `disk_type`
--

DROP TABLE IF EXISTS `disk_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `disk_type` (
  `id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_zh_0900_as_cs;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `disk_type`
--

LOCK TABLES `disk_type` WRITE;
/*!40000 ALTER TABLE `disk_type` DISABLE KEYS */;
INSERT INTO `disk_type` VALUES ('demo-disk-nvme','NVMe SSD'),('demo-disk-ssd','SATA SSD');
/*!40000 ALTER TABLE `disk_type` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `flavor`
--

DROP TABLE IF EXISTS `flavor`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flavor` (
  `id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `description` text COLLATE utf8mb4_zh_0900_as_cs,
  `vcpus` int NOT NULL DEFAULT '0',
  `ram` int NOT NULL DEFAULT '0',
  `disk` int NOT NULL DEFAULT '0',
  `gpus` int NOT NULL DEFAULT '0',
  `vram` int NOT NULL DEFAULT '0',
  `hypervisor_type` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `cpu_type` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `ram_type` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `disk_type` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `gpu_type` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `region_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `os_flavor_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_flavor_region` (`region_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_zh_0900_as_cs;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `flavor`
--

LOCK TABLES `flavor` WRITE;
/*!40000 ALTER TABLE `flavor` DISABLE KEYS */;
INSERT INTO `flavor` VALUES ('demo-flavor-medium','demo.c8.r32','Example larger general purpose flavor.',8,32,200,0,0,'demo-hv-general','demo-cpu-32c','demo-ram-ddr4-32g','demo-disk-ssd',NULL,'demo-region',0,'REPLACE_WITH_MEDIUM_NOVA_FLAVOR_ID'),('demo-flavor-small','demo.c4.r16','Example general purpose flavor.',4,16,100,0,0,'demo-hv-general','demo-cpu-32c','demo-ram-ddr4-32g','demo-disk-ssd',NULL,'demo-region',0,'REPLACE_WITH_SMALL_NOVA_FLAVOR_ID'),('demo-flavor-t4','demo.t4.c8.r32','Example single-GPU flavor.',8,32,100,1,16,'demo-hv-t4','demo-cpu-64c','demo-ram-ddr4-64g','demo-disk-nvme','demo-gpu-t4','demo-region',0,'REPLACE_WITH_T4_NOVA_FLAVOR_ID');
/*!40000 ALTER TABLE `flavor` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `floating_ip`
--

DROP TABLE IF EXISTS `floating_ip`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `floating_ip` (
  `id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `description` text COLLATE utf8mb4_zh_0900_as_cs,
  `region_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `ext_net_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `os_ext_net_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `os_project_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `os_floating_ip_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `floating_ip` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `port_mask` blob NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_floating_ip_region_os_id` (`region_id`,`os_floating_ip_id`),
  KEY `idx_floating_ip_ext_net` (`ext_net_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_zh_0900_as_cs;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `floating_ip`
--

LOCK TABLES `floating_ip` WRITE;
/*!40000 ALTER TABLE `floating_ip` DISABLE KEYS */;
INSERT INTO `floating_ip` VALUES ('a9314055-f999-4633-aba3-ac9521c65942','floating-ip-172.30.67.137',NULL,'Test-SH-01','2e2d09dd-7f09-4d69-866d-4d4b06062383','16bce9f5-729f-428e-9619-ea0c241d3868','31205d8c26f4471cbed5f8c5a689a585','2a899f59-4d64-4be3-a023-2a3ec4c9b818','172.30.67.137',_binary '0\0\0\0\0¿¿');
/*!40000 ALTER TABLE `floating_ip` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `gpu_type`
--

DROP TABLE IF EXISTS `gpu_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gpu_type` (
  `id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `vendor_name` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `vendor_code` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `product_name` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `product_code` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `description` text COLLATE utf8mb4_zh_0900_as_cs,
  `vram` bigint NOT NULL DEFAULT '0',
  `pci_passthrough_alias` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_zh_0900_as_cs;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gpu_type`
--

LOCK TABLES `gpu_type` WRITE;
/*!40000 ALTER TABLE `gpu_type` DISABLE KEYS */;
INSERT INTO `gpu_type` VALUES ('demo-gpu-t4','NVIDIA T4 16 GiB','NVIDIA',NULL,'T4',NULL,'Example GPU configuration; verify the device and Nova alias before use.',16,'REPLACE_WITH_NOVA_T4_ALIAS');
/*!40000 ALTER TABLE `gpu_type` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `hypervisor_gpu`
--

DROP TABLE IF EXISTS `hypervisor_gpu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `hypervisor_gpu` (
  `hypervisor_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `gpu_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `model` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `memory_mb` bigint DEFAULT NULL,
  `numa_node` int DEFAULT NULL,
  `traits` json NOT NULL,
  `assignment_status` varchar(32) COLLATE utf8mb4_zh_0900_as_cs NOT NULL DEFAULT 'AVAILABLE',
  `reservation_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `instance_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `resource_version` bigint NOT NULL,
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`hypervisor_id`,`gpu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_zh_0900_as_cs;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `hypervisor_gpu`
--

LOCK TABLES `hypervisor_gpu` WRITE;
/*!40000 ALTER TABLE `hypervisor_gpu` DISABLE KEYS */;
/*!40000 ALTER TABLE `hypervisor_gpu` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `hypervisor_host`
--

DROP TABLE IF EXISTS `hypervisor_host`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `hypervisor_host` (
  `id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `description` text COLLATE utf8mb4_zh_0900_as_cs,
  `hostname` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `ip` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `os_hypervisor_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `hypervisor_type` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `cpu_type` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `ram_type` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `disk_type` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `gpu_type` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `vcpus` int NOT NULL DEFAULT '0',
  `ram` int NOT NULL DEFAULT '0' COMMENT 'GB',
  `disk` int NOT NULL DEFAULT '0',
  `gpus` int NOT NULL DEFAULT '0',
  `used_vcpus` int NOT NULL DEFAULT '0',
  `used_ram` int NOT NULL DEFAULT '0' COMMENT 'GB',
  `used_disk` int NOT NULL DEFAULT '0',
  `used_gpus` int NOT NULL DEFAULT '0',
  `running_vms` int NOT NULL DEFAULT '0',
  `status` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `state` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `hypervisor_status` varchar(32) COLLATE utf8mb4_zh_0900_as_cs NOT NULL DEFAULT 'MAINTENANCE',
  `expected_maintenance_time` datetime(6) DEFAULT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `region_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hypervisor_host_region_os_id` (`region_id`,`os_hypervisor_id`),
  KEY `idx_hypervisor_host_region_name` (`region_id`,`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_zh_0900_as_cs;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `hypervisor_host`
--

LOCK TABLES `hypervisor_host` WRITE;
/*!40000 ALTER TABLE `hypervisor_host` DISABLE KEYS */;
INSERT INTO `hypervisor_host` VALUES ('37a385cc-926d-4e4d-9d7a-5dd7f447a007','GPUËÆ°ÁÆóËäÇÁÇπ01','Êõ¥Êñ∞Êé•Âè£ÊµãËØïÔºöÈÖçÁΩÆ4Âº†NVIDIA T4','test-openstack-node001','172.30.25.14','37a385cc-926d-4e4d-9d7a-5dd7f447a007','demo-hv-t4','demo-cpu-64c','demo-ram-ddr4-64g','demo-disk-nvme','demo-gpu-t4',12,23,199,4,6,12,50,0,0,'enabled','up','ACTIVE',NULL,0,'Test-SH-01'),('7b59db76-87f5-408e-9b79-8d3412f4f449','test-openstack-node002',NULL,'test-openstack-node002','172.30.25.15','7b59db76-87f5-408e-9b79-8d3412f4f449',NULL,NULL,NULL,NULL,NULL,12,23,199,0,5,10,30,0,0,'enabled','up','MAINTENANCE',NULL,0,'Test-SH-01');
/*!40000 ALTER TABLE `hypervisor_host` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `hypervisor_resource`
--

DROP TABLE IF EXISTS `hypervisor_resource`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `hypervisor_resource` (
  `hypervisor_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `availability_zone` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL DEFAULT 'default',
  `traits` json NOT NULL,
  `total_vcpu` int NOT NULL DEFAULT '0',
  `reserved_vcpu` int NOT NULL DEFAULT '0',
  `allocated_vcpu` int NOT NULL DEFAULT '0',
  `available_vcpu` int GENERATED ALWAYS AS (((`total_vcpu` - `reserved_vcpu`) - `allocated_vcpu`)) STORED,
  `total_memory_gb` bigint NOT NULL DEFAULT '0' COMMENT 'GB',
  `reserved_memory_gb` bigint NOT NULL DEFAULT '0' COMMENT 'GB',
  `allocated_memory_gb` bigint NOT NULL DEFAULT '0' COMMENT 'GB',
  `available_memory_gb` bigint GENERATED ALWAYS AS (((`total_memory_gb` - `reserved_memory_gb`) - `allocated_memory_gb`)) STORED,
  `total_gpu` int NOT NULL DEFAULT '0',
  `reserved_gpu` int NOT NULL DEFAULT '0',
  `allocated_gpu` int NOT NULL DEFAULT '0',
  `available_gpu` int GENERATED ALWAYS AS (((`total_gpu` - `reserved_gpu`) - `allocated_gpu`)) STORED,
  `resource_version` bigint NOT NULL DEFAULT '0',
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`hypervisor_id`),
  KEY `idx_hypervisor_candidate` (`status`,`availability_zone`,`available_vcpu`,`available_memory_gb`,`available_gpu`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_zh_0900_as_cs;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `hypervisor_resource`
--

LOCK TABLES `hypervisor_resource` WRITE;
/*!40000 ALTER TABLE `hypervisor_resource` DISABLE KEYS */;
INSERT INTO `hypervisor_resource` (`hypervisor_id`, `status`, `availability_zone`, `traits`, `total_vcpu`, `reserved_vcpu`, `allocated_vcpu`, `total_memory_gb`, `reserved_memory_gb`, `allocated_memory_gb`, `total_gpu`, `reserved_gpu`, `allocated_gpu`, `resource_version`, `updated_at`) VALUES ('37a385cc-926d-4e4d-9d7a-5dd7f447a007','ACTIVE','default','[]',12,0,0,23,0,0,4,0,0,2,'2026-10-23 09:14:33.889000');
/*!40000 ALTER TABLE `hypervisor_resource` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `hypervisor_type`
--

DROP TABLE IF EXISTS `hypervisor_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `hypervisor_type` (
  `id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `description` text COLLATE utf8mb4_zh_0900_as_cs,
  `vcpu_spec` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `gpu_spec` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `cpu_type` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `ram_type` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `disk_type` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `gpu_type` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `gpu_total` int NOT NULL DEFAULT '0',
  `numa_node_cnt` int NOT NULL DEFAULT '2',
  `socket_cnt` int NOT NULL DEFAULT '2',
  `vcpu_unit` int NOT NULL DEFAULT '0',
  `ram_unit` int NOT NULL DEFAULT '0',
  `disk_unit` int NOT NULL DEFAULT '0',
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `privately` tinyint(1) NOT NULL DEFAULT '0',
  `original_hourly_pay_price` decimal(19,6) DEFAULT NULL,
  `original_daily_pay_price` decimal(19,6) DEFAULT NULL,
  `original_weekly_pay_price` decimal(19,6) DEFAULT NULL,
  `original_monthly_pay_price` decimal(19,6) DEFAULT NULL,
  `discount_hourly_pay_price` decimal(19,6) DEFAULT NULL,
  `discount_daily_pay_price` decimal(19,6) DEFAULT NULL,
  `discount_weekly_pay_price` decimal(19,6) DEFAULT NULL,
  `discount_monthly_pay_price` decimal(19,6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_zh_0900_as_cs;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `hypervisor_type`
--

LOCK TABLES `hypervisor_type` WRITE;
/*!40000 ALTER TABLE `hypervisor_type` DISABLE KEYS */;
INSERT INTO `hypervisor_type` VALUES ('demo-hv-general','General purpose host','Example CPU-only host specification.','2 sockets x 32 cores',NULL,'demo-cpu-32c','demo-ram-ddr4-32g','demo-disk-ssd',NULL,0,2,2,4,16,100,1,0,1.000000,24.000000,168.000000,720.000000,0.800000,19.200000,134.400000,576.000000),('demo-hv-t4','T4 GPU host','Example host with four T4 GPUs.','2 sockets x 64 cores','4 x NVIDIA T4 16 GiB','demo-cpu-64c','demo-ram-ddr4-64g','demo-disk-nvme','demo-gpu-t4',4,2,2,8,32,100,1,0,4.000000,96.000000,672.000000,2880.000000,3.200000,76.800000,537.600000,2304.000000);
/*!40000 ALTER TABLE `hypervisor_type` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `image`
--

DROP TABLE IF EXISTS `image`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `image` (
  `id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `distro` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `version` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '0',
  `category_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `gpu_driver_installed` tinyint(1) NOT NULL DEFAULT '0',
  `gpu_driver` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `os_image_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `region_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_zh_0900_as_cs;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `image`
--

LOCK TABLES `image` WRITE;
/*!40000 ALTER TABLE `image` DISABLE KEYS */;
INSERT INTO `image` VALUES ('demo-image-debian','Debian 12 cloud image','debian','12',0,'demo-category-debian',0,NULL,'REPLACE_WITH_DEBIAN_GLANCE_IMAGE_ID','demo-region'),('demo-image-ubuntu','Ubuntu 24.04 cloud image','ubuntu','24.04',0,'demo-category-ubuntu',0,NULL,'REPLACE_WITH_UBUNTU_GLANCE_IMAGE_ID','demo-region');
/*!40000 ALTER TABLE `image` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `image_category`
--

DROP TABLE IF EXISTS `image_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `image_category` (
  `id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `distro` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `version` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_zh_0900_as_cs;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `image_category`
--

LOCK TABLES `image_category` WRITE;
/*!40000 ALTER TABLE `image_category` DISABLE KEYS */;
INSERT INTO `image_category` VALUES ('demo-category-debian','Debian 12','debian','12',1),('demo-category-ubuntu','Ubuntu 24.04 LTS','ubuntu','24.04',1);
/*!40000 ALTER TABLE `image_category` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `instance_allocation`
--

DROP TABLE IF EXISTS `instance_allocation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `instance_allocation` (
  `instance_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `hypervisor_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `reservation_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `vcpus` int NOT NULL,
  `memory_gb` bigint NOT NULL COMMENT 'GB',
  `gpu_count` int NOT NULL,
  `allocation_status` varchar(32) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `resource_version` bigint NOT NULL,
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`instance_id`),
  KEY `idx_allocation_hypervisor_status` (`hypervisor_id`,`allocation_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_zh_0900_as_cs;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `instance_allocation`
--

LOCK TABLES `instance_allocation` WRITE;
/*!40000 ALTER TABLE `instance_allocation` DISABLE KEYS */;
/*!40000 ALTER TABLE `instance_allocation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `instance_view`
--

DROP TABLE IF EXISTS `instance_view`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `instance_view` (
  `instance_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `request_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `phase` varchar(64) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `hypervisor_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `reservation_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `nova_operation_id` varchar(512) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `nova_server_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `nova_status` varchar(64) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `failure_code` varchar(128) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `failure_message` text COLLATE utf8mb4_zh_0900_as_cs,
  `workflow_version` bigint NOT NULL,
  `created_at` timestamp(6) NULL DEFAULT NULL,
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`instance_id`),
  KEY `idx_instance_hypervisor` (`hypervisor_id`),
  KEY `idx_instance_phase` (`phase`),
  KEY `idx_instance_nova_server` (`nova_server_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_zh_0900_as_cs;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `instance_view`
--

LOCK TABLES `instance_view` WRITE;
/*!40000 ALTER TABLE `instance_view` DISABLE KEYS */;
/*!40000 ALTER TABLE `instance_view` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `openstack_cluster`
--

DROP TABLE IF EXISTS `openstack_cluster`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `openstack_cluster` (
  `id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `description` text COLLATE utf8mb4_zh_0900_as_cs,
  `admin_username` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `admin_password` varchar(1024) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `admin_default_project_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `admin_default_domain_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `admin_default_region` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `auth_endpoint` varchar(1024) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `member_role_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `admin_role_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_zh_0900_as_cs;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `openstack_cluster`
--

LOCK TABLES `openstack_cluster` WRITE;
/*!40000 ALTER TABLE `openstack_cluster` DISABLE KEYS */;
INSERT INTO `openstack_cluster` VALUES ('demo-cluster','demo-cluster','Example cluster; replace all connection settings.','admin','YrH8AAjiiDD9QLq95n4lotkgXrE55kSrlgK30s0M','31205d8c26f4471cbed5f8c5a689a585','default','Test-SH-01','http://172.30.25.253:5000/v3','115b541c421c4e63a4969ae587682896','140d67b15fc445bebb8865db19323aa8');
/*!40000 ALTER TABLE `openstack_cluster` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pekko_event_journal`
--

DROP TABLE IF EXISTS `pekko_event_journal`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pekko_event_journal` (
  `slice` int NOT NULL,
  `entity_type` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `persistence_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `seq_nr` bigint NOT NULL,
  `db_timestamp` timestamp(6) NOT NULL,
  `event_ser_id` int NOT NULL,
  `event_ser_manifest` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `event_payload` longblob NOT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `writer` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `adapter_manifest` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `tags` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `meta_ser_id` int DEFAULT NULL,
  `meta_ser_manifest` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `meta_payload` longblob,
  PRIMARY KEY (`persistence_id`,`seq_nr`),
  KEY `pekko_event_journal_slice_idx` (`slice`,`entity_type`,`db_timestamp`,`seq_nr`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_zh_0900_as_cs;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pekko_event_journal`
--

LOCK TABLES `pekko_event_journal` WRITE;
/*!40000 ALTER TABLE `pekko_event_journal` DISABLE KEYS */;
INSERT INTO `pekko_event_journal` VALUES (442,'Hypervisor','Hypervisor|37a385cc-926d-4e4d-9d7a-5dd7f447a007',1,'2026-10-23 09:14:33.799972',33,'cn.veryai.arcreactor.actor.hypervisor.event.CapacityUpdated',_binary 'øhcapacityøjtotalVcpusmtotalMemoryGbdgpuspavailabilityZonegdefaultˇiupdatedAtx2026-10-23T09:14:33.789070Zˇ',0,'4c23b22c-0ea0-43f4-87cf-01a095dcc6e0','',NULL,NULL,NULL,NULL),(442,'Hypervisor','Hypervisor|37a385cc-926d-4e4d-9d7a-5dd7f447a007',2,'2026-10-23 09:14:33.889757',33,'cn.veryai.arcreactor.actor.hypervisor.event.HypervisorEnabled',_binary 'øienabledAtx2026-10-23T09:14:33.889354Zˇ',0,'4c23b22c-0ea0-43f4-87cf-01a095dcc6e0','',NULL,NULL,NULL,NULL);
/*!40000 ALTER TABLE `pekko_event_journal` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pekko_projection_management`
--

DROP TABLE IF EXISTS `pekko_projection_management`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pekko_projection_management` (
  `projection_name` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `projection_key` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `paused` tinyint(1) NOT NULL,
  `last_updated` bigint NOT NULL,
  PRIMARY KEY (`projection_name`,`projection_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_zh_0900_as_cs;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pekko_projection_management`
--

LOCK TABLES `pekko_projection_management` WRITE;
/*!40000 ALTER TABLE `pekko_projection_management` DISABLE KEYS */;
/*!40000 ALTER TABLE `pekko_projection_management` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pekko_projection_offset_store`
--

DROP TABLE IF EXISTS `pekko_projection_offset_store`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pekko_projection_offset_store` (
  `projection_name` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `projection_key` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `current_offset` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `manifest` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `mergeable` tinyint(1) NOT NULL,
  `last_updated` bigint NOT NULL,
  PRIMARY KEY (`projection_name`,`projection_key`),
  KEY `projection_name_index` (`projection_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_zh_0900_as_cs;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pekko_projection_offset_store`
--

LOCK TABLES `pekko_projection_offset_store` WRITE;
/*!40000 ALTER TABLE `pekko_projection_offset_store` DISABLE KEYS */;
INSERT INTO `pekko_projection_offset_store` VALUES ('hypervisor-resource','256-511','MjAyNi0xMC0yM1QwOToxNDozMy44ODk3NTdaOzIwMjYtMTAtMjdUMDY6MTA6MDAuNzk1MDY1WjtIeXBlcnZpc29yfDM3YTM4NWNjLTkyNmQtNGU0ZC05ZDdhLTVkZDdmNDQ3YTAwNzsy','39:TSO',0,1793081400818);
/*!40000 ALTER TABLE `pekko_projection_offset_store` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pekko_snapshot`
--

DROP TABLE IF EXISTS `pekko_snapshot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pekko_snapshot` (
  `slice` int NOT NULL,
  `entity_type` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `persistence_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `seq_nr` bigint NOT NULL,
  `write_timestamp` bigint NOT NULL,
  `ser_id` int NOT NULL,
  `ser_manifest` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `snapshot` longblob NOT NULL,
  `meta_ser_id` int DEFAULT NULL,
  `meta_ser_manifest` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `meta_payload` longblob,
  PRIMARY KEY (`persistence_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_zh_0900_as_cs;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pekko_snapshot`
--

LOCK TABLES `pekko_snapshot` WRITE;
/*!40000 ALTER TABLE `pekko_snapshot` DISABLE KEYS */;
/*!40000 ALTER TABLE `pekko_snapshot` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ram_type`
--

DROP TABLE IF EXISTS `ram_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ram_type` (
  `id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_zh_0900_as_cs;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ram_type`
--

LOCK TABLES `ram_type` WRITE;
/*!40000 ALTER TABLE `ram_type` DISABLE KEYS */;
INSERT INTO `ram_type` VALUES ('demo-ram-ddr4-32g','DDR4 ECC 32 GiB'),('demo-ram-ddr4-64g','DDR4 ECC 64 GiB');
/*!40000 ALTER TABLE `ram_type` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `region`
--

DROP TABLE IF EXISTS `region`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `region` (
  `cluster_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `description` text COLLATE utf8mb4_zh_0900_as_cs,
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  KEY `idx_region_cluster` (`cluster_id`),
  KEY `idx_region_enabled` (`cluster_id`,`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_zh_0900_as_cs;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `region`
--

LOCK TABLES `region` WRITE;
/*!40000 ALTER TABLE `region` DISABLE KEYS */;
INSERT INTO `region` VALUES ('demo-cluster','Test-SH-01','Test-SH-01','Disabled until a real OpenStack region is configured.',1);
/*!40000 ALTER TABLE `region` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_network`
--

DROP TABLE IF EXISTS `sys_network`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_network` (
  `id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `description` text COLLATE utf8mb4_zh_0900_as_cs,
  `ipv4_cidr` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `gateway` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `dns` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `segment_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `sys_network_type` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `physical_network` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `router_externally` tinyint(1) NOT NULL DEFAULT '0',
  `shared` tinyint(1) NOT NULL DEFAULT '0',
  `os_net_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `os_sub_net_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `os_network_type` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `os_project_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `region_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `host_route` json NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sys_network_region` (`region_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_zh_0900_as_cs;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_network`
--

LOCK TABLES `sys_network` WRITE;
/*!40000 ALTER TABLE `sys_network` DISABLE KEYS */;
INSERT INTO `sys_network` VALUES ('2e2d09dd-7f09-4d69-866d-4d4b06062383','sys_public_network',NULL,'172.30.64.0/21','172.30.64.1','223.5.5.5','2225','PUBLIC','physnet1',1,1,'16bce9f5-729f-428e-9619-ea0c241d3868','ecad5b72-97a3-41ce-b476-e1ff12d7a648','VLAN','31205d8c26f4471cbed5f8c5a689a585','Test-SH-01','{}'),('83a38efb-0328-43a0-8bba-59483699e056','sys_local_network',NULL,'172.30.48.0/20','172.30.48.1','223.5.5.5','2224','LOCAL','physnet1',1,1,'f464dee5-f8d1-477c-9184-56ad0bc33c04','5d6b18b5-81ac-4647-8f25-8e18e4c5aca7','VLAN','31205d8c26f4471cbed5f8c5a689a585','Test-SH-01','{}'),('946ac75c-6d3b-46de-a7c3-b061ba5c97d9','sys_shared_network',NULL,'100.125.0.0/16','100.125.0.1','223.5.5.5','3000000','SHARED',NULL,0,1,'84500586-ee27-4237-b7fe-0871e82819db','1ab6ae07-9493-4eaa-b72a-5df9baba61d4','GENEVE','31205d8c26f4471cbed5f8c5a689a585','Test-SH-01','{}');
/*!40000 ALTER TABLE `sys_network` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_router`
--

DROP TABLE IF EXISTS `sys_router`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_router` (
  `id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `ext_net_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `shared_net_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `os_router_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `os_project_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `os_ext_net_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `os_shared_net_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
  `region_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sys_router_region` (`region_id`),
  KEY `idx_sys_router_ext_net` (`ext_net_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_zh_0900_as_cs;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_router`
--

LOCK TABLES `sys_router` WRITE;
/*!40000 ALTER TABLE `sys_router` DISABLE KEYS */;
INSERT INTO `sys_router` VALUES ('75b647a1-30e7-4814-80da-d37811e84d79','sys_public_router','2e2d09dd-7f09-4d69-866d-4d4b06062383','946ac75c-6d3b-46de-a7c3-b061ba5c97d9','f632f981-0537-4b11-8431-3570d40fcbbb','31205d8c26f4471cbed5f8c5a689a585','16bce9f5-729f-428e-9619-ea0c241d3868','84500586-ee27-4237-b7fe-0871e82819db','Test-SH-01');
/*!40000 ALTER TABLE `sys_router` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_region_project`
--

DROP TABLE IF EXISTS `user_region_project`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_region_project` (
  `user_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `region_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  `project_id` varchar(255) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
  PRIMARY KEY (`user_id`,`region_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_zh_0900_as_cs;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_region_project`
--

LOCK TABLES `user_region_project` WRITE;
/*!40000 ALTER TABLE `user_region_project` DISABLE KEYS */;
INSERT INTO `user_region_project` VALUES ('test-user','Test-SH-01','1d5de76fedbd4d2083cf65ecc7815c08');
/*!40000 ALTER TABLE `user_region_project` ENABLE KEYS */;
UNLOCK TABLES;
SET @@SESSION.SQL_LOG_BIN = @MYSQLDUMP_TEMP_LOG_BIN;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-27 17:36:08
