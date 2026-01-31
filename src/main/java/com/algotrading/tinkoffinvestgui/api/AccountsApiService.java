package com.algotrading.tinkoffinvestgui.api;

import com.algotrading.tinkoffinvestgui.config.ConnectorConfig;
import io.grpc.Channel;
import ru.tinkoff.piapi.contract.v1.GetAccountsRequest;
import ru.tinkoff.piapi.contract.v1.GetAccountsResponse;
import ru.tinkoff.piapi.contract.v1.UsersServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * API сервис для работы со счетами Tinkoff Invest (получение списка счетов через gRPC)
 */
public class AccountsApiService extends BaseApiService {
    private static final Logger log = LoggerFactory.getLogger(AccountsApiService.class);

    public AccountsApiService() {
        super(ConnectorConfig.getApiToken(), ConnectorConfig.API_URL, ConnectorConfig.API_PORT);
        log.debug("AccountsApiService инициализирован");
    }

    /**
     * Получает список счетов пользователя из Tinkoff API
     */
    public GetAccountsResponse getAccounts() {
        log.info("📊 Запрос списка счетов из Tinkoff API");
        
        Channel channel = getChannel();
        UsersServiceGrpc.UsersServiceBlockingStub stub = UsersServiceGrpc.newBlockingStub(channel)
                .withCallCredentials(getCallCredentials());

        GetAccountsRequest request = GetAccountsRequest.newBuilder().build();
        GetAccountsResponse response = stub.getAccounts(request);
        
        log.info("✅ Получено счетов: {}", response.getAccountsCount());
        return response;
    }

    /**
     * Получает количество счетов пользователя
     */
    public int getAccountsCount() {
        GetAccountsResponse response = getAccounts();
        return response.getAccountsCount();
    }
}
