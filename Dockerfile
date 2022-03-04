FROM registry.access.redhat.com/ubi8/openjdk-11

WORKDIR /app

ARG JAR_FILE
# Copy the source tree to the build container
COPY ${JAR_FILE} ./service.jar

USER root
RUN mkdir ./data
RUN chmod g+rwx ./data

ARG EPRICER_SERVICEVER=0.0.0
ENV EPRICER_SERVICEVER=$EPRICER_SERVICEVER

ENTRYPOINT ["java", "-jar", "service.jar"]
