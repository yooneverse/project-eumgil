FROM jenkins/jenkins:lts-jdk21

USER root
ARG DOCKER_GID=999

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl gnupg python3 \
    && install -m 0755 -d /etc/apt/keyrings \
    && curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc \
    && chmod a+r /etc/apt/keyrings/docker.asc \
    && . /etc/os-release \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian ${VERSION_CODENAME} stable" > /etc/apt/sources.list.d/docker.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends docker-ce-cli docker-compose-plugin docker-buildx-plugin openssh-client \
    && rm -rf /var/lib/apt/lists/* \
    && group_name="$(getent group ${DOCKER_GID} | cut -d: -f1 || true)" \
    && if [ -z "$group_name" ]; then groupadd -g "${DOCKER_GID}" dockerhost && group_name=dockerhost; fi \
    && usermod -aG "$group_name" jenkins

USER jenkins
ENV JENKINS_UC=https://updates.jenkins.io/stable
ENV JENKINS_UC_DOWNLOAD=https://updates.jenkins.io/download

COPY --chown=jenkins:jenkins s1/plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

COPY --chown=jenkins:jenkins pipelines /usr/share/jenkins/ref/pipelines
COPY --chown=jenkins:jenkins s1/init.groovy.d/*.groovy /usr/share/jenkins/ref/init.groovy.d/
