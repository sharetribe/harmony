CREATE INDEX `index_bookings_bookable` ON `bookings` (`bookable_id`);
--;;
DROP INDEX `index_bookings_bookable_status` ON `bookings`;
