(ns harmony.bookings.types
  (:require [schema.core :as s]
            [harmony.service.web.resource :as resource]))

(declare Bookable Plan Allow Block TimeSlot Booking)

(def Bookable
  (resource/api-resource
   {:type :bookable

    :attrs
    {:marketplaceId s/Uuid
     :refId s/Uuid
     :authorId s/Uuid
     :unitType (s/enum :day :time)}

    :rels
    {:activePlan #'Plan
     :plans [#'Plan]
     :bookings [#'Booking]
     :allows [#'Allow]
     :blocks [#'Block]}}))

(def Plan
  (resource/api-resource
   {:type :plan
    :attrs
    {:seats s/Int
     :planMode (s/enum :available :blocked :schedule)}}))


(def Allow
  (resource/api-resource
   {:type :allow
    :attrs
    {(s/optional-key :seatsOverride) long
     :start s/Inst
     :end s/Inst}}))

(def Block
  (resource/api-resource
   {:type :block
    :attrs
    {:start s/Inst
     :end s/Inst}}))

(def TimeSlot
  (resource/api-resource
   {:type :timeSlot
    :attrs
    {:refId s/Uuid
     :unitType (s/enum :day :type)
     :seats s/Int
     :start s/Inst
     :end s/Inst
     :year s/Int
     :month s/Int
     :day s/Int}}))

(def Booking
  (resource/api-resource
   {:type :booking
    :attrs
    {:customerId s/Uuid
     :status (s/enum :initial :cancelled :paid :accepted :rejected)
     (s/optional-key :seats) s/Int
     :start s/Inst
     :end s/Inst}}))


(comment
  (s/check
   Bookable
   {:id (java.util.UUID/randomUUID)
    :type :bookable
    :marketplaceId (java.util.UUID/randomUUID)
    :refId (java.util.UUID/randomUUID)
    :authorId (java.util.UUID/randomUUID)
    :unitType :day})


  Bookable
  (resource/created-response-schema Bookable)
  (resource/created-response-schema Plan)

  (sequential? [:a])
  (sequential? '(:a))
  (sequential? {:a 1})
  )

