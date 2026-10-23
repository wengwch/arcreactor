-- Initialization examples for the current schema. Select the application database first.
-- Apply hardware-types.sql, hypervisors.sql, openstack.sql and system-networks.sql
-- before this file. Existing databases must apply the relevant migrations first.
-- Re-running this file preserves existing rows, including locally edited values.
-- All IDs use the demo- prefix. Prices and hardware configurations are illustrative.
-- OpenStack IDs, credentials, endpoints and PCI aliases must be replaced for real use.
-- Regions, hosts, images and flavors stay disabled until their OpenStack resources exist.
-- No actor journals, snapshots, offsets or projection rows are seeded: those are
-- managed by Pekko and must be populated through the application's event flow.

START TRANSACTION;

-- Hardware dictionaries. Memory uses GB (1024 MB); disk capacities use GiB.
INSERT INTO cpu_type (id, name)
VALUES ('demo-cpu-32c', 'x86_64 32-core CPU (example)'),
       ('demo-cpu-64c', 'x86_64 64-core CPU (example)')
ON DUPLICATE KEY UPDATE id = cpu_type.id;

INSERT INTO ram_type (id, name)
VALUES ('demo-ram-ddr4-32g', 'DDR4 ECC 32 GB'),
       ('demo-ram-ddr4-64g', 'DDR4 ECC 64 GB')
ON DUPLICATE KEY UPDATE id = ram_type.id;

INSERT INTO disk_type (id, name)
VALUES ('demo-disk-ssd', 'SATA SSD'),
       ('demo-disk-nvme', 'NVMe SSD')
ON DUPLICATE KEY UPDATE id = disk_type.id;

-- pci_passthrough_alias must match the alias configured in Nova.
INSERT INTO gpu_type (id, name, vendor_name, vendor_code, product_name, product_code,
                      description, vram, pci_passthrough_alias)
VALUES ('demo-gpu-t4', 'NVIDIA T4 16 GB', 'NVIDIA', NULL, 'T4', NULL,
        'Example GPU configuration; verify the device and Nova alias before use.',
        16, 'REPLACE_WITH_NOVA_T4_ALIAS')
ON DUPLICATE KEY UPDATE id = gpu_type.id;

INSERT INTO hypervisor_type (id, name, description, vcpu_spec, gpu_spec, cpu_type, ram_type, disk_type,
                             gpu_type, gpu_total, numa_node_cnt, socket_cnt, vcpu_unit, ram_unit, disk_unit,
                             enabled, privately,
                             original_hourly_pay_price, original_daily_pay_price,
                             original_weekly_pay_price, original_monthly_pay_price,
                             discount_hourly_pay_price, discount_daily_pay_price,
                             discount_weekly_pay_price, discount_monthly_pay_price)
VALUES ('demo-hv-general', 'General purpose host', 'Example CPU-only host specification.',
        '2 sockets x 32 cores', NULL,
        'demo-cpu-32c', 'demo-ram-ddr4-32g', 'demo-disk-ssd', NULL, 0,
        2, 2, 4, 16, 100, TRUE, FALSE,
        1.000000, 24.000000, 168.000000, 720.000000,
        0.800000, 19.200000, 134.400000, 576.000000),
       ('demo-hv-t4', 'T4 GPU host', 'Example host with four T4 GPUs.',
        '2 sockets x 64 cores', '4 x NVIDIA T4 16 GB',
        'demo-cpu-64c', 'demo-ram-ddr4-64g', 'demo-disk-nvme', 'demo-gpu-t4', 4,
        2, 2, 8, 32, 100, TRUE, FALSE,
        4.000000, 96.000000, 672.000000, 2880.000000,
        3.200000, 76.800000, 537.600000, 2304.000000)
ON DUPLICATE KEY UPDATE id = hypervisor_type.id;

INSERT INTO image_category (id, name, distro, version, enabled)
VALUES ('demo-category-ubuntu', 'Ubuntu 24.04 LTS', 'ubuntu', '24.04', TRUE),
       ('demo-category-debian', 'Debian 12', 'debian', '12', TRUE)
