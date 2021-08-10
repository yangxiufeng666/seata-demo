package order.seata.feign;

import com.dsy.sunshine.core.Response;
import order.seata.feign.dto.AccountReduceBalanceDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * `account-service` 服务的 Feign 客户端
 */
@FeignClient(name = "seata-account")
public interface AccountServiceFeignClient {

    @PostMapping("/account/reduce-balance")
    Response<Void> reduceBalance(@RequestBody AccountReduceBalanceDTO accountReduceBalanceDTO);

}
