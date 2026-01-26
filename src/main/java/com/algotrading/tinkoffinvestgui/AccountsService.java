package com.algotrading.tinkoffinvestgui.api;

import ru.tinkoff.piapi.contract.v1.*;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;

import java.util.List;

/**
 * Сервис для работы с аккаунтами Tinkoff Invest API.
 * Управляет получением списка счетов и их информации.
 */
public class AccountsService extends BaseApiService {

    public AccountsService(String token, String apiUrl, int apiPort) {
        super(token, apiUrl, apiPort);
        validateToken();
    }

    /**
     * Получает количество счетов
     */
    public int getAccountsCount() {
        try {
            ManagedChannel channel = getChannel();
            Metadata headers = getAuthorizationHeaders();

            UsersServiceGrpc.UsersServiceBlockingStub usersService =
                    UsersServiceGrpc.newBlockingStub(channel)
                            .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));

            GetAccountsResponse response = usersService.getAccounts(GetAccountsRequest.getDefaultInstance());
            return response.getAccountsCount();
        } catch (Exception e) {
            throw handleApiError("получении количества счетов", e);
        }
    }

    /**
     * Получает список всех счетов
     */
    public List<Account> getAccountsList() {
        try {
            ManagedChannel channel = getChannel();
            Metadata headers = getAuthorizationHeaders();

            UsersServiceGrpc.UsersServiceBlockingStub usersService =
                    UsersServiceGrpc.newBlockingStub(channel)
                            .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));

            GetAccountsResponse response = usersService.getAccounts(GetAccountsRequest.getDefaultInstance());
            return response.getAccountsList();
        } catch (Exception e) {
            throw handleApiError("получении списка счетов", e);
        }
    }
}