ON DUPLICATE KEY UPDATE id = image_category.id;

-- Demonstration topology. These rows describe resources; SQL does not create
-- projects, images, flavors, networks, routers or hypervisors in OpenStack.
INSERT INTO openstack_cluster (id, name, description, admin_username, admin_password,
                               admin_default_project_id, admin_default_domain_id, admin_default_region,
                               auth_endpoint, member_role_id, admin_role_id)
VALUES ('demo-cluster', 'demo-cluster', 'Example cluster; replace all connection settings.', 'admin',
        'YrH8AAjiiDD9QLq95n4lotkgXrE55kSrlgK30s0M', '31205d8c26f4471cbed5f8c5a689a585', 'default', 'Test-SH-01',
        'http://172.30.25.253:5000/v3', '115b541c421c4e63a4969ae587682896', '140d67b15fc445bebb8865db19323aa8')
ON DUPLICATE KEY
    UPDATE id = openstack_cluster.id;


-- The region ID must match the actual OpenStack region name when enabling it.
-- Update its references in this script together with admin_default_region.
INSERT INTO arc_reactor.region (cluster_id, id, name, description, enabled)
VALUES ('demo-cluster', 'Test-SH-01', 'Test-SH-01', 'Disabled until a real OpenStack region is configured.', 1)
ON DUPLICATE KEY
    UPDATE id = region.id;

INSERT INTO image (id, name, distro, version, enabled, category_id, gpu_driver_installed,
                   gpu_driver, os_image_id, region_id)
VALUES ('demo-image-ubuntu', 'Ubuntu 24.04 cloud image', 'ubuntu', '24.04', FALSE,
        'demo-category-ubuntu', FALSE, NULL,
        'REPLACE_WITH_UBUNTU_GLANCE_IMAGE_ID', 'demo-region'),
       ('demo-image-debian', 'Debian 12 cloud image', 'debian', '12', FALSE,
        'demo-category-debian', FALSE, NULL,
        'REPLACE_WITH_DEBIAN_GLANCE_IMAGE_ID', 'demo-region')
ON DUPLICATE KEY UPDATE id = image.id;

-- flavor.ram follows FlavorService's stored GB value (Nova RAM / 1024).
-- vram is total GPU memory in GB: gpu_type.vram multiplied by gpus.
INSERT INTO flavor (id, name, description, vcpus, ram, disk, gpus, vram,
                    hypervisor_type, cpu_type, ram_type, disk_type, gpu_type,
                    region_id, enabled, os_flavor_id)
VALUES ('demo-flavor-small', 'demo.c4.r16', 'Example general purpose flavor.',
        4, 16, 100, 0, 0, 'demo-hv-general', 'demo-cpu-32c',
        'demo-ram-ddr4-32g', 'demo-disk-ssd', NULL,
        'demo-region', FALSE, 'REPLACE_WITH_SMALL_NOVA_FLAVOR_ID'),
       ('demo-flavor-medium', 'demo.c8.r32', 'Example larger general purpose flavor.',
        8, 32, 200, 0, 0, 'demo-hv-general', 'demo-cpu-32c',
        'demo-ram-ddr4-32g', 'demo-disk-ssd', NULL,
        'demo-region', FALSE, 'REPLACE_WITH_MEDIUM_NOVA_FLAVOR_ID'),
       ('demo-flavor-t4', 'demo.t4.c8.r32', 'Example single-GPU flavor.',
        8, 32, 100, 1, 16, 'demo-hv-t4', 'demo-cpu-64c',
        'demo-ram-ddr4-64g', 'demo-disk-nvme', 'demo-gpu-t4',
        'demo-region', FALSE, 'REPLACE_WITH_T4_NOVA_FLAVOR_ID')
ON DUPLICATE KEY UPDATE id = flavor.id;

INSERT INTO hypervisor_host (id, name, description, hostname, ip, os_hypervisor_id,
                             hypervisor_type, cpu_type, ram_type, disk_type, gpu_type,
                             vcpus, ram, disk, gpus, used_vcpus, used_ram, used_disk, used_gpus,
                             status, state, enabled, region_id)
