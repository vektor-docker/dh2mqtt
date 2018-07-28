FROM javister-docker-docker.bintray.io/javister/javister-docker-openjdk:1.0.java8

COPY src/main/docker/ /
RUN chmod --recursive +x /etc/my_init.d/*.sh /etc/service /usr/local/bin

ENV SERVER_URL=tcp://example.com:1883 \
    SERVER_USER=user \
    SERVER_PASSWORD=password \
    CLIENT_ID=example

COPY target/dh2mqtt.jar /app/

