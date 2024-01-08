FROM clojure:lein
RUN apt-get update && apt-get install --yes graphviz
COPY src/ /alzabo/src/
COPY resources/ /alzabo/resources/
COPY project.clj /alzabo/
COPY test/resources/pretense/resources/schema/ /schema
WORKDIR /alzabo
RUN lein deps

ENV DATOMIC_URI="datomic:dev://host.docker.internal:4334/unify-example"
ENTRYPOINT lein run

