package com.algotrading.tinkoffinvestgui;

import ru.tinkoff.piapi.contract.v1.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import ru.tinkoff.piapi.contract.v1.UsersServiceGrpc;

import java.util.List;

public class AccountsService {
    private final String token;

    public AccountsService(String token) {
        this.token = token;
    }

    public int getAccountsCount() {
        // Старый метод остаётся
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress("invest-public-api.tinkoff.ru", 443)
                .useTransportSecurity()
                .build();
        try {
            Metadata headers = new Metadata();
            headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + token);
            UsersServiceGrpc.UsersServiceBlockingStub usersService = UsersServiceGrpc.newBlockingStub(channel)
                    .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
            GetAccountsResponse response = usersService.getAccounts(GetAccountsRequest.getDefaultInstance());
            return response.getAccountsCount();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка получения счетов: " + e.getMessage(), e);
        } finally {
            channel.shutdown();
        }
    }

    // НОВЫЙ МЕТОД: полный список счетов с деталями
    public List<Account> getAccountsList() {
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress("invest-public-api.tinkoff.ru", 443)
                .useTransportSecurity()
                .build();
        try {
            Metadata headers = new Metadata();
            headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + token);
            UsersServiceGrpc.UsersServiceBlockingStub usersService = UsersServiceGrpc.newBlockingStub(channel)
                    .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
            GetAccountsResponse response = usersService.getAccounts(GetAccountsRequest.getDefaultInstance());
            return response.getAccountsList();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка получения списка счетов: " + e.getMessage(), e);
        } finally {
            channel.shutdown();
        }
    }
}
