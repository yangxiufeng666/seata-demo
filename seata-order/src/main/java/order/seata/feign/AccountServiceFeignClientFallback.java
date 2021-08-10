package order.seata.feign;

import com.dsy.sunshine.core.Response;
import feign.hystrix.FallbackFactory;
import order.seata.feign.dto.AccountReduceBalanceDTO;
import org.springframework.stereotype.Component;

/**
 * @author Mr.Yangxiufeng
 * @date 2021-08-10
 * @time 14:43
 */
@Component
public class AccountServiceFeignClientFallback implements FallbackFactory<AccountServiceFeignClient> {
    @Override
    public AccountServiceFeignClient create(Throwable throwable) {
        return new AccountServiceFeignClient() {
            @Override
            public Response<Void> reduceBalance(AccountReduceBalanceDTO accountReduceBalanceDTO) {
                return Response.fail();
            }
        };
    }
}
