# Customer API demo using Spring Cloud Kubernetes

This is the full source code for the workshop "Spring Loves PCF". If you didn't complete the coding side of the workshop 
you can simply clone and package the code in this repo as follows

```bash
$ git clone https://github.com/papicella/customer-api
$ cd customer-api
$ ./mvnw -DskipTests package
```

## Deploying to CF

Use a manifest.yml as follows for the first deploy to PCF then "cf push"

```yaml
applications:
  - name: customer-api
    memory: 1G
    instances: 1
    random-route: true
    path: ./target/customer-api-0.0.1-SNAPSHOT.jar
```

## Deploying to a Kubernetes Cluster

#### What is Required 

* kubectl CLI
* mysql CLI
* helm package manager installed

#### STEP 1

Connect to the cluster as follows:

```
$ kubectl config use-context apples
Switched to context "apples".

$ kubectl config set-context --current --namespace=piv-workshop-1
Context "apples" modified.
```

Using helm create a MySQL instance as follows:

```
$ helm install --name pas-mysql stable/mysql
```

Verify MySQL up and running after about 1 - 2 minutes:

```
$ kubectl get all --namespace=piv-workshop-1
NAME                             READY   STATUS    RESTARTS   AGE
pod/pas-mysql-7f85d6985b-2xrgz   1/1     Running   0          2m2s

NAME                TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)    AGE
service/pas-mysql   ClusterIP   10.100.200.49   <none>        3306/TCP   2m3s

NAME                        READY   UP-TO-DATE   AVAILABLE   AGE
deployment.apps/pas-mysql   1/1     1            1           2m3s

NAME                                   DESIRED   CURRENT   READY   AGE
replicaset.apps/pas-mysql-7f85d6985b   1         1         1       2m4s

$ helm ls
NAME        	REVISION	UPDATED                 	STATUS  	CHART          	APP VERSION	NAMESPACE
pas-mysql   	1       	Fri Aug 16 14:16:32 2019	DEPLOYED	mysql-0.19.0   	5.7.14     	piv-workshop-1
```

#### STEP 2

Let's expose a service endpoint to access the MySQL instance. Please make sure you use your correct namespace and mysql name you used above:

```
$ kubectl expose service -n piv-workshop-1 pas-mysql --type LoadBalancer --port 3306 --target-port 3306 --name pas-mysql-public
service/pas-mysql-public exposed

$ kubectl get all
NAME                             READY   STATUS    RESTARTS   AGE
pod/pas-mysql-7f85d6985b-2xrgz   1/1     Running   0          4m45s

NAME                       TYPE           CLUSTER-IP      EXTERNAL-IP                 PORT(S)          AGE
service/pas-mysql          ClusterIP      10.100.200.49   <none>                      3306/TCP         4m46s
service/pas-mysql-public   LoadBalancer   10.100.200.16   10.195.75.155,100.64.96.7   3306:30679/TCP   8s

NAME                        READY   UP-TO-DATE   AVAILABLE   AGE
deployment.apps/pas-mysql   1/1     1            1           4m46s

NAME                                   DESIRED   CURRENT   READY   AGE
replicaset.apps/pas-mysql-7f85d6985b   1         1         1       4m46s
```

#### STEP 3

Create a script called "connect-to-mysql.sh" with contents as follows. It is assumed you have "mysql" command line CLI installed and if you do you can use this script as is to connect to the MySQL instance:

```
export MYSQL_HOST=$(kubectl get svc pas-mysql-public -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
export MYSQL_ROOT_PASSWORD=$(kubectl get secret --namespace pas pas-mysql -o jsonpath="{.data.mysql-root-password}" | base64 --decode; echo)

echo ""
echo "mysql -h $MYSQL_HOST -P3306 -u root -p$MYSQL_ROOT_PASSWORD"
echo ""

mysql -h $MYSQL_HOST -P3306 -u root -p$MYSQL_ROOT_PASSWORD
```

Run the script to connect to the MySQL instance. Even if you don't have mysql command line client the connect details will be displayed and you can use workbench or some other utility tool to connect to the MySQL database instance:

