-- Run once on an existing database created with the old table names.
-- Run before applying sql/openstack.sql; the destination tables must not exist.
-- New installations should use sql/openstack.sql directly.
RENAME TABLE
  openstack_region TO region,
  openstack_image TO image,
  openstack_flavor TO flavor;
