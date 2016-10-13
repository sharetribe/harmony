CREATE INDEX `index_bookings_bookable_status` ON `bookings` (`bookable_id`, `status`);
--;;
DROP INDEX `index_bookings_bookable` ON `bookings`;
