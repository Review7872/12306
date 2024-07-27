# 目录

[关于 12306](#1)

[项目规划](#2)

[微服务组件介绍](#3)

[个人补充](#4)

# <span id="1">1 关于 12306</span>

本项目来源于 https://gitee.com/nageoffer/12306 （ 2024/7/15 版本），本人只做学习参考。本项目设计了大量企业级编码规范，建议加入原作者知识星球进行细致学习。

*引用部位为本人观点

# <span id="2">2 项目规划</span>

## 2.1 微服务切分

用户组件

购票组件

订单组件

支付组件

网关组件

## 2.2 技术选型（部分）

Nacos

Sentinel

SpringCloudGateway

LoadBalancer

OpenFeign

Redis

RocketMQ

Canal

Sharding-JDBC

# <span id="3">3 微服务组件介绍</span>

## 3.1 用户组件

本组件提供了用户的信息管理功能，涉及了 6 个表。

------

![](static\1.png)

![7](static\7.png)

![](static\2.png)

![3](static\3.png)

![4](static\4.png)

![5](static\5.png)

![6](static\6.png)

![](static\8.png)

------

由于 12306 设计用户数量非常庞大，故采用 sharding-jdbc 对数据库进行分库分表处理，t_user 为用户主表，存储了用户的全部个人信息，分片键采用用户名 username 的 hash 值，每个表的主键使用数据库自增长表并且无任何业务意义，使用布隆过滤器防止用户名重复，

用户注册必须填写手机号码，注册时先检查用户名是否被使用，若通过验证则以注册的用户名为 key 获取分布式锁开启对多个表的的插入操作，首先插入 t_user 主表，随后插入 t_user_phone 号码表，若用户有填写邮箱则再插入 t_user_mail 邮箱表，随后将用户名添加入布隆过滤器，并且将用户名从可重用数据库（由于布隆过滤器无法删除，故设计此表）与 Redis 中的可重用用户名集合中删除，

> 1.这里先判断用户名是否可用，随后获取锁进行插入，如果这其间用户名被注册，还是会引发数据库插入失败问题，作者也想到了所以捕获了唯一性约束异常，所以布隆过滤器的判断是否可以省略？在业务方面考虑可以省略，但是加上此处判断可以防止恶意调用接口打崩数据库。
>
> 2.在操作多个数据库时是否必须要获取分布式锁？获取多个分布式锁可以保证只有一个线程操作数据库，从而保护数据库&保护数据，插入数据库是一条线性操作，按理我们可以不使用分布式锁这样第一条插入失败（这里由数据库唯一性约束来保证数据安全）后续自然无法执行，我们采用了分库分表设计，同一个用户名会被添加入一个表，这里我能想到的用处也是同上：减轻多线程并发数据库压力。

用户名是否存在接口，此处作者的业务逻辑为：先去判断布隆过滤器（不存在就一定不存在，存在不一定存在），如果不存在则直接返回用户名可用，如果存在则需要再次判断，此时存在的情况有可能是布隆过滤器碰撞，也有可能是因为用户名被注销（布隆过滤器无法删除），这里作者随后判断 Redis 中可重用用户名集合是否存在此用户名，存在则返回存在，反之返回不存在，

> 这里源代码有点小问题：当不存在于布隆过滤器时返回了 true，我改成了 false 。
>
> 其次，众所周知，Redis 是不能保证数据安全的，即使开启了 rdb、aof 持久化最大也只能保证1秒前的数据安全，所以存在于布隆过滤器的判断有点简陋。

用户注销接口，此接口与注册类似，但是不再判断布隆过滤器，拿到用户名的分布式锁后，先将 证件类型和证件号 插入到删除用户名记录表，然后删除用户表、手机号码表、邮箱表（如果有），然后删除 Redis 中的 Token 信息，随后插入可重用用户名表、插入 Redis 可重用用户名集合，

> 这里 Redis 的可重用用户名集合是采用 set 数据类型存储，作者还做了分片处理，用户名哈希码以 1024 取模 存入 Redis

用户登录接口，先拿到用户输入的 用户名/手机号码/邮箱 信息，然后判断是否为邮箱，如果格式为邮箱则从 t_user_mail 表查询此邮箱对应的用户名（没查到说明输入错误），否则从手机号码表查询对应的用户名（没查到不一定输出错误，用户名赋值为空），如果用户名为空，则说明这时可能用户输入的是用户名也可能输入错误，直接去 t_user 查询，查询成功说明用户使用用户名登录，反之说明输出错误，如果查询成功，此时颁发 JWT ，JWT 入缓存后，将 主键id、输入的 用户名/手机号码/邮箱 信息、真实姓名、JWT 返回给客户端，

> 这里如果用户名设置为一个邮箱格式，则会出问题，所以注册时需要预防。
>
> 真实姓名可以返回给客户端吗？我觉得这是个敏感数据。
>
> 主键id返回给客户端有用吗？貌似没什么用，购票的记录采用的是证件号码。
>
> JWT 的过期时间为 1 天，而入缓存的过期时间设置为了 30 分钟。这里看起来没什么问题，但其实是有点意思的，微服务网关中只做了一件事，配置路径黑名单，对黑名单中的请求进行处理，解析请求头中的 JWT 信息，把详细信息重新附着到请求头上，如果 JWT 过期则不予放行，并没有判断 Redis ，这样显得 Redis 中的 JWT 有点多余，之前和朋友也商量过一致认为 JWT 是可以脱离 Redis单独使用的，但是一般我们都会配合 Redis 一方面更加灵活，二者可以预防 JWT 预料之外的 bug。

用户登出、用户是否登录接口，这两个接口都是判断 Redis 中 JWT 是否存在，没有什么特色。 

------

查询用户脱敏信息（通过主键 id ），通过主键 id 查询用户全部信息，这个接口非常慢，因为要扫描所有的分表，

查询用户脱敏信息（通过用户名），这个接口相比上一个快点，因为可以根据用户名精准分片，

> 这个信息脱敏的设计非常巧妙，作者重写了 SpringMVC 的内部 jackson 对 证件号、手机号码 的序列化方式。

查询用户真实信息（通过用户名），

根据 证件类型和证件号 查询注销次数（是记录行数），这里被用作注册的参数验证，

> 作者写了查缓存的 todo ，其实这个记录并没有入缓存，每次注册都要判断，所以这个接口使用次数非常多，建议入缓存，本人觉得查询注销次数应该从记录行数迁移到新增一个字段进行统计。

更新用户信息，这里没什么好说的，先通过用户名查询，然后更新 t_user 表，如果邮箱有更新还需要更新 t_user_mail 表（手机号码无法更新）。

------

查询乘车人信息（通过用户名），这里查询的结果为 List<PassengerRespDTO> ，一个用户可以添加多个乘车人，

> 这里的缓存查询使用了安全获取，设计非常巧妙。

查询乘车人信息（通过用户名，但只保留 指定主键 id 的乘车人信息），并不知道这个接口有什么用，

登记乘车人信息，将乘车人信息与 JWT 内置用户名绑定存入数据库，成功后删除缓存，

更新乘车信息，成功后删除缓存，

删除乘车信息，成功后删除缓存。

------

至此，这个组件中的所有业务都分析完毕了，这个组件的亮点都有：分库分表、责任链设计模式、自定义类字段jackson序列化方式实现数据脱敏、各种常量的规范化管控。另外本组件包括 sentinel、prometheus 两个运维管理界面。

## 3.2 车票组件

本组件提供了车票、座位、列车等方面的管理功能，涉及了 9 个表，是此项目含金量最高、业务最为复杂、最核心的模块。

![](static\18.png)

![19](static\19.png)

![20](static\20.png)

![21](static\21.png)

![22](static\22.png)

![23](static\23.png)

![](static\24.png)

![25](static\25.png)

![26](static\26.png)

![27](static\27.png)

![28](static\28.png)

![29](static\29.png)

![30](static\30.png)

![32](static\32.png)

![](static\33.png)

------

本组件提供了 城市查询、车站查询、列车查询、车票查询、购票、退票、查询列车详细信息，canal 数据一致性解决方案 RocketMQ 消费者端、延迟关闭订单消费者、支付结果回调购票消费者，

查询车站&城市站点集合信息，如果站点名称不为空则使用站点名查询，否则使用查询类型（0表示查询热门列车、1-5表示不同的地区首字母），

查询所有车站，

> 查询车站（根据地区） -> 查询列车（根据起始站） -> 查询座位（根据列车） -> 买票（根据座位）

查询列车（两个版本），

> 查询列车时顺便查询了座位

v1 首先根据车站代码从缓存中查询车次信息，如果缓存为空，则从数据库中查询车次关系信息，并加载到缓存中，然后根据车次ID查询座位类型和价格信息，并计算余票，最后组装查询结果的响应体。在查询和更新缓存的过程中，使用了分布式锁来保证并发访问的安全性，

v2 首先，通过分布式缓存实例获取出发站与到达站的车次详情，接着构建特定的缓存键来检索详细的车次信息，对获取的数据流进行解析与时间排序。随后，构建车次价格查询的缓存键列表，利用管道模式优化批量价格信息的获取，同时处理这些信息转换为业务对象，并准备余票查询的缓存键。再次使用管道模式批量获取余票信息，然后结合车次与价格信息，组装每个车次的座位详情，包括余票数量与票价。最后，将整理后的车次列表、出发站、到达站、车次品牌及座位类型列表等信息封装为查询结果返回，整个过程体现了高并发场景下的性能优化策略。

> 使用管道模式批量操作，减少与Redis的交互次数。
>
> 提高缓存命中率，优化缓存键结构。
>
> 进一步简化代码结构，提高可读性和可扩展性。
>
> 更高效的并发处理机制，减少锁竞争问题。

执行购票接口（被购票接口调用，接口未加任何锁），首先通过请求参数获取列车ID，并安全地从分布式缓存或数据库加载列车信息。接着，根据列车类型和购票需求选择座位，将选座结果转换为车票实体进行批量保存。同时，构建订单项和票务详情响应对象，查询列车时刻表信息，构建远程请求对象以调用远程服务创建订单。若订单创建失败，会抛出异常，最后返回包含订单号和票务详情的购票响应数据。

> 此时会锁定座位

购票（两个版本），

v1 使用了责任链模式进行参数验证。在购票过程中，使用了Redisson分布式锁来保证并发下的线程安全。通过环境变量解析和格式化生成锁的key。在加锁后，调用ticketService.executePurchaseTickets方法执行购票逻辑，最后在finally块中释放锁。

v2 首先运用责任链模式预处理购票请求，进行参数验证等；接着，利用令牌桶算法控制购票频率，避免系统过载，若令牌不足，尝试刷新或提示票尽；再者，根据座位类型分配本地与分布式锁，采用双重检查锁定策略确保线程安全，最终加锁执行购票流程并及时解锁，全面提升了购票效率与安全性。

> 令牌桶限流算法
>
> 本地锁和分布式锁

取消车票，首先，它调用远程服务取消订单，并检查是否取消成功且不使用binlog更新库存。如果满足条件，它会查询订单详情，获取车次信息和乘车人信息。然后，它会尝试释放座位，并在释放座位失败时抛出异常。接下来，它会回滚令牌桶库存，并更新Redis缓存中的余票信息。如果更新缓存失败，也会抛出异常。

------

支付结果回调购票消费者，如果支付成功就将座位从锁定改成已售，

延迟关闭订单消费者，调用远程服务关闭订单，如果关闭订单成功且不是通过binlog方式更新缓存则会进一步释放火车座位 & 更新缓存 & 回滚令牌桶，如果是 则会触发—— canal 列车车票余量缓存更新消费端，

canal 列车车票余量缓存更新消费端，通过策略模式 调用 org.opengoofy.index12306.biz.ticketservice.canal.OrderCloseCacheAndTokenUpdateHandler & org.opengoofy.index12306.biz.ticketservice.canal.TicketAvailabilityCacheUpdateHandler 两个类执行对缓存的更新。

------

除了以上接口，还有几个比较重要的接口，

org.opengoofy.index12306.biz.ticketservice.service.handler.ticket.select.TrainSeatTypeSelector 此接口为选择座位接口 ，此接口被 执行购票接口 使用，实现了一套复杂的火车票查询与分配流程。首先，它依据座位类型的数量决定是否采用并发查询策略来提升效率。对于多种座位类型的情况，利用线程池并行执行查询任务，收集并整合所有查询结果；而单一座位类型时，则直接进行查询。接着，检查查询结果是否满足需求，若票数不足则抛出异常。随后，通过远程调用获取乘客的详细信息，并将这些信息填充到查询到的车票数据中，同时查询并设定每张车票的价格。最后一步是锁定选定的座位，确保购票成功，并返回最终的车票信息列表，

org.opengoofy.index12306.biz.ticketservice.service.handler.ticket.tokenbucket.TicketAvailabilityTokenBucket  此接口为购票令牌桶，有拿取令牌 & 回滚令牌的功能。

------

至此，本组件所有业务分析完毕，这个组件非常复杂，完成了列车信息的查询、座位的选择，这个组件的亮点有：RocketMQ 消费者幂等处理、各种常量的规范化管控、责任链设计模式、各种常量的规范化管控。本组件包括 prometheus、sentinel 两个运维管理界面。

## 3.3 订单组件

本组件提供了订购车票的订单功能，设计了 3 个数据库表。

![](static\13.png)

![14](static\14.png)

![15](static\15.png)

![16](static\16.png)

![17](static\17.png)

------

该组件为详细订单组件，包含订单操作、MQ 支付结果回调消费者、MQ 退款回调消费者、MQ 订单延迟关闭生产者，

> 作者文档指出  MQ 订单延迟关闭消费者 也在这个组件下，但实际上， MQ 订单延迟关闭消费者 处在车票组件

订单的创建，首先将订单号、用户信息、列车信息插入到 order 表，之后获取请求中的乘车人信息，将乘车人详细信息、订单号、座位信息插入到 order_item 表中，再将 乘车人信息、订单号 插入到 order_item_passenger 表，

> order_item_passenger 表记录的信息在 order_item 表中已经记录过一次了。
>
> 订单号的生成融入了用户 id 。

订单的查询（根据订单号），这里会先去 order 表中查询，再去 order_item 中查询，随后将两者合并，

订单的查询（根据用户id），此方法也会查询两个表然后合并，

> 看到这个方法我觉得用户主键id生成可能是需要雪花算法的，但是作者没加，导致使用了数据库主键自增长。

查询本人订单，这个方法会获取登录状态用户的真实个人信息，然后查询此信息下的订单状态，

> 值得一提的是此方法查询了三个表，之前我有提到 order_item 与 order_item_passenger 数据冗余问题，order_item_passenger 表建立了 证件号字段的索引，而 order_item 表没有建立此索引

订单的取消，此方法会验证订单号是否存在，若存在则获取分布式锁，开启对 order & order_item 表修改订单状态为关闭，

订单状态反转，此方法会验证订单号是否存在，若存在则获取分布式锁，开启对 order & order_item 表修改订单状态，

支付回调接口，此方法会直接修改 order 表中的 payType & payTime 字段，

子订单状态反转，此方法用于车票部分退款情况，

------

支付结果回调订单消费者，此方法会调用 订单状态反转 & 支付回调接口 更改订单状态，

退款结果回调订单消费者，此方法会调用 子订单状态反转 更改部分订单状态或者全部订单状态。

------

至此，本组件所有业务分析完毕，这个组件相对简单，主要完成了对三个订单表的增删改查，这个组件的亮点有：分库分表、RocketMQ 消费者幂等处理、各种常量的规范化管控。本组件包括 prometheus、sentinel 两个运维管理界面。

## 3.4 支付组件

本组件提供了车票的支付功能，设计了 2 个数据库表。

![](static\9.png)

![10](static\10.png)

![11](static\11.png)

![12](static\12.png)

------

该组件封装了 AliPay 支付接口，提供了创建支付单（发起用户支付）、支付回调（用户支付成功后aliyun回调接口会附带订单信息）、查询订单详情、退款四大接口。

> 该组件可被其他支付组件平替，例如 WxPay 等等。

------

该组件还提供了 支付结果、退款回调 通过 RocketMQ 异步修改 Order订单状态功能 生产者。

------

至此，本组件所有业务分析完毕，这个组件比较简单，在订单创建成功后调用 支付组件 创建支付单，拿到支付 ID 发送客户端，客户端通过此 ID 支付给 Aliyun ，然后由 Aliyun 调用回调接口通知支付结果，这个组件的亮点有：支付单分库分表、封装 Alipay 接口、MQ异步更改订单状态、策略模式、各种常量的规范化管控。本组件包括 prometheus、sentinel 两个运维管理界面。

## 3.5 微服务网关组件

本组件是微服务网关，微服务入口，未涉及到数据库与 Redis 、MQ 等中间件的使用，单纯的 SpringCloudGateway处理。

------

本组件在配置文件中对 URL 进行了重定向，并且添加了黑名单，名单内请求需要结果过滤器二次处理：解析请求头中的 JWT 信息，把详细信息重新附着到请求头上，如果 JWT 过期则不予放行。

------

至此，本组件所有业务分析完毕，这个组件的亮点有：自定义过滤器。本组件包括 prometheus 一个运维管理界面。

# <span id="4">4 个人补充</span>

## 4.1 全局业务流程

以下是作者提供的前端项目使用到的接口：

```
登录
url: '/api/user-service/v1/login',
注册
url: '/api/user-service/register',
根据用户名查询乘车人列表
url: '/api/user-service/passenger/query',
移除乘车人
url: '/api/user-service/passenger/remove',
新增乘车人
url: '/api/user-service/passenger/save',
修改乘车人
url: '/api/user-service/passenger/update',
登出
url: '/api/user-service/logout',
修改用户
url: '/api/user-service/update',
根据用户名查询用户脱敏信息
url: '/api/user-service/query',
支付单详情查询
url: '/api/ticket-service/ticket/query',
查询车站&城市站点集合信息
url: '/api/ticket-service/region-station/query',
购买车票v2
url: '/api/ticket-service/ticket/purchase/v2',
查询车站站点集合信息
url: '/api/ticket-service/station/all'
取消车票订单
url: '/api/ticket-service/ticket/cancel',
根据列车 ID 查询站点信息
url: '/api/ticket-service/train-station/query',
公共退款接口
url: '/api/ticket-service/ticket/refund',
分页查询车票订单
url: '/api/order-service/order/ticket/page',
根据订单号查询车票订单
url: '/api/order-service/order/ticket/query',
分页查询本人车票订单
url: '/api/order-service/order/ticket/self/page',
跟据订单号查询支付单详情
url: '/api/pay-service/pay/query/order-sn',
公共支付接口
url: '/api/pay-service/pay/create',
```

可以看到业务接口大多使用的是 用户组件 & 车票组件 ，而订单组件是完全只用到了查询，支付组件除了查询接口还将请求支付接口暴露了，这样也不错，入参传入订单号即可，根据用户选择的支付方式自主选择请求支付接口，更好的解耦合。

所以看到这里，业务流程基本明了了吧，支付业务调用 支付组件（深度解耦合），用户信息的业务调用 用户组件，其他业务（车站查询、车票查询、购票、退票）都由 车票组件完成。

## 4.2 支付成功后订单状态的修改

支付组件提供了 支付结果、退款回调 两个生产者，退款回调的 消费端 在 订单组件中，而支付结果需要在 车票组件 & 订单组件分别消费一次。

为什么退款回调不在 车票组件 设立消费端，这种情况不应该也要回调车票吗？

消息的消费不是实时的，那么如何保证用户支付成功后马上能看到支付成功的订单？

## 4.3 车票预售

12306 具有预售功能，同一辆列车很多数据都是重复的，此项目中并没有将这些数据进行统一管控，我们后续同一辆列车的再次出发会产生很多数据冗余。

## 4.4 数据归档

可以发现并不是所有表都进行了分库分表，尤其是 车票组件，例如：座位表 （t_seat）。

如果考虑三层 B+ 树索引单表上限为 2kw 数据（实际上更多因为此表字段偏少），那么一辆正常列车可以乘坐 2000+ 乘客，而我国每天要形式11149辆列车（数据来自必应），那么这张表最多存储 1 天的数据...... 所以我们需要考虑拓展，而数据归档就是最实用的解决方式。

## 4.5 功能偏少

除去候补等等加分项，此项目缺少很多接口，例如：列车员检票（乘务员）、列车实际到站时间的记录（乘务员）、车站、列车站点、列车座位、列车种类等等信息的管理（顶级管理员）等等接口。

> 我也没有实现候补此功能，此功能可以使得 12306 收益最大化与用户最大体验，
>
> 但我已经设计好了方案：
>
> 我们选择逐次放票，比如：烟台->郑州->洛阳->西安，这趟车，15号的票提前半个月预售，1号这天从烟台到西安的票全部开放，烟台到郑州可能只开放10%，等到了13号、14号，我发现好像从烟台到西安的票只卖了40%，剩下了非常多的座位，为了避免空车，就选择烟台到郑州的票全部开放。
>
> 前几天没抢到很正常，之后会逐渐开放更多座位，这么做是为了保证尽可能的卖从起始站到终点站的票，以保证最大收益，当然，如果用户只坐一个站，那应该会有更多的交通方式，从某种角度讲，也极大的保证了用户的整体体验。
>
> 比如从烟台到淄博，去年全国都在抢火车票，而烟台到淄博的大巴，几乎没啥人。

## 4.6 总结

总体来说，这个项目是非常不错的，这也是我学习的第一个微服务项目（我之前都是自己编），尤其是编码风格很值得我们学习，我半年之前也独立写过 12306 项目。对比此项目，我的项目编码风格差、业务处理逻辑差，刚刚接触微服务开发的我风格是随机的、组件切分是随心所欲的、数据处理是统一 JSON 格式的、前端界面也是没有的......但是也不完全是缺点，例如我设计了候补功能，还写了用户、乘务员、总管理员的三种身份接口，我还使用了 Dubbo 框架，技术&业务的选型是需要压测的，我使用纯 Dubbo 通信可能会提升 TPS ，但此项目部分功能使用了 MQ ，在此场景下 HTTP 请求不一定会成为最大的性能瓶颈。