```
$ ./connect-to-mysql.sh

mysql -h 10.195.75.155 -P3306 -u root -pnANli13lIG

mysql: [Warning] Using a password on the command line interface can be insecure.
Welcome to the MySQL monitor.  Commands end with ; or \g.
Your MySQL connection id is 105
Server version: 5.7.14 MySQL Community Server (GPL)

Copyright (c) 2000, 2018, Oracle and/or its affiliates. All rights reserved.

Oracle is a registered trademark of Oracle Corporation and/or its
affiliates. Other names may be trademarks of their respective
owners.

Type 'help;' or '\h' for help. Type '\c' to clear the current input statement.

mysql> show databases;
+--------------------+
| Database           |
+--------------------+
| information_schema |
| mysql              |
| performance_schema |
| sys                |
+--------------------+
4 rows in set (0.24 sec)

mysql>
```

Create a new database, user and grant privileges as shown below:

```
mysql> create database apples;
Query OK, 1 row affected (0.28 sec)

mysql> CREATE USER 'pas'@'%' IDENTIFIED BY 'pas';
Query OK, 0 rows affected (0.28 sec)

mysql> GRANT ALL PRIVILEGES ON apples.* TO 'pas'@'%' WITH GRANT OPTION;
Query OK, 0 rows affected (0.26 sec)

mysql> exit
```

Get the IP address used as the exposed service using a command "kubectl get svc pas-mysql-public -o jsonpath='{.status.loadBalancer.ingress[0].ip}'":

```
$ kubectl get svc pas-mysql-public -o jsonpath='{.status.loadBalancer.ingress[0].ip}'
10.195.75.155
```

Connect to the "apples" database using the new PAS user as shown below. Ensure you use the IP address which was displayed in the previous command and then change to the database "apples":

```
$ mysql -h 10.195.75.155 -P3306 -u pas -ppas
mysql: [Warning] Using a password on the command line interface can be insecure.
Welcome to the MySQL monitor.  Commands end with ; or \g.
Your MySQL connection id is 209
Server version: 5.7.14 MySQL Community Server (GPL)

Copyright (c) 2000, 2018, Oracle and/or its affiliates. All rights reserved.

Oracle is a registered trademark of Oracle Corporation and/or its
affiliates. Other names may be trademarks of their respective
owners.

Type 'help;' or '\h' for help. Type '\c' to clear the current input statement.

mysql> use apples;
Database changed
```
So what we have now is the following connection details to connect to our database BUT do we really want to hard code the PORT or even the IP address here? What if it changes?

* username: pas
* password: pas
* IP: 10.195.75.155
* Database Name: apples
* Port: 3306

Kubernetes has some really cool features which makes light work of this.

#### STEP 4

First lets take a look at our services which we have 2. One internal called "pas-mysql" and an external service called "pas-mysql-public" which we used to connect to mysql CLI through. It's the "pas-mysql" service our spring boot application will use:

```
$ kubectl get svc
NAME               TYPE           CLUSTER-IP      EXTERNAL-IP                 PORT(S)          AGE
pas-mysql          ClusterIP      10.100.200.49   <none>                      3306/TCP         30m
pas-mysql-public   LoadBalancer   10.100.200.16   10.195.75.155,100.64.96.7   3306:30679/TCP   25m
```

#### STEP 5

So lets create a ConfigMap which has the internal service name we want to use and the database name using "kubectl create configmap mysql-config --from-literal=mysql.service.name=pas-mysql --from-literal=mysql.db.name=apples":

```
$ kubectl create configmap mysql-config --from-literal=mysql.service.name=pas-mysql --from-literal=mysql.db.name=apples
configmap/mysql-config created
$ kubectl get cm mysql-config -o json
{
    "apiVersion": "v1",
    "data": {
        "mysql.db.name": "apples",
        "mysql.service.name": "pas-mysql"
    },
    "kind": "ConfigMap",
    "metadata": {
        "creationTimestamp": "2019-08-16T04:54:46Z",
        "name": "mysql-config",
        "namespace": "piv-workshop-1",
        "resourceVersion": "13960716",
        "selfLink": "/api/v1/namespaces/piv-workshop-1/configmaps/mysql-config",
        "uid": "f86ffa77-bfe1-11e9-a1c4-005056aca066"
    }
}
```

