Inside toaster/

- run first:
    rm -f dependency-reduced-pom.xml
    rm -rf target
    mvn -q clean package

- then run:
    java -Dconfig="services.json" -jar target/windows-notifier-1.0-all.jar
