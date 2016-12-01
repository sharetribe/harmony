ALTER TABLE `bookings` DROP KEY `index_bookings_marketplace_bookable`;
--;;
ALTER TABLE `bookings` ADD KEY `index_bookings_marketplace_bookable` (`marketplace_id`, `bookable_id`);
