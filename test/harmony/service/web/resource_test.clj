(ns harmony.service.web.resource-test
  (:require [harmony.service.web.resource :as resource]
            [clojure.test :refer :all]
            [schema.core :as s]))

(def Plan
  (resource/api-resource
   {:type :plan
    :attrs
    {:seats s/Int
     :planMode (s/enum :available :blocked :schedule)}
    :rels
    {:subPlan #'Plan}}))

(def Bookable
  (resource/api-resource
   {:type :bookable

    :attrs
    {:marketplaceId s/Uuid
     :name s/Str
     :unitType (s/enum :day :time)}

    :rels
    {:activePlan Plan
     :plans [Plan]}}))

(def fixed-uuid
  (let [ids-holder (atom {})]
    (fn [id]
      (if (contains? @ids-holder id)
        (get @ids-holder id)
        (get (swap! ids-holder assoc id (java.util.UUID/randomUUID)) id)))))

(deftest resource-normalization-single
  (testing "flat resource"
    (let [bookable {:id (fixed-uuid :booking)
                   :marketplaceId (fixed-uuid :marketplace)
                   :name "bookable resource 1"
                   :unitType :time}
          {:keys [data included]} (resource/-normalized Bookable bookable)]
      (is (= {:id (fixed-uuid :booking)
              :type :bookable}
             (select-keys data [:id :type])))
      (is (= {:marketplaceId (fixed-uuid :marketplace)
              :name "bookable resource 1"
              :unitType :time}
             (:attributes data)))))

  (testing "single relationships"
    (let [bookable {:id (fixed-uuid :bookable)
                    :unitType :time
                    :activePlan {:id (fixed-uuid :plan1)
                                 :seats 5
                                 :planMode :available}}
          {:keys [data included]} (resource/-normalized Bookable bookable)]
      (is (= [{:id (fixed-uuid :plan1)
               :type :plan
               :attributes {:seats 5
                            :planMode :available}
               :relationships {}}]
             included))
      (is (= {:activePlan {:id (fixed-uuid :plan1) :type :plan}}
             (:relationships data)))))

  (testing "2 level relationship tree"
    (let [bookable {:id (fixed-uuid :bookable)
                    :unitType :time
                    :activePlan {:id (fixed-uuid :plan1)
                                 :seats 5
                                 :planMode :available
                                 :subPlan {:id (fixed-uuid :plan2)
                                           :seats 3
                                           :planMode :blocked}}}
          {:keys [data included]} (resource/-normalized Bookable bookable)]
      (is (= (sort-by :id [{:id (fixed-uuid :plan1)
                            :type :plan
                            :attributes {:seats 5
                                         :planMode :available}
                            :relationships {:subPlan {:id (fixed-uuid :plan2) :type :plan}}}
                           {:id (fixed-uuid :plan2)
                            :type :plan
                            :attributes {:seats 3
                                         :planMode :blocked}
                            :relationships {}}])
             (sort-by :id included)))
      (is (= {:activePlan {:id (fixed-uuid :plan1) :type :plan}}
             (:relationships data)))))

  (testing "nil resource"
    (is (= {:data nil :included []}
           (resource/-normalized Bookable nil)))))


(deftest resource-normalization-sequence
  (testing "multiple resources"
    (let [bookable1 {:id (fixed-uuid :bookable1)
                     :name "bookable 1"
                     :unitType :time
                     :activePlan {:id (fixed-uuid :plan1)
                                  :seats 5
                                  :planMode :available}}
          bookable2 {:id (fixed-uuid :bookable2)
                     :name "bookable 2"
                     :unitType :time
                     :activePlan {:id (fixed-uuid :plan1)
                                  :seats 5
                                  :planMode :available}}
          {:keys [data included]} (resource/-normalized Bookable [bookable1 bookable2])]
      (is (= [{:id (fixed-uuid :bookable1)
               :type :bookable
               :attributes
               {:name "bookable 1"
                :unitType :time}
               :relationships {:activePlan {:id (fixed-uuid :plan1) :type :plan}}}
              {:id (fixed-uuid :bookable2)
               :type :bookable
               :attributes
               {:name "bookable 2"
                :unitType :time}
               :relationships {:activePlan {:id (fixed-uuid :plan1) :type :plan}}}]
             data))
      (is (= [{:id (fixed-uuid :plan1)
               :type :plan
               :attributes {:seats 5 :planMode :available}
               :relationships {}}]
             included)))))


(comment
  (let [bookable {:id (fixed-uuid :bookable)
                  :unitType :time
                  :activePlan {:id (fixed-uuid :plan1)
                               :seats 5
                               :planMode :available
                               :subPlan {:id (fixed-uuid :plan2) :seats 3 :planMode :blocked}}
                  :plans [{:id (fixed-uuid :plan2) :seats 3 :planMode :blocked}]}]
    (resource/-normalized Bookable bookable))

  (let [bookable {:id (fixed-uuid :bookable)
                  :unitType :time
                  :activePlan {:id (fixed-uuid :plan1)
                               :seats 5
                               :planMode :available}
                  :plans [{:id (fixed-uuid :plan1) :seats 5 :planMode :available}
                          {:id (fixed-uuid :plan2) :seats 3 :planMode :available}]}]
    (resource/-normalized Bookable bookable))
  )
