# Customer API demo using Spring Cloud Kubernetes

This is the full source code for the workshop "Spring Loves PCF". If you didn't complete the coding side of the workshop 
you can simply clone and package the code in this repo as follows

```bash
$ git clone https://github.com/papicella/customer-api
$ cd customer-api
$ ./mvnw -DskipTests package
```

## Deploying to CF

Use a manifest.yml as follows for the first deploy to PCF

```yaml
applications:
  - name: customer-api
    memory: 1G
    instances: 1
    random-route: true
    path: ./target/customer-api-0.0.1-SNAPSHOT.jar
```

<hr />
Pas Apicella [papicella at pivotal.io] is an Advisory Platform Architect at Pivotal Australia