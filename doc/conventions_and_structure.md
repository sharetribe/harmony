# Harmony Code Style & Structure

This document describes the code style and conventions we want to
follow when building the Harmony service. Currently it is both
incomplete and in a constant state of change, at least for now. When
patterns emerge and we want to capture them to support consistent
style and code architecture, we document them here.

## General code conventions

[The Clojure Style Guide](https://github.com/bbatsov/clojure-style-guide)
is a good reference for writing Clojure with good style. We try to
follow it as much as possible. It can be used in code reviews as a
reference and linked to to support argumenting in favour of one style
over some other. When we deliberately wish to use a different style
the exception should be documented in here.

When aliasing namespaces the starting point is
[Stuart Sierra's notes](https://stuartsierra.com/2015/05/10/clojure-namespace-aliases).

A line in a .clj file should be limited to 80 characters. You can
selectively make exceptions when breaking the line would hurt
readability. Obviously this is a more then little subjective and we
might clarify the rules later. One example of an exception case is an
enumeration of valid values that doesn't fit into 80 chars. The
default rule for adding breaks means adding a break after every
element which can result in an unbalanced function body where the
enumeration grabs all the focus:

```clojure
   (let [qp (format-params
             {:bookableId bookableId :start start :end end}
             {:cols cols :default-cols #{:id :marketplaceId :bookableId :customerId :status :seats :start :end}})]
```

The above is sometimes a better alternative to:

```clojure
   (let [qp (format-params
             {:bookableId bookableId :start start :end end}
             {:cols cols :default-cols #{:id
                                         :marketplaceId
                                         :bookableId
                                         :customerId
                                         :status
                                         :seats
                                         :start
                                         :end}})]
```

## Project specific conventions

This section documents conventions that either deviate from the
general conventions mentioned above or are specific to the Harmony
project.

### Directory structure

The Harmony service consists of generic services, generic utilities and independent submodules. The project directory structure reflects this convention:

* src/harmony/service - Generic services such as a web server
  component. Subdirs for services can contain service specific
  utilities.
* src/harmony/bookings - The bookings module including API endpoints,
  service implementations, db access, etc. You can think of an
  independent submodule as something like a Rails Engine.
* src/harmony/util - Generic utilities collected to namespaces by
  utility domain, e.g. logging or uuid handling.
* src/harmony/main - Application entry points for launching
  deployments. An entry point launches a system and a system can be a
  composition of one or more submodules + generic services.

### Accessing database

To talk to a SQL database we use
[the HugSQL library](http://www.hugsql.org/). You can read more about
the library in the linked documentation but the gist of it is that db
access functions are written as pure parameterized SQL and exposed
(via macro) as functions in desired namespace. On top of this we
implement a thin abstraction of db access functions that handle things
like translating between columns naming conventions in db and key
names of Clojure maps that represent domain data. For the bookings
submodule the layering looks like this:

```text
-- service implementation (harmony.bookings.service) --
                   |
                   |
                   v
-- our db access layer (harmony.bookings.db) --
                   |
                   |
                   v
-- functions generated from sql via HugSQL macro --
                   |
                   |
                   v
-- src/harmony/bookings/db/sql/bookings.sql --
                   |
                   |
                   v
-- MySQL 5.7. (exposed via Hikari Connection Pool) --
```

#### Naming db access functions

To make it clear which layer is in question we use a couple of simple
naming conventions. The HugSQL generated functions (defined in .sql
file) always have an insert- update- or select- prefixes (we might
define more later if need be, e.g. count-, or delete-). The prefix
tells what type of DML operation is in question. At the db access
layer level a function that creates data has a create- prefix and a
function that looks up data has a fetch- prefix.

#### Dynamic column specification

All the select-functions should be made to accept a :cols-paremeter to
dynamically control the column list in a select statement.

In a HugSQL .sql file it looks like this:

```sql
-- :name select-booking-by-id :? :1
-- :doc Get a booking by id
select :i*:cols from bookings
where id = :id;
```

The :id in this case is a query parameter and :cols is a seq of
columns to be selected.

select-functions are then written as arity 3 functions that take a db
connection, query parameters and a columns speficiation for the data
to be returned. In addition, fetch-functions should define an arity 2
version that omits the columns spec and uses a default column
spefication instead. An example from the bookings module:

```clojure
(defn fetch-bookable
  "Fetch a bookable by marketplaceId and refId."
  ([db query-params] (fetch-bookable db query-params {}))
  ([db {:keys [marketplaceId refId]} {:keys [cols]}]
   (let [qp (format-params
             {:marketplaceId marketplaceId :refId refId}
             {:cols cols :default-cols #{:id :marketplaceId :refId :authorId :unitType :activePlanId}})]
     (format-result (select-bookable-by-ref db qp)
                    {:as-keywords #{:unitType}}))))

```

This convention makes it possible for service functions to only issue
queries that fetch the data that is needed and no more. This avoids
the problem of overfetching (I'm looking at you ORMs) and allows using
covering indexes as an optimization technique. At the same time we
don't have to write a separate query for every select combination we
need.

### Avoid special case db access functions and queries

When we don't have the convenience of an established and widely scoped
DB access framework such as an ActiveRecord it's easy to get a
feeling we're writing trivial query functions one after the other. We
can alleviate this pain by defining a set of sane conventions and
leaning on dynamic access patterns like the dynamic column
specification above.

Both, the db access layer that is implemented on top of the HugSQL
functions and the HugSQL queries itself, should be limited as much as
possible. One way to do this is to not implement special queries where
the same effect can be achieved with an existing and/or more generic
query. One example is implementing an contains? query that checks if
the db contains a row for given parameters. Another example is
implementing a query that directly returns the uuid of a row for given
parameters. Both goals can just as well be achieved by implementing a
fetch-function with dynamic cols spec as described in the previous
section:

```clojure
;; contains?
(let [r (db/fetch-bookable db
                           {:marketplaceId m-id :refId ref-id}
                           {:cols :id})]
  (when (:id r)
    ;; Do the stuff, id was there so row exists))

;; select just id
(let [{bookable-id :id} (db/fetch-bookable
                          db
                          {:marketplaceId m-id :refId ref-id}
                          {:cols :id})]
  ;; Do the stuff with bookable-id

```

