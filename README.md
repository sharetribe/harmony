# Harmony

[![CircleCI](https://circleci.com/gh/sharetribe/harmony/tree/master.svg?style=svg&circle-token=043fa482e17e0b7a80641714c91b41a6bc0d3a40)](https://circleci.com/gh/sharetribe/harmony/tree/master)

The Sharetribe Harmony backend for marketplace transaction functionalities.

## Requirements

* MySQL 5.7
* This component is designed to be deployed to AWS using Docker and Convox but AWS is not a hard requirement.

## Starting a dockerized environment on localhost

This instruction will describe how to set up a local Harmony API on
localhost on OS X. This is intended for doing development work for
other services that will rely on and integrate to the Harmony API.

### Install docker, docker-compose

Use latest Docker for Mac: https://docs.docker.com/engine/installation/mac/

### Clone this repository and start the services

If this is the first time you are setting harmony up and you haven't
yet created an empty database you must do that as the first step:

First, start up just the database service. In the project root:

```
docker-compose up db
```

Next, create an empty database and run migrations:

```
echo "CREATE DATABASE IF NOT EXISTS harmony_db;" | mysql -u root -p -h 127.0.0.1 --port 13306
DB_PORT=13306 lein migrate migrate
```

After the database is set up you can now start the API service:

```
docker-compose up api
```

Next time, when the database has been setup you can just run
docker-compose up to start both services in one go.

```
docker-compose up
```

The Harmony API Swagger UI is available at `http://localhost:8085/apidoc/index.html`.

Exiting:

`Ctrl+C` or `docker-compose down`

### Rebuilding new code:

Exit the running container.

```
git pull
docker-compose build
docker-compose up
```

### Cleaning old docker containers:

In case the old containers are conflicting somehow with new ones or
there is a need to remove old exited containers:

```
docker-compose rm -v
```

## Development

When developing Harmony it often easier to run the service outside
Docker. In this case, you can still use docker to run the database. In
the development configuration everything is already setup to connect
to the exposed port of 13306 using the correct root user
password. Just run `docker-compose up db`, navigate to user-namespace
in your favourite REPL client and run `(reset)`.

The database service data volume is mounted in the host OS to
~/.sharetribe/harmony-mysql-data/. This means that the database
contents are persisted even across removing and rebuilding the MySQL
db container. To completely clean up your development database just
delete the aforementioned directory in your home directory.

Check the conventions for code style and architure: [conventions and structure](doc/conventions_and_structure.md).

## Testing

Integrations tests (test/harmony/integration/) run against a live
MySQL database and a live web server. By default, the web server is
setup to run at localhost:8086. It assumes this port is available for
binding.

The default configuration also assumes a MySQL server running at
127.0.0.1:13306 (the setup provided in docker-compose
configuration). To run the integration tests locally you need to have
the docker container for db running. By default, the tests use
harmony_test_db database. The contents of this DB are refreshed after
each test using the Migratus migrations (resources/migrations/).

## Deploying with ChatOps

`master` branch to `stating`:

```
@Sharebot deploy harmony-api/master to staging
```

`master` branch to `production`:

```
@Sharebot deploy harmony-api/master to production
```

## Deploying with Convox

1. Make sure you're using the right Convox rack:

  ```bash
  convox rack
  ```

1. Deploy:

  ```
  convox deploy -f docker-compose.harmony-api.yml -a <app name> --wait
  ```

1. Run migrations (if needed):

  ```
  convox run api lein migrate migrate -a <app name>
  ```

## License and Copyright

Copyright © 2016 [Sharetribe Ltd](https://www.sharetribe.com).

Distributed under [The Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)

