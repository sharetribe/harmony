# Harmony

[![CircleCI](https://circleci.com/gh/sharetribe/harmony/tree/master.svg?style=svg&circle-token=043fa482e17e0b7a80641714c91b41a6bc0d3a40)](https://circleci.com/gh/sharetribe/harmony/tree/master)

Availability management backend and API for Sharetribe marketplaces.

For more information about Sharetribe marketplaces and the core marketplace functionalities, see the [main Sharetribe repository](https://www.github.com/sharetribe/sharetribe).

Would you like to set up your marketplace in one minute without touching code? Head to [Sharetribe.com](https://www.sharetribe.com).

### Contents

- [Installation](#installation)
- [Changelog](#changelog)
- [Development](#development)
- [Testing](#testing)
- [Using in production](#using-in-production)
- [Deploying](#deploying)
- [Release](#release)
- [License](#license)

## Installation

### Requirements

* MySQL 5.7
* This component is designed to be deployed to [Amazon Web Services (AWS)](https://aws.amazon.com/) using [Docker](https://www.docker.com/) and [Convox](https://convox.com/) but AWS is not a hard requirement.

### Starting a dockerized environment on localhost

This instruction will describe how to set up a local Harmony API on
localhost on OS X. This is intended for doing development work for
other services that will rely on and integrate to the Harmony API.

1. Install `docker` and `docker-compose`

  Use latest Docker for Mac: https://docs.docker.com/engine/installation/mac/

1. Clone this repository and checkout the latest version

  ```
  git clone git://github.com/sharetribe/harmony.git
  cd harmony
  git checkout latest
  ```

1. Create database

  First, start up just the database service. In the project root:

  ```
  docker-compose up db
  ```

  Next, create an empty database and run migrations:

  ```
  echo "CREATE DATABASE IF NOT EXISTS harmony_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" | mysql -u root --password=harmony-root -h 127.0.0.1 --port 13306
  ```
  
  Then, run migrations:

  ```
  DB_PORT=13306 lein migrate migrate
  ```

  (Please note that using a bare password in command line command is insecure. Consider changing the default password.)

1. Start the services

  After the database is set up you can now start the API service:

  ```
  docker-compose up api
  ```

  Congratulations! The Harmony API Swagger UI is available at [http://localhost:8085/apidoc/index.html](http://localhost:8085/apidoc/index.html).

  Next time, when the database has been setup you can just run
  `docker-compose up` to start both services in one go.

  ```
  docker-compose up
  ```

1. Exiting

  To stop the service, use either `Ctrl+C` or `docker-compose down`

### Rebuild new code

1. Exit the running container.

  ```
  docker-compose down
  ```

1. Pull the newest code and checkout the latest version

  ```
  git pull
  git checkout latest
  ```

1. Rebuild and restart the service

  ```
  docker-compose build
  docker-compose up
  ```

1. Run migrations

  ```
  DB_PORT=13306 lein migrate migrate
  ```

For production use we recommend you to upgrade only when new version is released and **not** to follow the master branch.


### Cleaning old docker containers:

In case the old containers are conflicting somehow with new ones or
there is a need to remove old exited containers:

```
docker-compose rm -v
```

## Changelog

See [CHANGELOG.md](./CHANGELOG.md) for detailed list of changes between releases.

## Development

When developing Harmony it often easier to run the service outside
Docker. In this case, you can still use Docker to run the database. In
the development configuration everything is already setup to connect
to the exposed port of `13306` using the correct root user
password. Just run `docker-compose up db`, navigate to user-namespace
in your favourite REPL client and run `(reset)`.

### Database clean up

The database service data volume is mounted in the host OS to
`~/.sharetribe/harmony-mysql-data/`. This means that the database
contents are persisted even across removing and rebuilding the MySQL
db container. To completely clean up your development database just
delete the aforementioned directory in your home directory:

  ```
  rm -r  ~/.sharetribe/harmony-mysql-data/
  ```

### Architecture and coding conventions

Check the conventions for code style and architure: [conventions and structure](doc/conventions_and_structure.md).

## Testing

Integrations tests (`test/harmony/integration/`) run against a live
MySQL database and a live web server. By default, the web server is
setup to run at `localhost:8086`. It assumes this port is available for
binding.

The default configuration also assumes a MySQL server running at
`127.0.0.1:13306` (the setup provided in `docker-compose`
configuration). To run the integration tests locally you need to have
the docker container for db running. By default, the tests use
`harmony_test_db` database. The contents of this DB are refreshed after
each test using the Migratus migrations (`resources/migrations/`).

## Using in production

For production use, we recommend using [Convox](https://convox.com/) and a proper production-ready database, such as [Amazon RDS](https://aws.amazon.com/rds/). The Harmony Docker container is optimized for development use.

Before going to production, you need to change secret keys and probably some other configurations, e.g. database server, database username and password and authentication tokens. See [./resources/conf/harmony-api.edn](./resources/conf/harmony-api.edn) for more information about all the possible configurations.

## Deploying

1. Make sure you're using the right Convox rack:

  ```bash
  convox rack
  ```

  If you need to change the rack, you can first list available racks:

  ```bash
  convox racks
  ```

  ...and then switch the rack

  ```bash
  convox switch <rack name>
  ```

1. Deploy:

  To list all available apps:

  ```
  convox apps
  ```

  Deploy:

  ```
  convox deploy -f docker-compose.harmony-api.yml -a <app name> --wait
  ```

1. Run migrations (if needed):

  ```
  convox run api lein migrate migrate -a <app name>
  ```

## Release

See [RELEASE.md](./RELEASE.md) for information about how to make a new release.

## License

Copyright 2017 [Sharetribe Ltd](https://www.sharetribe.com).

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
