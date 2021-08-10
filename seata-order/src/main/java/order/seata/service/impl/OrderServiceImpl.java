package order.seata.service.impl;


import com.dsy.sunshine.core.Response;
import io.seata.core.context.RootContext;
import io.seata.spring.annotation.GlobalTransactional;
import order.seata.dao.OrderDao;
import order.seata.entity.OrderDO;
import order.seata.feign.AccountServiceFeignClient;
import order.seata.feign.ProductServiceFeignClient;
import order.seata.feign.dto.AccountReduceBalanceDTO;
import order.seata.feign.dto.ProductReduceStockDTO;
import order.seata.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OrderServiceImpl implements OrderService {

    private Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private OrderDao orderDao;

    @Autowired
    private AccountServiceFeignClient accountService;
    @Autowired
    private ProductServiceFeignClient productService;

    @Override
    @GlobalTransactional
    public Integer createOrder(Long userId, Long productId, Integer price) {
        Integer amount = 1; // 购买数量，暂时设置为 1。

        logger.info("[createOrder] 当前 XID: {}", RootContext.getXID());

        // 扣减库存
        productService.reduceStock(new ProductReduceStockDTO().setProductId(productId).setAmount(amount));

        // 扣减余额
        Response<Void> response = accountService.reduceBalance(new AccountReduceBalanceDTO().setUserId(userId).setPrice(price));
        if (!response.isSuccess()){
            throw new RuntimeException("扣减余额出错，抛异常回退");
        }

        // 保存订单
        OrderDO order = new OrderDO().setUserId(userId).setProductId(productId).setPayAmount(amount * price);
        orderDao.saveOrder(order);
        logger.info("[createOrder] 保存订单: {}", order.getId());
        if (true){
            throw new RuntimeException("故意抛出异常回退");
        }
        // 返回订单编号
        return order.getId();
    }

}
