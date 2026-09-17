-- Run once for existing databases before applying sql/persistence.sql.
-- Stop application instances before migration; destination tables must not exist.
-- New installations should use sql/persistence.sql directly.
RENAME TABLE
  event_journal TO pekko_event_journal,
  snapshot TO pekko_snapshot;
