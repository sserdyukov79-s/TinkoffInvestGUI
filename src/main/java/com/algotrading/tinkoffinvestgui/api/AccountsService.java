package com.algotrading.tinkoffinvestgui.api;

import com.algotrading.tinkoffinvestgui.config.ConnectorConfig;
import ru.tinkoff.piapi.contract.v1.*;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;

/**
 * Сервис для работы с аккаунтами Tinkoff Invest API.
 */
public class AccountsService extends BaseApiService {

    public AccountsService() {
        // Получаем токен из БД через ConnectorConfig
        super(
                ConnectorConfig.getApiToken(),
                ConnectorConfig.API_URL,
                ConnectorConfig.API_PORT
        );
        validateToken();
    }

    /**
     * Получает количество аккаунтов пользователя
     */
    public int getAccountsCount() {
        try {
            ManagedChannel channel = getChannel();
            Metadata headers = getAuthorizationHeaders();

            UsersServiceGrpc.UsersServiceBlockingStub usersService =
                    UsersServiceGrpc.newBlockingStub(channel)
                            .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));

            GetAccountsRequest request = GetAccountsRequest.newBuilder().build();
            GetAccountsResponse response = usersService.getAccounts(request);

            return response.getAccountsCount();
        } catch (Exception e) {
            throw handleApiError("получении количества аккаунтов", e);
        }
    }

    /**
     * Получает список всех аккаунтов
     */
    public GetAccountsResponse getAccounts() {
        try {
            ManagedChannel channel = getChannel();
            Metadata headers = getAuthorizationHeaders();

            UsersServiceGrpc.UsersServiceBlockingStub usersService =
                    UsersServiceGrpc.newBlockingStub(channel)
                            .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));

            GetAccountsRequest request = GetAccountsRequest.newBuilder().build();
            return usersService.getAccounts(request);
        } catch (Exception e) {
            throw handleApiError("получении списка аккаунтов", e);
        }
    }
}
