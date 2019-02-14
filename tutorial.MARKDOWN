# DOCKERYZOWANIE APLIKACJI SPRING BOOT   

Niniejszy dokument przedstawia:
* procedurę zbudowania aplikacji komponentów składających się na środowisko deweloperskie.
* procedurę tworzenia obrazów dockerowych zawierających komponenty środowiska (_Spring Boot, MySQL, adminer_)
* procedurę tworzenia plików wykorzystywanych w celu deploymentu komponentów aplikacji na klastrze kubernetesowym.

## Założenia

W niniejszym tutorialu korzystać będziemy z kodu udostępnionego na github'ie pod adresem [tym][1]

Zanim przystąpisz do realizacji procedur upewnij się, że posiadasz:
* zainstalowane JDK w wersji 8, odaz oprogramowanie Maven.
* zainstalowanego środowisko dockera (procedura instalacji dostępna na tej [stronie](https://docs.docker.com/toolbox/toolbox_install_windows/))
* zainstalowane środowisko Minikube, które tworzy lokalny klaster z jednym węzłem (procedure instalacji dostępna na tej [stronie](https://kubernetes.io/docs/tasks/tools/install-minikube/#install-minikube))
* zainstalowane narzędzie kubectl służące do komunikacji z klastrem poprzez jego API.


## Budowanie aplikacji Spring Boot

W pierwszej kolejności należy sklonować repozytorium dostępnym pod [tym linkiem][1]. Kiedy posiadamy je już na swoim dysku możemy przejrzeć jego kluczowe elementy.
Pierwszym z nich jest testowy endpoint, który wystawia zaimplementowane w nim testowe operacje na świat zewnętrzny. Aby uruchomić aplikację należy:
1. Zbudować aplikację.
~~~~~~
$ cd ${PATH_TO_REPO}
$ mvn clean install
~~~~~~
2. Uruchomić aplikację lokalnie za pomocą komendy poniżej.
~~~~
$ mvn spring-boot:run
~~~~

Aplikacja uruchomi się, ale podczas uruchamiania wyświetli błędy, które mówią o tym, że nie udało się połączyć z bazą danych.
Nic, dziwnego, skoro nie uruchomiliśmy jeszcze basy danych :). 

W pliku application.properties znajduje się jeden rekord. który odpowiada za ustalenie z jakim profilem aplikacja zostanie uruchomiona.
~~~~
spring.profiles.active=dev1
~~~~ 
Inne propertiesy znajdują się w plikach dla poszczególnych profili(application-dev1.properties, application-dev2.properties).
~~~~
spring.datasource.url=jdbc:mysql://db:3306/test?useSSL=false&autoReconnect=true&failOverReadOnly=false&maxReconnects=2&allowPublicKeyRetrieval=true
spring.datasource.username=ponaszki
spring.datasource.password=ponaszki
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.datasource.hikari.connection-timeout=20000
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.maximum-pool-size=12
spring.datasource.hikari.idle-timeout=300000
spring.datasource.hikari.max-lifetime=1200000

spring.jpa.properties.hibernate.dialect = org.hibernate.dialect.MySQLDialect
spring.jpa.properties.hibernate.format_sql = true

logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
~~~~

Ponadto w  katalogu resources znajduje się plik _logback-spring.xml_, Który jest używany do konfiduracji logowania w aplikacji.
Na uwagę zasługuje fakt, że jako enkoder do logowania wiadomości został użyty enkoder opracowny w projekcie logstash, który zapisuje wiadonoći w formacie JSON co ma wspomagać ładowanie danych z logów do aplikacji ELK.

# Budowanie obrazów aplikacji.
Zanim zaczniemy budowanie obrazów aplikacji należy zapoznać się z podstawowymi zasadami dotyczącymi tego budowania. 
Każdy plik dockerfile posiada Określoną strukturę:
* FROM obraz:tag- która wskazuje obraz bazowy dla naszego obrazu i inicjalizuje fazę budowania.
* instrukcje modyfikujące obraz w dowolnej sekwencji:
    - ADD argSrc argDest - instrukcja kopiująca pliki a lokalnego systemu plików do systemu plików obrazu. Dodatkowo jej _srcSrc_ może być adresem url albo archiwum tar, które zostanie rozpakowane w _argDest_
    - COPY argSrc argDest -  instrukcja kopiująca pliki a lokalnego systemu plikó do systemu plików obrazu.
    - VOLUME src:dest - montuje wolumin na obrazie w lokalizacji dest, który odwołuje się do lokalizacji src na hoscie.   
    - ENV  klucz watrość - ustawia zmiennąśrodowiskową obrazu. 
    - EXPOSE port - informuje dockera, że dany obraz nasłuchuje na danym porcie. Istnieje możliwość wyspecyfikowania również protokołu (port/protokół). Domyślnymo protokołem jest TCP. UWAGA! Nie jest to równoznaczne z wystawieniem portu na zewnątrz dockera.  
    - WORKDIR path - Ustawia "working directory dla instrukcji  RUN, CMD, ENTRYPOINT, COPY i ADD. 
    - RUN arg - wywołuje na obrazie komendę podaną jako argument.
* ENTRYPOINT [...,...,...] - komenda która wskazuje punkt startu kontenera. 
    
## Sprawdzenie poprawności działania docker
Na początku sprawdźmy czy oprogramowanie docker jest zainstalowane poprawnie:
~~~~
$ docker run hello-world
~~~~
Rezultatem wykonania powyższej komendy powinna być poniższa wiadomość wypisana na konsolę.
~~~~
Hello from Docker!
This message shows that your installation appears to be working correctly.

To generate this message, Docker took the following steps:
 1. The Docker client contacted the Docker daemon.
 2. The Docker daemon pulled the "hello-world" image from the Docker Hub.
    (amd64)
 3. The Docker daemon created a new container from that image which runs the
    executable that produces the output you are currently reading.
 4. The Docker daemon streamed that output to the Docker client, which sent it
    to your terminal.

To try something more ambitious, you can run an Ubuntu container with:
 $ docker run -it ubuntu bash

Share images, automate workflows, and more with a free Docker ID:
 https://hub.docker.com/

For more examples and ideas, visit:
 https://docs.docker.com/get-started/

~~~~
## Budowanie obrazu bazy danych MySQL
### Budowa za pomocą wiersza poleceń:
Budowa podstawowego obrazu zawierającego serwer MySQL wygląda następująco:
~~~~
$ docker run --name mysql -e MYSQL_ROOT_PASSWORD=root -d mysql:8.0.14
~~~~ 
Powyższa komenda sprawi, że docker będzie próbował uruchomić obraz o nazwie mysql:8.0.14. Z racji tego, że nie znajdze do wsród lokalnie dostępnych obrazów spróbuje go pobrać z reposytorium DockerHub. Po pobraniu obrazu nastąpi jego uruchomienie.
uruchomionej instancji obrazu zostanie nadana nazwa _db_. 
Opcja _-e -e MYSQL_ROOT_PASSWORD=root_ sprawi, że podczas uruchomienia obrazu zostanie w nim ustawiona zmienna środowiskowa o nazwie _MYSQL_ROOT_PASSWORD_ i wartości _root_. Następnie ta zmienna zostanie wykorzystana podczas uruchamiania serwera mysql w celu ustawienia hasła dla użytkownika root.
Opcji konfiguracji jest znacznie więcej. Aby dowiedzieć się o nich, należy przeczytać instrukcję znajdującą się pod tym [adresem][2] 

Istnieje możliwość wyświetlenia uruchomionych obrazów:
~~~~
$ docker ps
~~~~
Następnie jest możliwość zastopowania uruchomionego obrazu i usunięcia go:
~~~~
$ docker stop mysql
$ docker rm mysql
~~~~
Istnieje również możliwość usunięcia obrazu z lokalnego repozytorium:
~~~~
docker rmi mysql
~~~~

### Budowanie za pomocą Dockerfile.yml
Dockerfile.yml są to pliki opisujące, procedurę budowania obrazu. Plik służący do budowania obrazu mysql przedstawiono poniżej.
~~~~
FROM mysql:8.0.14
COPY ./mysqlInit/createTables.sql /docker-entrypoint-initdb.d/createTables.sql
COPY ./mysqlInit/custom-config.cnf /etc/mysql/conf.d/custom-config.cnf
~~~~
Pierwsza linia mówi o tym, że obrazem bazowym dla budowania naszego obrazu jest obraz _mysql:8.0.14_.
Kolejne służą do skopiowania danych które posłużą do zainicjowania bazy danych.
Aby zbudować obraz płyty należy wykonać polecenie:
~~~~
$ docker build  -t my-mysql -f Dockerfile-mysql .
~~~~
Opcja -t służy do przypisania nazwy/tadu do stworzonego obrazu.
Opcja -f wskazuje plik Dockerfile, który zostanie użyty do stworzenia obrasu
Kropka znajdująca się na końcu wskazuje w jakim kontekście ma się odbyć konstruowanie obrazu(wrzystkie relatywne ścieżki będą ustalane w relacji do lokalizacji wskazanej w tym miejscu).

## Budowanie obrazu aplikacji Spring Boot
Procedura budowania aplikacji Spring boot wygenerowała w katalogu ${PATH_TO_REPO}/target/ archiwum jar o nazwie _spring-docker-kubernetes-0.1.0.jar_.
Z racji tego, że proces tworzenia obrazu zawierającyego aplikację Spring Boot jest nieco bardziej skomplikowany zostanie on stworzony tylko za pomocą pliku Dockerfile.
### Budowanie za pomocą Dockerfile
Plik Dockerfile-web.yaml zawiera instrukcje wykonywane podczas tworzenia obrazu.
~~~~
FROM openjdk:8-jdk-alpine
VOLUME /tmp
ARG DEPENDENCY=target/dependency
ARG CONFIG=src
COPY ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY ${DEPENDENCY}/META-INF /app/META-INF
COPY ${DEPENDENCY}/BOOT-INF/classes /app
COPY ${CONFIG} /app/config
COPY wait_for.sh /usr/local/bin/
RUN chmod u+x /usr/local/bin/wait_for.sh

ENTRYPOINT ["wait_for.sh", "-host=db", "-port=3306", "--", "java -cp app:app/lib/*:app/config/* hello.Application"]
#ENTRYPOINT ["java","-cp","app:app/lib/*:app/config/*","hello.Application"]
~~~~

Pierwsza instrukcja oznacza, że obraz bazowy jaki zostanie użyty do stworzenia obrazu to _openjdk:8-jdk-alpine_.

Kolejna instrukcaj Tworzy w obrazie wolumin w lokalizacji /tmp. Jest ona użyta ponieważ uruchomiony serwer tomcal może w niektórych przypadkach chcieć odłożyć w tym miejscu jakieś pliki.

Instrukcja ARG jest odpowiednikiem deklaracji zmiennej w celu jej późniejszego użycia. 

Instrukcje 5-9 są kopiowaniem plików z lokalnego systemu plików do obrazu.

Instrukcja RUN wykonuje w obrazie instrukcje przekazaną jej jako argument (w tym przypadku chmod ..). 

Instrukcja EXPOSE mówi o tym, że dany kontener nasłuchuje na danym porcie. Ale nie sprawia, że jest on widoczny na zewnątrz sieci albo, że jest on dostępny poprzez hosta. 

Przed ostatnia linia ENTRYPOINT odpowiada za uruchomienie aplikacji spring boot. Uruchamiany jest skrypt _wait_for.sh_, który czeka aż zostanie uruchomiony obraz MySQL, a następnie wywołuje polecenie przekazane jako ostatni element tablicy.

Ostatnia linia ilustruje sposób uruchomienia aplikacji bezpośrednio, bez udziału skryptu.

Aby uruchomić obraz należy wykonać polecenie:
~~~~
$ docker build  -t spring -f Dockerfile-web .
~~~~

## Budowanie obrazu aplikacji adminer:
Obraz aplikacji adminer jest używany bez żadnych modywikacji więc nie zachodzi konieczność budowania dedykowanego obrazu.
W związku z tym, obraz ten można uruchomić z konsoli za pomocą polecenia
~~~~
$ docker run --name my-adminer --link my-mysql -p 8080:8080 -d adminer
~~~~
Powyższa komenda uruchamia obraz (run), nadaje mu nazwę (--name), łączy go z innym obrazem (--link), mapuje port hosta na port obrazu (port_hosta:port_obrazu) i uruchamia go w tle (-d == --detach).
Połączenie obrazu z innym obrazem jes robione po to, aby istniała możliwość odwołania się do obrazu mysql z obrasu adminer za pomozą nazwy obrazu mysql.
Jest to możliwe, ponieważ do obrazu aplikacji adminer jest dodawana odpowiednia zmienna środowiskowa.

## Uruchomienie obrazów
~~~~
$ docker run --name db -e MYSQL_ROOT_PASSWORD=root -e MYSQL_USER=ponaszki -e MYSQL_PASSWORD=ponaszki -e MYSQL_DATABASE=test my-mysql
~~~~
W tym momencie uruchomiliśmy obraz mysql o nazwie db. Jeżeli nadamy mu inną nazwę (np. moja-baza), należy zmodyfikować dwa pliki w aplikacji java:
* w skrypcie _wait_for.sh_ w komendzie ENTRYPOINT należy zmienić nazwę hosta na odpowiednią (-host=moja-baza).
* w pliku application-<actywny_profil>.properties w rekordzie _spring.datasource.url_ należy zmodyfikować nazwę hosta na moja-baza. Przykład poniżej:
~~~~
spring.datasource.url=jdbc:mysql://moja-baza:3306/test?useSSL=false&autoReconnect=true&failOverReadOnly=false&maxReconnects=2&allowPublicKeyRetrieval=true) 
~~~~
Następnie uruchamiamy pozostałe dwa dockery pamiętając o połączeniu ich z dockerem bazy (opcja _--link <nazwa-kontenera>_). Możemy także uruchomić je w tle używając opcji _-d_.
~~~~
$ docker create --name spring --link db -p 8090:8080 -d spring
$ docker run --name my-adminer --link db -p 8080:8080 -d adminer
~~~~ 
### Sprawdzenie działania
Aby uzyskać adres IP maszyny wirtualnej dockera wykonujemy polecenie:
~~~~
docker-machine ip
~~~~
1. Za pomocą przeglądarki wchodzimy na adtes http://<docker-machime-ip>:8080. Wpisujemy w nigo dane:
    * Rodzaj bazy : MYSQL
    * Serwer : db
    * Użytkownik: ponaszki
    * Hasło : ponaszki
    * Baza danych : test
 
Po zalogowaniu powinniśmy zobaczyć naszą bazę danych z utworzonymi w niej dwoma tabelami, test i test2
2. Następnie dowolnym klientem restowym wysłamy wiadomość typu POST w której body jest wpisana treść wiadomości na adres http://<docker-machime-ip>:8090/fileLog.
W odpowiedzi powinnicmy otrymac status 200 OK i treść odpowiedzi.
~~~~
$ curl -d "przykladowa wiadomosc" -H "Content-Type: application/x-www-form-urlencoded" -X POST http://<docker-machine-ip>:8090/fileLog
Message written to file log:przykladowa wiadomosc
~~~~
3. Następnie dowolnym klientem restowym wysłamy wiadomość typu POST w której body jest wpisana treść wiadomości na adres http://<docker-machime-ip>:8090/fileLog.
W odpowiedzi powinnicmy otrymac status 200 OK i treść odpowiedzi.
~~~~
$ curl -d "przykladowa wiadomosc" -H "Content-Type: application/x-www-form-urlencoded" -X POST http://<docker-machine-ip>:8090/consoleLog
Message written to console:przykladowa wiadomosc
~~~~
4. Jako ostatnią sprawdzimy komunikację aplikacji spring z bazą danych. 
~~~~
$ curl -d "przykladowa wiadomosc" -H "Content-Type: application/x-www-form-urlencoded" -X POST http://<docker-machine-ip>:8090/save
Saved entity: Test(id=1, message=przykladowa wiadomosc)
~~~~
W tym momencie możemy sprawdzić czy w bazie danych w tabeli test został zapisany odpowiedni rekord. Najprościej zrobić to za pomocą adminera, jak w punkcie 1.
~~~~
$ winpty docker exec -it spring cat logs/psLog.log
{"@version":"1","level":"INFO","logger_name":"PS_LOG","appName":"springBootApp","appVersion":"0.1.0","thread_name":"http-nio-8080-exec-1","message":"Przykladowa wiadomosc"}
$ docker logs spring | grep CONSOLE
2019-02-12 10:10:42,312 INFO  [http-nio-8080-exec-3] CONSOLE: przykladowa wiadomosc
~~~~
**Działa!!!!**

### Wymiana linków na sieci
Linkowanie obrazów jest przestarzałym mechanizmem. W nowszych wersjach logera zaleca się zamiast niego łączenie dockerów w sieci. Dockery znajdijące się w jednej sieci widzą siebie nawzajem pod nazwami takimi jak nazwy uruchomionych obrazów. Kluczowe komendy dotyczące sieci przedstawiono poniżej:
~~~~
$ docker network create my-net
$ docker network rm my-net
$ docker run  --name spring \
  --network my-net \
  --publish 8090:8080 \
  spring

$ docker network connect my-net spring
$ docker network disconnect my-net spring

~~~~
Pierwsza komend tworzy sieć o nazwie my-net. Druga komenda usuwa ją. Trzecia komenda uruchamia obraz i podłącza do do sieci my-net. Czwarta komenda podłącza do sieci o nazwie my-net uruchomiony wcześniej kontener o nazwie spring. Piąta komenda odłącza kontener spring od sieci my-net.

Następnie sprawdzamy działanie kontenerów poprzez wykonanie instrukcji z sekcji **sprawdzanie działania**. 

# Uruchamianie za pomocą docker-compose.yaml
Uruchamianie dużej ilości obrazów bezpośrednio z konsoli potrafi nastręczyć trudności. Przez konieczność wywoływania dużej liczby komend zarzączanie większą ilością kontenerów staje się trudno zarządzalne. Sposobem na uproszczenie deploymentu jest stworzenie pliku docker-compose.yaml. 
i używanie go do uruchamiania i usuwania całej struktury obrazów i sieci na raz.
Plik docker-compose używany do  deploymentu stworzonych w poprzednich krokach obrazów przedstawiono poniżej.
~~~~
version: '3'
services:
    db:
        image: my-mysql
        build:
            context: ./
            dockerfile: Dockerfile-mysql            
        command: --default-authentication-plugin=mysql_native_password
        environment:
            - MYSQL_DATABASE=test
            - MYSQL_USER=ponaszki
            - MYSQL_PASSWORD=ponaszki
            - MYSQL_ROOT_PASSWORD=root
        volumes:
            - ./database:/var/lib/mysql
        ports:
            - 3306:3306
        networks:
            - net
    adminer:
        image: adminer
        restart: always
        ports:
            - 8080:8080
        networks:
            - net
    spring:
        image: spring
        build:
            context: ./
            dockerfile: Dockerfile-web
        depends_on: 
            - db
        ports: 
            - 8090:8080
        networks:
          - net
networks:
    net:
        driver: bridge
~~~~
Każdy rekord z pliku docker-compose.yalm  może mieć swoje odwzorowanie w komendach wiersza poleceń.
 
Pierwszym serwisem opisanym w pliku jest servis o nazwie _db_.  Jego pierwsza sekcja, _image_ odpowiada za wskazanie nazwy obrazu na podstawie którego będzie budowany uruchomiony serwis. Druga sekcja,_build_, wskazuje kontekst i plik Dockerfile z którego może zostać przebudowany obraz użyty w danej usłudze.
Sekcja command sprawia, że do wywołania komendy ENTRYPOINT dodawany jest argument przekazany w tej sekcji. W tym przypadku ten argument sprawia, że do autoryzacji użytkowników stosowana jest starsza wersja pluginu.
Sekcja environment służy do ustawiania zmiennych środowiskowych w uruchamianym obrazie.
Sekcja voumes pozwala na dodawanie woluminów do obrazu. Woluminy zapewniają persystencję danych. Woluminy możemy dodawać w trybie read/write albo read only. W tym wypadku katalog hosta _.database_ jest montowany w lokalizajci kontenera _/var/lib/mysql_. Sprawia to że baza ta jest persystentna, ponieważ dane znajdujące się w bazie są znajdują się w tej lokalizacji.
Po zatrzymaniu obrazu nie są one jednak tracone, ale pozostają zapisane na hoscie. Przy ponownym uruchomieniu obrazu dane są dalej widoczne.
Sekcja ports odpowiada za mapowanie portów kontenera na porty hosta. (port-hosta:port-kontenera).
Sekcja networks mówi otym do których sieci będzie należał kontener.

Kolejny servis nosi nazwę _adminer_ i zawiera obraz adminera. Zawiera on jedną nową sekcję o nazwie restart. Mówi ona jaka ma być polityka restartu aplikacji w przypadku jej zatrzymania/awarii. Wartość domyślna to always.

Ostatni serwis nosi nazwę _spring_ i jak sama nazwa wskazuje będzie on zawierał kontener w którym będzie uruchomiona aplikacja spring boot.
Występuje w nik jedna nowa struktura o nazwie depends_on. Umożliwia ona ustalenie kolejności uruchamiania obrazów. 

**Uwaga!!!!** 

To jest tylko kolejność "wciśnięcia przycisku power". Jeżeli uruchamianie poszczególnych obrazów zajmuje dłuższy okres czasu, mimo wyznaczenia kolejności i tak mogą wystepowac wyścigi. Przykładem jest połączenie kontenera serwisu _db_ i _spring_. Oba te obrazy uruchamiają się koło 20-30 sekund.
Serwis _spring_ podczas swojego uruchamiania próbuje nawiązać połączenie z bazą danych która nie jest jeszcze dotowa i zaczyna rzucać wyjątkami.
W celu uniknięcia takich problemów uruchomienie aplikacjie spring odbywa się pośrednio poprzez wywołanie skryptu wait_for.sh z odpowiednimi argumentami (Patrz komenda ENTRYPOINT w pliku Dockerfile-web.yaml). 

## Sprawdzenie działania

Aby uruchomić obrazy należy użyć komendy znajdując się w katalogu z plikiem docker-compose.yaml:

~~~~
$ docker-compose up --build --detach
~~~~

Argumenty  build i detach są opcjonalne. Argument build powoduje ponowne zbudowanie obrazów potrzebnych do stworzenia serwisów (!!tylko obrazów!! należy pamiętać również o ponownym przebudowaniu kodu aplikacji spring za pomocą mavena). Argument --detach poworuje uruchomienie serwisów w tle.

Sprawdzenia poprawności deploymentu można dokonać według procedury przedstawionej wcześniej.

Aby zatrzymać obrazy należy użyć komendy

~~~~
docker-compose down
~~~~  

# Kubernetes
Jako klaster kubernetesowy będziemy używali klastra tworzonego przez minikube.
## Przygotowanie środowiska
1. Uruchamiamy minikube wywołując polecenie: `minikube --memory 4096`

2. Następnie wywołujemy polecenie `minikube cluster-info` w odpowiedzi otrzymując 
~~~~
kubectl cluster-info
Kubernetes master is running at https://192.168.99.101:8443
KubeDNS is running at https://192.168.99.101:8443/api/v1/namespaces/kube-system/services/kube-dns:dns/proxy

To further debug and diagnose cluster problems, use 'kubectl cluster-info dump'.
~~~~

3. Następnie powinniśmy skonfigurować naszego dockera aby używał rejestru umiejscowionego na maszynie na której zainstalowany jest klaster kubernetes. Robimy to za pomocą komendy `eval $(minikube docker-env)`
4. Należy ponownie zbudować wszystkie potrzebne obrazy. A następnie wypchnąć je do lokalnego rejestru znajdującego się na maszynie, na której jest klaster kubernetes. 
Zostaną one wtedy umieszczone w rejestrze dostępnym na maszynie wirtualnej minikube.
~~~~
$ docker build  -t localhost:5000/spring -f Dockerfile-web .
$ docker build  -t localhost:5000/my-mysql -f Dockerfile-mysql .
$ docker pull adminer
$ docker tag adminer localhost:5000/adminer

$ docker push localhost:5000/spring
$ docker push localhost:5000/my-mysql
$ docker push localhost:5000/adminer
~~~~
Po sprawdzeniu dostępnych w rejestrze obrazów komendą `docker images` powinny się wyświetlić dostępne obrazy. Lista opbarów powinna zawierać rekordy:
~~~~
REPOSITORY                                TAG                 IMAGE ID            CREATED             SIZE
localhost:5000/spring                     latest              f8a424115642        4 minutes ago       138MB
localhost:5000/adminer                    latest              e2d2c89239b2        4 minutes ago          84.1MB
localhost:5000/my-mysql                   latest              ee8d73bb8b55        4 minutes ago          477MB
~~~~

5. Następnie, przechodzimy do katalogu zawierającego plik deployment.yaml poleceniem `cd kubernetes/`.

~~~~
apiVersion: v1
kind: Namespace
metadata:
  name: web-namespace
  labels:
    apps: web
  annotations:
    type: demo

---

#apiVersion: v1
#kind: ResourceQuota
#metadata:
#  name: mem-cpu-quota
#  namespace: web-namespace
#spec:
#  hard:
#    requests.cpu: "1"
#    requests.memory: 2Gi
#    limits.cpu: "2"
#    limits.memory: 3Gi
#
#---

apiVersion: v1
kind: Service
metadata:
  labels:
    name: spring-service
  name: spring-service
  namespace: web-namespace
spec:
  ports:
    - nodePort: 31000
      port: 8080
      protocol: TCP
      targetPort: 8080
  selector:
    name: spring-service
  type: NodePort

---

apiVersion: v1
kind: Service
metadata:
  labels:
    name: adminer-service
  name: adminer-service
  namespace: web-namespace
spec:
  ports:
    - port: 80
      protocol: TCP
      nodePort: 31001
      targetPort: 8080
  selector:
    name: adminer
  type: NodePort

---

apiVersion: v1
kind: Service
metadata:
  labels:
    name: mysql-service
  name: db
  namespace: web-namespace
spec:
  ports:
    - port: 3306
      protocol: TCP
      targetPort: 3306
  selector:
    name: db
  type: ClusterIP

---

apiVersion: apps/v1
kind: Deployment
metadata:
  name: spring-boot
  namespace: web-namespace
spec:
  replicas: 1
  selector:
    matchLabels:
      name: spring-service
  template:
    metadata:
      labels:
        name: spring-service
    spec:
      containers:
      - image: localhost:5000/spring
        name: spring
        ports:
        - containerPort: 8080
#        resources:
#          limits:
#            memory: "1000m"
#            cpu: "1000m"
#          requests: 
#            memory: "800m"
#            cpu: "800m"

---

apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: mysql
  labels:
    app: mysql
    tier: backend
  namespace: web-namespace
spec:
  replicas: 1
  selector:
    matchLabels:
      name: db
  serviceName: mysql-service
  template:
    metadata:
      labels:
        name: db
    spec:
      containers:
      - image: localhost:5000/my-mysql
        name: db
        ports:
        - containerPort: 3306
        env:
        - name: MYSQL_ROOT_PASSWORD
          value: root
        - name: MYSQL_DATABASE
          value: test
        - name: MYSQL_USER
          value: ponaszki
        - name: MYSQL_PASSWORD
          value: ponaszki

---

apiVersion: apps/v1
kind: Deployment
metadata:
  name: adminer
  labels:
    app: adminer
    tier: frontend
  namespace: web-namespace
spec:
  replicas: 1
  selector:
    matchLabels:
      name: adminer
  template:
    metadata:
      labels:
        name: adminer
    spec:
      containers:
      - image: localhost:5000/adminer
        name: adminer
        ports:
        - containerPort: 80
~~~~
W powyższym pliku są tworzone 3 deploymenty i 3 serwisy, po jednym dla każdego deploymentu. 
Deklaracje poszczególnych obiektów oddzielone są trzema myślnikami. Typ danego obiektu opisuje wartość znajdująca się pod kluczem **kind:**. 
Deklaracje obiektu składa się z trzech sekcji. Pierwszą z nich są metadane (_metadata_). 
W niej znajduje się nazwa obiektu, etykiety jakimi oznaczamy obiekt, oraz ewentualnie namespace w jakim zostanie imieszczony.
Deklaracje składają się z 3 sekcji:
* metadata - sekcja zawierająca metadane obiektu, czyli między innymi jego nazwę, namespace w którym zostanie umieszczony obiekt o etykiety którymi jest opisywany;
* spec - pożądana specyfikacja obiektu, która różni się w zależności od typu obiektu. 
    - W przypadku obiektu Deployment zawiera ona
        * replicas - ilość instancji kontenera jakie zostaną uruchomione, 
        * selector - etykiety specyfikacji
        * template - szablon tworzonego kontenera który składa się z kilku części
            - metadata - metadane szablonu, w których znajdują się etykiety szablonu (muszą one być zgodne z etykietami specyfikacji) 
            - spec - specyfikacja szablonu, w której znajdują się opisy jednego lub sięcej kontenerów. 
            nazwy obrazów jakie zostaną stworzone do ich użycia nazwy kontenerów i specyfikacja ich portów
    - W przypadku obiektu Service zawiera ona:
        * ports - specyfikacja portów i protokołów jakie ma obsługiwać serwis
        * selector - selektor według którego inne obiekty będą włączane w skład serwisu. Selektory te są porównywane na przykład z etykietami w sekcji spec.metadata obiektów typu Deployment
        * type - typ ser4wisu. Istnieje likla typów serwisów. Jednym s nich jest serwis typu NodePort, który sprawia że serwis jest dostępny na zewnątrz hosta na którym jest umieszczony. 
        Kolejnym jest serwis typu ClusterIP który sprawia, że serwis jest widoczny tylko w ramach klastra na którym jest uruchomiony pod wewnętrznym IP. Ostatnim jest serwis typu LoadBalancer. Zastosowanie tego typu powoduje przydzielenie temu serwisowi zewnętrznego adresu IP. 
* status - sekcja opisująca obecny status obiektu, uzupełniana i zarządzana przez klaster kubernetesowy            

Opis portów w sekcji spec może posiadać kilka elementów:
* nodePort - port hosta na którym wystawiony jest serwis
* port - port na którym seris nasłuchuje wewnątrz klastra
* targetPort - port w kapsule (pod) na który wiadomość jest przesyłana
* protocol - obsługiwany protokół

Dodatkowym typem obiektu jest RasourceQuota,  która pozwala na przypisanie określonej ilości zasobów do określonj przestrzeni nazw (namespace). Sprawia to, że zużycie zasobów przez obiekty uruchomione w tej przestrzeni nazw nie może przekroczyć zadeklarowanej w niej ilości zasobów.
Zarówno obiekty przedstawione powyżej zawierają więcej właściwości niż przedstawione powyżej. Pełny ich opis znajdije się pod [adresem][3]

6. Uruchomienie wszystkich obiektów zadeklarowanych w pliku deployment.yaml odbywa się za pomocą komendy `kubectl create -f deployment.yaml`
7. Można przetestować aplikacje:
    * adminer - dostając się do niego przez przeglądarkę wpisując adres _192.168.99.101:31001_
    * spring - wykonując następującą komendę:
    ~~~~
    $ curl -d "przykladowa wiadomosc" -H "Content-Type: application/x-www-form-urlencoded" -X POST http://<docker-machine-ip>:8090/fileLog
    ~~~~

8. Wyłączenie wszystkich obiektów zadeklarowanych w pliku _deployment.yaml_ za pomocą polecenia `kubectl delete -f deployment.yaml`


[1]: https://github.com/pikor91/spring-docker-kubernetes.git
[2]: https://hub.docker.com/_/mysql
[3]: https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.13/
