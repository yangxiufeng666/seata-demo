# 版本

 1. seata：1.3.0
 2. spring-boot：2.2.9.RELEASE
 3. spring-cloud：Hoxton.SR6
 4. spring-cloud-alibaba：2.2.1.RELEASE
 5. Nacos：1.3
 
 # seata配置
 seata server的配置文件如下：
 
![在这里插入图片描述](https://img-blog.csdnimg.cn/20200804175859586.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3cxMDU0OTkzNTQ0,size_16,color_FFFFFF,t_70)
分别设置file.conf de mode为db，registry.config为nacos， 其他不变， 之后启动。

然后配置客户端：

```
seata:
  application-id: ${spring.application.name}
  tx-service-group: ${spring.application.name}-group
  registry:
    nacos:
      namespace: ff2a1399-d3a6-48f5-80a9-18d67b35e383
      server-addr: 1.1.11.15:8848
    type: nacos
  service:
    vgroup-mapping:
      seata-product-group: default
```
启动客户端， 会出现如下的错误：

```
no available service 'default' found, please make sure registry config correct
```
网上很多都会提到default大小写之类的，依然没有解决问题，通过跟踪代码可知在NettyClientChannelManager的reconnect方法中getAvailServerList获取不到服务，再跟踪到NacosRegistryServiceImpl的lookup中，发现最终调用NamingService去获取nacos中的实例

```
@Override
    public List<InetSocketAddress> lookup(String key) throws Exception {
        String clusterName = getServiceGroup(key);
        if (null == clusterName) {
            return null;
        }
        if (!LISTENER_SERVICE_MAP.containsKey(clusterName)) {
            synchronized (LOCK_OBJ) {
                if (!LISTENER_SERVICE_MAP.containsKey(clusterName)) {
                    List<String> clusters = new ArrayList<>();
                    clusters.add(clusterName);
                    List<Instance> firstAllInstances = getNamingInstance().getAllInstances(PRO_SERVER_ADDR_KEY, clusters);
                    if (null != firstAllInstances) {
                        List<InetSocketAddress> newAddressList = firstAllInstances.stream()
                                .filter(instance -> instance.isEnabled() && instance.isHealthy())
                                .map(instance -> new InetSocketAddress(instance.getIp(), instance.getPort()))
                                .collect(Collectors.toList());
                        CLUSTER_ADDRESS_MAP.put(clusterName, newAddressList);
                    }
                    subscribe(clusterName, event -> {
                        List<Instance> instances = ((NamingEvent)event).getInstances();
                        if (null == instances && null != CLUSTER_ADDRESS_MAP.get(clusterName)) {
                            CLUSTER_ADDRESS_MAP.remove(clusterName);
                        } else if (!CollectionUtils.isEmpty(instances)) {
                            List<InetSocketAddress> newAddressList = instances.stream()
                                    .filter(instance -> instance.isEnabled() && instance.isHealthy())
                                    .map(instance -> new InetSocketAddress(instance.getIp(), instance.getPort()))
                                    .collect(Collectors.toList());
                            CLUSTER_ADDRESS_MAP.put(clusterName, newAddressList);
                        }
                    });
                }
            }
        }
        return CLUSTER_ADDRESS_MAP.get(clusterName);
    }
```
接口中的key就是我们客户端配置的seata-product-group，getServiceGroup得到clusterName=default，这里都没有问题， 和我们的配置没有出入，再深入发现问题出在这里：

![在这里插入图片描述](https://img-blog.csdnimg.cn/20200804180938131.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3cxMDU0OTkzNTQ0,size_16,color_FFFFFF,t_70)

List<Instance> firstAllInstances = getNamingInstance().getAllInstances(PRO_SERVER_ADDR_KEY, clusters);拿不到实例，为啥呢，因为PRO_SERVER_ADDR_KEY = "serverAddr";  但是我们注册的服务根本没有叫serverAddr，所以就会报题目说的错误。

回头再看seata的registry.conf，

```bash
registry {
  # file 、nacos 、eureka、redis、zk、consul、etcd3、sofa
  type = "nacos"

  nacos {
    application = "seata-server"
    serverAddr = ""
    group = "SEATA_GROUP"
    namespace = ""
    cluster = "default"
    username = ""
    password = ""
  }
  eureka {
    serviceUrl = "http://localhost:8761/eureka"
    application = "default"
    weight = "1"
  }
  redis {
    serverAddr = "localhost:6379"
    db = 0
    password = ""
    cluster = "default"
    timeout = 0
  }
```
由于默认的application 是seata-server， 而我们引入的NacosRegistryServiceImpl中写死了服务叫serverAddr， 故一直找不到seata TC 的服务所在，还有一点是seata的group默认是"SEATA_GROUP"，如果我们客户端服务的group和seata不一样，也会报题目的错误，只要把registry.conf的nacos中的application 改为serverAddr，并且把group改为和客户端一样的或者修改客户端的group，要求一致即可：

## 改客户端
```bash
spring:
  cloud:
    nacos:
      discovery:
        server-addr: 1.1.11.15:8848
        namespace: ff2a1399-d3a6-48f5-80a9-18d67b35e383
        group: SEATA_GROUP
```
## 改registry.conf
```bash
registry {
  # file 、nacos 、eureka、redis、zk、consul、etcd3、sofa
  type = "nacos"

  nacos {
    application = "serverAddr"
    serverAddr = "XXXXXXX"
    group = "SEATA_GROUP"
    namespace = "XXXXXX"
    cluster = "default"
    username = "XXXXX"
    password = "XXXX"
  }
  eureka {
    serviceUrl = "http://localhost:8761/eureka"
    application = "default"
    weight = "1"
  }
  redis {
    serverAddr = "localhost:6379"
    db = 0
    password = ""
    cluster = "default"
    timeout = 0
  }
```

真是坑爹
