#/bin/bash

cd ../1.environment

echo "Iniciando entorno"
docker compose up -d
sleep 30

echo "Creando la base de datos db (si no existe)"
docker exec mysql bash -c "mysql --user=root --password=password -e 'CREATE DATABASE IF NOT EXISTS db;'"

echo "Creando la tabla transactions"
docker cp ../8.tarea/sql/transactions.sql mysql:/
docker exec mysql bash -c "mysql --user=root --password=password --database=db < /transactions.sql"

echo "Instalando conectores..."
docker compose exec connect confluent-hub install --no-prompt confluentinc/kafka-connect-datagen:latest
docker compose exec connect confluent-hub install --no-prompt confluentinc/kafka-connect-jdbc:latest

echo "Copiando drivers MySQL..."
docker cp ./mysql/mysql-connector-java-5.1.45.jar connect:/usr/share/confluent-hub-components/confluentinc-kafka-connect-jdbc/lib/mysql-connector-java-5.1.45.jar

echo "Copiando schemas AVRO..."
docker cp ../8.tarea/datagen/sensor-telemetry.avsc connect:/home/appuser/
docker cp ../8.tarea/datagen/transactions.avsc connect:/home/appuser/

echo "Reiniciando contenedor connect..."
docker compose restart connect
echo "Esperando reinicio contenedor connect"
sleep 30

echo "OK"
