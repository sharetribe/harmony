-- Manage bookables and availability plans

-- :name insert-bookable :! :n 
-- :doc Insert a new bookable
insert into bookables (id, marketplace_id, ref_id, author_id, unit_type, active_plan_id)
values (:id, :marketplaceId, :refId, :authorId, :unitType, :activePlanId);

-- :name find-bookable-by-ref :? :1
-- :doc Get a bookable by referenced id and marketplace id
select :i*:cols from bookables
where marketplace_id = :marketplaceId AND ref_id = :refId;

-- :name insert-plan :! :n
-- :doc Insert a new plan
insert into plans (id, marketplace_id, bookable_id, seats, plan_mode)
values (:id, :marketplaceId, :bookableId, :seats, :planMode);

-- :name find-plan-by-id :? :1
-- :doc Get a plan by id
select :i*:cols from plans
where id = :id;

-- :name insert-booking :! :n
-- :Doc Insert a new booking
insert into bookings (id, marketplace_id, bookable_id, customer_id, status, seats, start, end)
values (:id, :marketplaceId, :bookableId, :customerId, :status, :seats, :start, :end);

-- :name find-booking-by-id :? :1
-- :doc Get a booking by id
select :i*:cols from bookings
where id = :id;

-- :name find-bookings-by-bookable-start-end :? :*
-- :doc Get bookables by bookableId, start end end
select :i*:cols from bookings
where bookable_id = :bookableId
AND (
  (end > :start AND end <= :end)
  OR
  (start >= :start AND start < :end)
  OR
  (start <= :start AND end >= :end)
);
