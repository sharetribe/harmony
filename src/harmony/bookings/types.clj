(ns harmony.bookings.types
  (:require [schema.core :as s]))

(s/defschema Bookable
  {:id s/Uuid
   :type (s/eq :bookable)
   :marketplaceId s/Uuid
   :refId s/Uuid
   :authorId s/Uuid
   :unitType (s/enum :day :time)})

(s/defschema Plan
  {:id s/Uuid
   :type (s/eq :plan)
   :seats s/Int
   (s/optional-key :schedule) s/Any ;; Format TBD
   })

(s/defschema Allow
  {:id s/Uuid
   :type (s/eq :allow)
   (s/optional-key :seatsOverride) long
   :start s/Inst
   :end s/Inst})

(s/defschema Block
  {:id s/Uuid
   :type (s/eq :block)
   :start s/Inst
   :end s/Inst})

(s/defschema TimeSlot
  {:type (s/eq :timeSlot)
   :bookableId s/Uuid
   :unitType (s/enum :day :type)
   :seats s/Int
   :start s/Inst
   :end s/Inst
   :year s/Int
   :month s/Int
   :day s/Int
   :hour s/Int})

(s/defschema Booking
  {:id s/Uuid
   :type (s/eq :booking)
   :authorId s/Uuid
   :status (s/enum :initial :cancelled :paid :accepted :rejected)
   (s/optional-key :seats) s/Int
   :start s/Inst
   :end s/Inst})


(comment
  (s/check
   Bookable
   {:id (java.util.UUID/randomUUID)
    :type :bookable
    :marketplaceId (java.util.UUID/randomUUID)
    :refId (java.util.UUID/randomUUID)
    :authorId (java.util.UUID/randomUUID)
    :unitType :day})
  )

