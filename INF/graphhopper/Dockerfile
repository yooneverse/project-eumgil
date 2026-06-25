ARG GRAPHHOPPER_VERSION=11.0

FROM eclipse-temurin:21-jdk

ARG GRAPHHOPPER_VERSION

WORKDIR /opt/graphhopper

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates osmium-tool python3 python3-psycopg2 \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL -o graphhopper-web.jar "https://repo1.maven.org/maven2/com/graphhopper/graphhopper-web/${GRAPHHOPPER_VERSION}/graphhopper-web-${GRAPHHOPPER_VERSION}.jar"
RUN curl -fsSL -o postgresql.jar "https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.5/postgresql-42.7.5.jar"

COPY INF/graphhopper/config-local.yml /opt/graphhopper/config-local.yml
COPY INF/graphhopper/config-runtime.yml /opt/graphhopper/config-runtime.yml
COPY INF/graphhopper/config-build.yml /opt/graphhopper/config-build.yml
COPY INF/graphhopper/custom_models /opt/graphhopper/custom_models
COPY scripts/docker/entrypoints/graphhopper.sh /usr/local/bin/graphhopper-entrypoint.sh
COPY scripts/docker/entrypoints/graphhopper-build.sh /usr/local/bin/graphhopper-build-entrypoint.sh
COPY scripts/graphhopper/export_postgis_to_osm.py /usr/local/bin/export-postgis-to-osm.py
COPY scripts/graphhopper/smoke_postgis_export.py /usr/local/bin/smoke-postgis-export.py
COPY scripts/graphhopper/smoke_graphhopper_profiles.py /usr/local/bin/smoke-graphhopper-profiles.py
COPY scripts/graphhopper/smoke_walk_access_hot_patch.py /usr/local/bin/smoke-walk-access-hot-patch.py
COPY scripts/db/load_accessibility_features.py /usr/local/bin/load-accessibility-features.py
COPY INF/graphhopper/plugin/src /opt/graphhopper/plugin/src

RUN mkdir -p /opt/graphhopper/plugin/classes \
    && javac -cp /opt/graphhopper/graphhopper-web.jar:/opt/graphhopper/postgresql.jar \
      -d /opt/graphhopper/plugin/classes \
      $(find /opt/graphhopper/plugin/src -name '*.java' | sort) \
    && jar --create --file /opt/graphhopper/plugin/ieum-graphhopper-plugin.jar \
      -C /opt/graphhopper/plugin/classes .

RUN chmod +x /usr/local/bin/graphhopper-entrypoint.sh \
    && chmod +x /usr/local/bin/graphhopper-build-entrypoint.sh \
    && chmod +x /usr/local/bin/export-postgis-to-osm.py \
    && chmod +x /usr/local/bin/smoke-postgis-export.py \
    && chmod +x /usr/local/bin/smoke-graphhopper-profiles.py \
    && chmod +x /usr/local/bin/smoke-walk-access-hot-patch.py \
    && chmod +x /usr/local/bin/load-accessibility-features.py

EXPOSE 8989 8990

ENTRYPOINT ["graphhopper-entrypoint.sh"]