#### STEP 6

Storing the username and password in a ConfigMap IS NOT a good idea. So with kubernetes we can use a "Secret" which we do using a command as follows "kubectl create secret generic db-security --from-literal=mysql_username=pas --from-literal=mysql_password=pas":

```
$ kubectl create secret generic db-security --from-literal=mysql_username=pas --from-literal=mysql_password=pas
secret/db-security created
$ kubectl get secret db-security -o json
{
    "apiVersion": "v1",
    "data": {
        "mysql_password": "cGFz",
        "mysql_username": "cGFz"
    },
    "kind": "Secret",
    "metadata": {
        "creationTimestamp": "2019-08-16T04:59:58Z",
        "name": "db-security",
        "namespace": "piv-workshop-1",
        "resourceVersion": "13961243",
        "selfLink": "/api/v1/namespaces/piv-workshop-1/secrets/db-security",
        "uid": "b24267f8-bfe2-11e9-9570-005056ac503a"
    },
    "type": "Opaque"
}
```

#### STEP 7

So our "application.properties" for our Customer API is going to now look like this. We will discuss further how this is actually translated and how / where do we define those $ENV variables. So for now create a file called "application.properties" with the exact same content as below:

```
spring.jpa.hibernate.ddl-auto=create
spring.jpa.hibernate.use-new-id-generator-mappings=false
spring.jpa.show-sql=true
spring.jpa.database-platform=org.hibernate.dialect.MySQL55Dialect
spring.datasource.username=${MYSQL_DB_USERNAME}
spring.datasource.password=${MYSQL_DB_PASSWORD}
spring.datasource.url=jdbc:mysql://${${MYSQL_SERVICE}.service.host}:${${MYSQL_SERVICE}.service.port}/${MYSQL_DB_NAME}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
```

#### STEP 8

As you may have guessed we will load that properties file into a ConfigMap using a command "kubectl create configmap app-config --from-file=application.properties":

```
$ kubectl create configmap app-config --from-file=application.properties
configmap/app-config created
papicella@papicella:~/pivotal/PCF/APJ/Workshops-SIMONA/kubernetes$ kubectl get cm app-config -o json
{
    "apiVersion": "v1",
    "data": {
        "application.properties": "spring.jpa.hibernate.ddl-auto=create\nspring.jpa.hibernate.use-new-id-generator-mappings=false\nspring.jpa.show-sql=true\nspring.jpa.database-platform=org.hibernate.dialect.MySQL55Dialect\nspring.datasource.username=${MYSQL_DB_USERNAME}\nspring.datasource.password=${MYSQL_DB_PASSWORD}\nspring.datasource.url=jdbc:mysql://${${MYSQL_SERVICE}.service.host}:${${MYSQL_SERVICE}.service.port}/${MYSQL_DB_NAME}\nspring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver\n"
    },
    "kind": "ConfigMap",
    "metadata": {
        "creationTimestamp": "2019-08-16T05:05:48Z",
        "name": "app-config",
        "namespace": "piv-workshop-1",
        "resourceVersion": "13961848",
        "selfLink": "/api/v1/namespaces/piv-workshop-1/configmaps/app-config",
        "uid": "82dcd639-bfe3-11e9-a389-005056aceaca"
    }
}
```

What is Spring Cloud Kubernetes?

The Spring Cloud Kubernetes plug-in implements the integration between Kubernetes and Spring Boot. It provides access to the configuration data of a ConfigMap using the Kubernetes API. It make so easy to integrate Kubernetes ConfigMap directly with the Spring Boot externalized configuration mechanism, so that Kubernetes ConfigMaps behave as an alternative property source for Spring Boot configuration:

If you have followed this step by step using the same ConfigMap and Secret names then you can use an already loaded image as follows on DockerHub

* pasapples/customer-api-mysql

