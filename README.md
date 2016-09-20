# Harmony

The Sharetribe Harmony backend for marketplace transaction functionalities.

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

Next, run a script to provision an empty database:

```
mysql -u root -p -h 127.0.0.1 --port 13306 < setup_dev_db.sql
```

The mysql cli will prompt for root password. The default password is:
`harmony-root`. After the database is set up you can now start the API
service:

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

## License and Copyright

Copyright © 2016 [Sharetribe Ltd](https://www.sharetribe.com).

Distributed under [The Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)

