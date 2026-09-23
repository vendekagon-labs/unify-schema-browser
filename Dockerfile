FROM clojure:temurin-21-tools-deps
RUN apt-get update && apt-get install --yes graphviz && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY deps.edn /app/
RUN clojure -P -M:service
COPY src/ /app/src/
COPY resources/ /app/resources/
EXPOSE 8999
ENTRYPOINT ["clojure", "-M:service"]
