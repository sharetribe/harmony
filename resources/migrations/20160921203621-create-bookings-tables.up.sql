CREATE TABLE IF NOT EXISTS `bookables`(
  `id` binary(16) NOT NULL,
  `marketplace_id` binary(16) NOT NULL,
  `ref_id` binary(16) NOT NULL,
  `author_id` binary(16) NOT NULL,
  `unit_type` varchar(64) NOT NULL DEFAULT 'day',
  `active_plan_id` binary(16) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `index_bookables_marketplace_refid` (`marketplace_id`, `ref_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
--;;
CREATE TABLE IF NOT EXISTS `plans`(
  `id` binary(16) NOT NULL,
  `marketplace_id` binary(16) NOT NULL,
  `bookable_id` binary(16) NOT NULL,
  `seats` int(11) NOT NULL DEFAULT '1',
  `plan_mode` varchar(64) NOT NULL DEFAULT 'available',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `index_plans_marketplace_bookable` (`marketplace_id`, `bookable_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

