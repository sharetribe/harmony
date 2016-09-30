CREATE TABLE IF NOT EXISTS `bookings`(
  `id` binary(16) NOT NULL,
  `marketplace_id` binary(16) NOT NULL,
  `bookable_id` binary(16) NOT NULL,
  `customer_id` binary(16) NOT NULL,
  `status` varchar(64) NOT NULL DEFAULT 'initial',
  `seats` int(11) NOT NULL DEFAULT '1',
  `start` datetime NOT NULL,
  `end` datetime NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `index_bookings_marketplace_bookable` (`marketplace_id`, `bookable_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
