FROM clojure:lein
COPY src/ /alzabo/src/
COPY resources/ /alzabo/resources/
COPY project.clj /alzabo/
COPY test/resources/pretense/resources/schema/ /schema
ENV UNIFY_SCHEMA_DIRECTORY="/schema/"
WORKDIR /alzabo
RUN lein deps
ENTRYPOINT lein run "resources/candel-config.edn" server

