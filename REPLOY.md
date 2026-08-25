1. 技术栈
   模块	技术
   后端框架	Spring Boot 4.1.1
   语言	Java 17
   构建工具	Maven
   数据库	MySQL 8（Docker 部署，宿主机端口 3307）
   MQTT Broker	Mosquitto（Docker 部署，宿主机端口 1883）
   ORM	Spring Data JPA / Hibernate
   安全	Spring Security + JWT
   消息集成	Spring Integration MQTT
2. 环境准备
   Docker Desktop（用于运行 MySQL 和 Mosquitto）
   Java 17 或更高版本（推荐 Eclipse Temurin 17）
   Maven 3.8+（可使用 IDEA 内置 Maven，或单独安装）
3. 快速启动
   
3.1 启动 MySQL 和 Mosquitto 容器
# 启动 MySQL 容器（宿主机 3307 → 容器 3306）
docker run -d --name smartlamp-mysql -p 3307:3306 -e MYSQL_ROOT_PASSWORD=123456 mysql:8

# 启动 Mosquitto 容器
docker run -d --name mosquitto -p 1883:1883 -p 9001:9001 eclipse-mosquitto
若容器已存在但未运行：

bash
复制
下载
docker start smartlamp-mysql mosquitto
  
3.2 创建数据库
bash
复制
下载
docker exec -it smartlamp-mysql mysql -uroot -p123456 -e "CREATE DATABASE IF NOT EXISTS smartlamp DEFAULT CHARACTER SET utf8mb4;"
后端 JPA 会自动建表，但不会自动创建数据库，所以此步骤必须执行。

3.3 配置后端
编辑 src/main/resources/application.yml，根据实际情况调整数据库密码、MQTT 地址等配置。默认配置如下：

yaml
复制
下载
server:
port: 8080

spring:
datasource:
url: jdbc:mysql://localhost:3307/smartlamp?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8
username: root
password: "123456"
driver-class-name: com.mysql.cj.jdbc.Driver
jpa:
hibernate:
ddl-auto: update
show-sql: true

mqtt:
broker: tcp://127.0.0.1:1883
client-id: smartlamp-backend
username:
password:

3.4 启动后端
方式 A：使用 IntelliJ IDEA
使用 IDEA 打开项目根目录（包含 pom.xml）。

等待 Maven 依赖下载完成。

找到启动类 SmartlampApplication，右键运行。

等待控制台输出 Tomcat started on port 8080。

方式 B：命令行
bash
复制
下载
cd backend
mvn spring-boot:run
或打包后运行：

bash
复制
下载
mvn clean package -DskipTests
java -jar target/backend-0.0.1-SNAPSHOT.jar

4.数据库表结构
   由 JPA ddl-auto: update 自动创建，包括以下表：

表名	说明	关键字段
device	设备表	id, code, location, longitude, latitude, status, latest_lux, last_seen, light_on
light_point	光照历史表	id, device_code, lux, ts, created_at
alarm	告警表	id, device_id, type, level, message, ts, status, created_at
sys_user	用户表	id, username, password, role, status, created_at
5. MQTT 主题说明
   方向	主题	说明
   上行（订阅）	device/{deviceId}/data	设备光照数据上报，payload: {"deviceId":"SL-001","lux":80,"ts":1787450000000}
   上行（订阅）	device/{deviceId}/heartbeat	设备心跳，payload: {"deviceId":"SL-001","ts":1787450000000}
   下行（发布）	device/{deviceId}/cmd	后端下发控制指令，payload: {"deviceId":"SL-001","on":true}
6. API 接口概览
   所有接口统一响应格式：{ "code": 0, "message": "ok", "data": {} }。
   除登录接口和 /events 外，均需携带请求头 Authorization: Bearer <token>。

