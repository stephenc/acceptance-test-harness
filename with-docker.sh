#!/bin/sh

if [ $# -lt 1 ]; then
	cat <<USAGE
Usage: $0 COMMAND [ARGS]

The script runs the command from within a docker image with VNC configured and both ~/.m2 and the current directory
bind mounted into the appropriate places. It assumes that your are using your local docker.

You can connect to the running VNC server on display :42 on localhost:5942 with password 'ath-user'.

Examples:

# Run full suite in FF against ./jenkins.war.
$ $0 ./run.sh firefox ./jenkins.war

# Run full suite in FF against LTS release candidate
$ $0 ./run.sh firefox lts-rc

# Debug just the login_ok LDAP plugin test in firefox against Jenkins 1.512.
# Attach your remote debugger to port 5005
$ $0 ./debug.sh firefox 1.512 -Dtest=LdapPluginTest\#login_ok

USAGE
  exit -2
fi

CMD='cd $HOME/ath && eval $(./vnc.sh) && '${@}
ID=$(docker build -q "$(pwd)/src/main/resources/ath-container")
docker run -it --rm \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v "$(pwd):/home/ath-user/ath" \
  -v "${HOME}/.m2:/home/ath-user/.m2" \
  -p 5942:5942 \
  -p 5005:5005 \
  -p 8000:8000 \
  -e SHARED_DOCKER_SERVICE=true \
  --user ath-user \
  ${ID} \
  bash -c "${CMD}"
