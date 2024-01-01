FROM clojure:lein
COPY src/ /alzabo/src/
COPY resources/ /alzabo/resources/
COPY project.clj /alzabo/
COPY test/resources/pretense/resources/schema/ /schema
WORKDIR /alzabo
RUN lein deps

ENV DATOMIC_URI="datomic:dev://host.docker.internal:4334/unify-example"
ENTRYPOINT lein run "resources/unify-db-config.edn" server