6.1 认证
方法	路径	说明
POST	/api/auth/login	登录，请求体：{"username":"admin","password":"123456"}，返回 JWT token
6.2 设备管理
方法	路径	说明
GET	/api/devices	设备列表
POST	/api/devices	添加设备（需 code、longitude、latitude）
PATCH	/api/devices/{deviceId}	更新设备信息
DELETE	/api/devices/{deviceId}	解绑设备
GET	/api/devices/{deviceId}/light	当前光照
POST	/api/devices/{deviceId}/switch	开关灯（{"on":true}）
POST	/api/devices/{deviceId}/control	控制（{"action":"ON"}）
6.3 数据查询
方法	路径	说明
GET	/api/light/history?deviceId=&start=&end=	历史光照
GET	/api/telemetry?deviceId=&limit=	遥测历史
GET	/api/dashboard/overview	大屏概览
GET	/api/summary	汇总数据
GET	/events	SSE 实时推送（免鉴权）
6.4 告警管理
方法	路径	说明
GET	/api/alarms	告警列表
GET	/api/alerts	告警列表别名
POST	/api/alarms/{id}/ack	确认告警
PATCH	/api/alarms/{id}/resolve	确认告警别名
6.5 配置管理
方法	路径	说明
GET	/api/config/linkage	查询联动配置
PUT	/api/config/linkage	保存联动配置
GET	/api/config	查询系统配置
PUT	/api/config	保存系统配置
6.6 用户管理
方法	路径	说明
GET	/api/users	用户列表
POST	/api/users	新增用户
PUT	/api/users/{id}/role	修改角色
DELETE	/api/users/{id}	删除用户
6.7 智能问答（桩实现）
方法	路径	说明
POST	/api/agent/ask	AI 运维问答，当前返回固定内容
7. 认证与安全
   使用 Spring Security 进行接口保护。

登录成功签发 JWT，有效期 24 小时。

密码采用 BCrypt 加密存储（开发环境可使用 {noop} 前缀存储明文）。

未认证访问受保护接口返回 code=401，权限不足返回 code=403，HTTP 状态码均为 200。

8. 测试与验证
   8.1 登录测试
   bash
   复制
   下载
   curl -X POST http://localhost:8080/api/auth/login \
   -H "Content-Type: application/json" \
   -d '{"username":"admin","password":"123456"}'
   8.2 模拟设备上报
   bash
   复制
   下载
   docker exec -it mosquitto mosquitto_pub -t "device/SL-001/data" \
   -m '{"deviceId":"SL-001","lux":80,"ts":1787450000000}'
   8.3 检查设备状态
   bash
   复制
   下载
   curl -X GET http://localhost:8080/api/devices \
   -H "Authorization: Bearer <token>"
9. 常见问题
   Q1：启动报错 Public Key Retrieval is not allowed
   解答：数据库 URL 中已添加 allowPublicKeyRetrieval=true，若仍报错，检查 MySQL 容器是否启动，端口是否映射为 3307。

Q2：设备一直离线
解答：设备需要定时发送心跳。可用命令模拟心跳：

bash
复制
下载
docker exec -it mosquitto mosquitto_pub -t "device/SL-001/heartbeat" \
-m '{"deviceId":"SL-001","ts":'"$(date +%s%3N)"'}'
Q3：如何重置管理员密码？
sql
复制
下载
UPDATE sys_user SET password='{noop}123456' WHERE username='admin';
Q4：如何修改后端端口？
修改 application.yml 中的 server.port，并同步修改前端代理配置（如需要）。

10. 项目结构
    text
    复制
    下载
    backend
    ├── src/main/java/com/smartlamp
    │   ├── controller     # REST 控制器
    │   ├── service        # 业务逻辑层
    │   ├── repository     # 数据访问层
    │   ├── entity         # 实体类
    │   ├── dto            # 数据传输对象
    │   ├── config         # 配置类（Security、MQTT、CORS）
    │   ├── mqtt           # MQTT 消息监听
    │   ├── task           # 定时任务（离线检测）
    │   └── security       # JWT 工具与过滤器
    ├── src/main/resources
    │   └── application.yml
    └── pom.xml
    部署完成标志：后端日志显示 Tomcat started on port 8080，且无数据库或 MQTT 连接错误。
