# Harmony

The Sharetribe Harmony backend for marketplace transaction functionalities.

## Starting a dockerized environment on localhost

This instruction will describe how to set up a local Harmony API on
localhost on OS X. This is intended for doing development work for
other services that will rely on and integrate to the Harmony API.

### Install docker, docker-compose

Use latest Docker for Mac: https://docs.docker.com/engine/installation/mac/

### Clone this repository and start the api

In the project root:

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

FIXME

## License and Copyright

Copyright © 2016 [Sharetribe Ltd](https://www.sharetribe.com).

Distributed under [The Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)

