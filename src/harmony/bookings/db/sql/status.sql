-- Simple health check status query

-- :name count-bookings :? 1
-- :doc Returns booking count
select count(id) as count from bookings
