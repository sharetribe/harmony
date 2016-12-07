-- Manage bookables and availability plans

-- :name insert-bookable :! :n 
-- :doc Insert a new bookable
insert into bookables (id, marketplace_id, ref_id, author_id, unit_type, active_plan_id)
values (:id, :marketplaceId, :refId, :authorId, :unitType, :activePlanId);

-- :name select-bookable-by-ref :? :1
-- :doc Get a bookable by referenced id and marketplace id
select :i*:cols from bookables
where marketplace_id = :marketplaceId AND ref_id = :refId;

-- :name select-for-update-bookable-by-id :? :1
-- :doc Select a bookable by primary key and get an "for update" lock to it.
select :i*:cols from bookables
where id = :id
for update;

-- :name insert-plan :! :n
-- :doc Insert a new plan
insert into plans (id, marketplace_id, bookable_id, seats, plan_mode)
values (:id, :marketplaceId, :bookableId, :seats, :planMode);

-- :name select-plan-by-id :? :1
-- :doc Get a plan by id
select :i*:cols from plans
where id = :id;

-- :name insert-booking :! :n
-- :Doc Insert a new booking
insert into bookings (id, marketplace_id, bookable_id, customer_id, status, seats, start, end)
values (:id, :marketplaceId, :bookableId, :customerId, :status, :seats, :start, :end);

-- :name update-booking-status :! :n
-- :doc Update booking
update bookings set status = :status
where id = :id;

-- :name select-booking-by-id :? :1
-- :doc Get a booking by id
select :i*:cols from bookings
where id = :id;

-- :name select-bookings-by-bookable-start-end-status :? :*
-- :doc Get bookables by bookableId, start, end, and optionally status
select :i*:cols from bookings
where bookable_id = :bookableId
/*~ (when (seq (:statuses params)) */
AND status in (:v*:statuses)
/*~ ) ~*/
AND (
  (end > :start AND end <= :end)
  OR
  (start >= :start AND start < :end)
  OR
  (start <= :start AND end >= :end)
);

-- :name insert-exceptions :! :n
-- :Doc Insert a new exceptions
insert into exceptions (id, type, marketplace_id, bookable_id, seats_override, start, end)
values :tuple*:exceptions

-- :name select-exceptions-by-bookable-start-end-type :? :*
-- :doc Get exceptions by bookableId, start, end, and type
select :i*:cols from exceptions
where bookable_id = :bookableId
AND deleted = false
AND type = :type
AND (
  (end > :start AND end <= :end)
  OR
  (start >= :start AND start < :end)
  OR
  (start <= :start AND end >= :end)
);

-- :name select-exceptions-by-ids-bookable :? :*
-- :doc Get exceptions by ids and bookableId
select :i*:cols from exceptions
where id in (:v*:ids)
and deleted = false
and bookable_id = :bookableId

-- :name update-exceptions-deleted-by-ids :! :n
-- :Doc Update exceptions
update exceptions set deleted = :deleted where id in (:v*:ids)