![alt tag](https://i.ibb.co/k4FQzYQ/dockerhub-customerapi.png)

#### STEP 9

Now create a file called "customer-api-deployment-MYSQL.yaml" with contents as follows. As you can see we are injecting ENV values for our "application.properties" from the relevant ConfigMap and Secret using it's names:

``` yaml
apiVersion: extensions/v1beta1
kind: Deployment
metadata:
  name: customer-api-mysql
spec:
  replicas: 1
  template:
    metadata:
      labels:
        app: customer-api-mysql
    spec:
      containers:
        - name: customer-api-mysql
          image: pasapples/customer-api-mysql
          ports:
            - containerPort: 8080
          env:
          - name: MYSQL_SERVICE
            valueFrom:
              configMapKeyRef:
                name: mysql-config
                key: mysql.service.name
          - name: MYSQL_DB_NAME
            valueFrom:
              configMapKeyRef:
                name: mysql-config
                key: mysql.db.name
          - name: MYSQL_DB_USERNAME
            valueFrom:
              secretKeyRef:
                name: db-security
                key: mysql_username
          - name: MYSQL_DB_PASSWORD
            valueFrom:
              secretKeyRef:
                name: db-security
                key: mysql_password

---
apiVersion: v1
kind: Service
metadata:
  name: customer-api-mysql-service
  labels:
    name: customer-api-mysql-service
spec:
  ports:
    - port: 80
      targetPort: 8080
      protocol: TCP
  selector:
    app: customer-api-mysql
  type: LoadBalancer
```

#### STEP 10

Now lets deploy the new Customer API which will connect to our MySQL instance through the clever use of Spring Cloud and Kubernetes ConfigMap and Secret files. To do that run a command as follows "kubectl create -f customer-api-deployment-MYSQL.yaml":

```
$ kubectl create -f customer-api-deployment-MYSQL.yaml
deployment.extensions/customer-api-mysql created
service/customer-api-mysql-service created

$ kubectl get all
NAME                                      READY   STATUS    RESTARTS   AGE
pod/customer-api-mysql-787fb7d45f-rsrwt   1/1     Running   0          27s
pod/pas-mysql-7f85d6985b-2xrgz            1/1     Running   0          59m

NAME                                 TYPE           CLUSTER-IP      EXTERNAL-IP                 PORT(S)          AGE
service/customer-api-mysql-service   LoadBalancer   10.100.200.21   10.195.75.156,100.64.96.7   80:30893/TCP     27s
service/pas-mysql                    ClusterIP      10.100.200.49   <none>                      3306/TCP         59m
service/pas-mysql-public             LoadBalancer   10.100.200.16   10.195.75.155,100.64.96.7   3306:30679/TCP   54m

NAME                                 READY   UP-TO-DATE   AVAILABLE   AGE
deployment.apps/customer-api-mysql   1/1     1            1           29s
deployment.apps/pas-mysql            1/1     1            1           59m

NAME                                            DESIRED   CURRENT   READY   AGE
replicaset.apps/customer-api-mysql-787fb7d45f   1         1         1       29s
replicaset.apps/pas-mysql-7f85d6985b            1         1         1       59m
```

#### STEP 11

Now lets get the external LB IP using a command as follows:

```
$ kubectl get svc customer-api-mysql-service -o jsonpath='{.status.loadBalancer.ingress[0].ip}'
10.195.75.156
```

#### STEP 12

Using the IP address above invoke a HTTP GET call as follows:

```
$ http http://10.195.75.156/customers/1
HTTP/1.1 200
Content-Type: application/hal+json;charset=UTF-8
Date: Fri, 16 Aug 2019 05:19:35 GMT
Transfer-Encoding: chunked

{
    "_links": {
        "customer": {
            "href": "http://10.195.75.156/customers/1"
        },
        "self": {
            "href": "http://10.195.75.156/customers/1"
        }
    },
    "name": "pas",
    "status": "active"
}
```

#### STEP 13
 
You can also invoke the swagger UI html page using the same IP LB address with an url as follows

* http://10.195.75.156/swagger-ui.html

![alt tag](https://i.ibb.co/3S2fKjz/swagger-ui-k8s-workshop.png)

<hr />

Pas Apicella [papicella at pivotal.io] is an Advisory Platform Architect at Pivotal Australia