VALUES ('demo-host-general-01', 'General compute 01', 'Disabled example compute host.',
        'demo-compute-01', '192.0.2.11', 'REPLACE_WITH_GENERAL_NOVA_HYPERVISOR_ID',
        'demo-hv-general', 'demo-cpu-32c', 'demo-ram-ddr4-32g', 'demo-disk-ssd', NULL,
        64, 256, 2000, 0, 0, 0, 0, 0, 'disabled', 'down', FALSE, 'demo-region'),
       ('demo-host-t4-01', 'GPU compute 01', 'Disabled example GPU compute host.',
        'demo-gpu-01', '192.0.2.12', 'REPLACE_WITH_T4_NOVA_HYPERVISOR_ID',
        'demo-hv-t4', 'demo-cpu-64c', 'demo-ram-ddr4-64g', 'demo-disk-nvme', 'demo-gpu-t4',
        128, 512, 4000, 4, 0, 0, 0, 0, 'disabled', 'down', FALSE, 'demo-region')
ON DUPLICATE KEY UPDATE id = hypervisor_host.id;

-- Enum values match SysNetworkType and OpenStack4j NetworkType names.
-- host_route is an object of destination CIDR -> next-hop IP, not an array.
INSERT INTO sys_network (id, name, description, ipv4_cidr, gateway, dns, segment_id,
                         sys_network_type, physical_network, router_externally, shared,
                         os_net_id, os_sub_net_id, os_network_type, os_project_id, region_id, host_route)
VALUES ('demo-net-public', 'Demo public network', 'Example external provider network.',
        '192.0.2.0/24', '192.0.2.1', '1.1.1.1', '100',
        'PUBLIC', 'REPLACE_WITH_PUBLIC_PHYSICAL_NETWORK', TRUE, TRUE,
        'REPLACE_WITH_PUBLIC_NEUTRON_NETWORK_ID', 'REPLACE_WITH_PUBLIC_NEUTRON_SUBNET_ID',
        'VLAN', 'REPLACE_WITH_ADMIN_PROJECT_ID', 'demo-region', JSON_OBJECT()),
       ('demo-net-local', 'Demo local network', 'Example internal provider network.',
        '10.20.0.0/24', '10.20.0.1', '1.1.1.1', '200',
        'LOCAL', 'REPLACE_WITH_LOCAL_PHYSICAL_NETWORK', FALSE, FALSE,
        'REPLACE_WITH_LOCAL_NEUTRON_NETWORK_ID', 'REPLACE_WITH_LOCAL_NEUTRON_SUBNET_ID',
        'VLAN', 'REPLACE_WITH_ADMIN_PROJECT_ID', 'demo-region', JSON_OBJECT()),
       ('demo-net-shared', 'Demo shared network', 'Example shared overlay network.',
        '10.30.0.0/24', '10.30.0.1', '1.1.1.1', NULL,
        'SHARED', NULL, FALSE, TRUE,
        'REPLACE_WITH_SHARED_NEUTRON_NETWORK_ID', 'REPLACE_WITH_SHARED_NEUTRON_SUBNET_ID',
        'GENEVE', 'REPLACE_WITH_ADMIN_PROJECT_ID', 'demo-region', JSON_OBJECT())
ON DUPLICATE KEY UPDATE id = sys_network.id;

INSERT INTO sys_router (id, name, ext_net_id, shared_net_id, os_router_id, os_project_id,
                        os_ext_net_id, os_shared_net_id, region_id)
VALUES ('demo-router', 'Demo shared router', 'demo-net-public', 'demo-net-shared',
        'REPLACE_WITH_NEUTRON_ROUTER_ID', 'REPLACE_WITH_ADMIN_PROJECT_ID',
        'REPLACE_WITH_PUBLIC_NEUTRON_NETWORK_ID', 'REPLACE_WITH_SHARED_NEUTRON_NETWORK_ID',
        'demo-region')
ON DUPLICATE KEY UPDATE id = sys_router.id;

COMMIT;
