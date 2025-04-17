
docker rmi -f jemini-app
mvn package -DskipTests
docker compose up