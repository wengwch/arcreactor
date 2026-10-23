-- Backfill existing host statuses before deploying the host status projection.
-- Apply sql/read-model.sql and the earlier hypervisor maintenance migrations first.
-- Stop application instances while migrating. New installations use sql/hypervisors.sql.
-- Seed status from the existing scheduler projection without resetting its offsets.
-- Keep maintenance times previously recorded by the API.
UPDATE hypervisor_host AS host
JOIN hypervisor_resource AS resource ON resource.hypervisor_id = host.id
SET host.hypervisor_status = resource.status;
