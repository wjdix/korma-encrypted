FROM clojure
RUN mkdir -p /code
WORKDIR /code
COPY project.clj /code/
RUN lein deps
COPY . /code
