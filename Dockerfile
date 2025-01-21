# Use an official Scala image as the base image
FROM sbtscala/scala-sbt:eclipse-temurin-23.0.1_11_1.10.7_3.6.3 as scalabuild
WORKDIR /app/scala
COPY backend/ .
RUN sbt compile

FROM node:14 as nodebuild
WORKDIR /app/elm
RUN npm install -g elm
COPY ui/ .
RUN elm make src/Main.elm --output=elm.js


FROM eclipse-temurin:21.3.0_0-jdk as app
COPY --from=scalabuild target/scala-3.3.4/medicate_3-0.1.0.jar /app/scala/medicate.jar
COPY --from=nodebuild /app/elm /app/elm

WORKDIR /app
COPY start.sh . 
CMD ["sh", "-c", "start.sh"]



EXPOSE 8080
EXPOSE 8000
