CREATE TABLE IF NOT EXISTS `exceptions`(
  `id` binary(16) NOT NULL,
  `marketplace_id` binary(16) NOT NULL,
  `type` varchar(32) NOT NULL,
  `bookable_id` binary(16) NOT NULL,
  `seats_override` int(11),
  `start` datetime NOT NULL,
  `end` datetime NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `index_bookings_marketplace_exceptions` (`bookable_id`, `marketplace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
