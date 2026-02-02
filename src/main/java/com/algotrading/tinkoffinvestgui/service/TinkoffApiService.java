package com.algotrading.tinkoffinvestgui.service;

import ru.tinkoff.piapi.contract.v1.*;
import io.grpc.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Сервис для работы с Tinkoff Invest API
 * Для обычных Java приложений (Swing) БЕЗ Spring Boot
 * Использует прямые gRPC вызовы с правильными именами классов из SDK 1.44
 */
public class TinkoffApiService {

    private static final String INVEST_API_HOST = "invest-public-api.tinkoff.ru";
    private static final int INVEST_API_PORT = 443;

    private final ManagedChannel channel;
    private final String accountId;

    private final OperationsServiceGrpc.OperationsServiceBlockingStub operationsStub;
    private final OrdersServiceGrpc.OrdersServiceBlockingStub ordersStub;
    private final InstrumentsServiceGrpc.InstrumentsServiceBlockingStub instrumentsStub;
    private final MarketDataServiceGrpc.MarketDataServiceBlockingStub marketDataStub;
    private final UsersServiceGrpc.UsersServiceBlockingStub usersStub;

    /**
     * Конструктор
     * @param token API токен от Tinkoff Invest
     * @param accountId ID счета
     */
    public TinkoffApiService(String token, String accountId) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("Token не может быть пустым");
        }
        if (accountId == null || accountId.isEmpty()) {
            throw new IllegalArgumentException("AccountId не может быть пустым");
        }

        this.accountId = accountId;

        // Создаем gRPC канал
        this.channel = ManagedChannelBuilder
                .forAddress(INVEST_API_HOST, INVEST_API_PORT)
                .useTransportSecurity()
                .build();

        // Создаем interceptor для добавления токена авторизации
        ClientInterceptor authInterceptor = new ClientInterceptor() {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                    MethodDescriptor<ReqT, RespT> method,
                    CallOptions callOptions,
                    Channel next) {
                return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                        next.newCall(method, callOptions)) {
                    @Override
                    public void start(Listener<RespT> responseListener, Metadata headers) {
                        headers.put(Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER),
                                "Bearer " + token);
                        super.start(responseListener, headers);
                    }
                };
            }
        };

        // Применяем interceptor к каналу
        Channel interceptedChannel = ClientInterceptors.intercept(channel, authInterceptor);

        // Создаем стабы (заглушки) для всех сервисов
        this.operationsStub = OperationsServiceGrpc.newBlockingStub(interceptedChannel);
        this.ordersStub = OrdersServiceGrpc.newBlockingStub(interceptedChannel);
        this.instrumentsStub = InstrumentsServiceGrpc.newBlockingStub(interceptedChannel);
        this.marketDataStub = MarketDataServiceGrpc.newBlockingStub(interceptedChannel);
        this.usersStub = UsersServiceGrpc.newBlockingStub(interceptedChannel);
    }

    /**
     * Получить портфель
     */
    public PortfolioResponse getPortfolio() {
        PortfolioRequest request = PortfolioRequest.newBuilder()
                .setAccountId(accountId)
                .build();
        return operationsStub.getPortfolio(request);
    }

    /**
     * Получить список заявок
     */
    public List<OrderState> getOrders() {
        GetOrdersRequest request = GetOrdersRequest.newBuilder()
                .setAccountId(accountId)
                .build();
        GetOrdersResponse response = ordersStub.getOrders(request);
        return response.getOrdersList();
    }

    /**
     * Разместить заявку
     */
    public String postOrder(String figi, long quantity, Quotation price,
                            OrderDirection direction, OrderType orderType) {
        PostOrderRequest request = PostOrderRequest.newBuilder()
                .setFigi(figi)
                .setQuantity(quantity)
                .setPrice(price)
                .setDirection(direction)
                .setAccountId(accountId)
                .setOrderType(orderType)
                .setOrderId(java.util.UUID.randomUUID().toString())
                .build();

        PostOrderResponse response = ordersStub.postOrder(request);
        return response.getOrderId();
    }

    /**
     * Отменить заявку
     */
    public void cancelOrder(String orderId) {
        CancelOrderRequest request = CancelOrderRequest.newBuilder()
                .setAccountId(accountId)
                .setOrderId(orderId)
                .build();
        ordersStub.cancelOrder(request);
    }

    /**
     * Получить инструмент по FIGI
     */
    public Instrument getInstrumentByFigi(String figi) {
        InstrumentRequest request = InstrumentRequest.newBuilder()
                .setIdType(InstrumentIdType.INSTRUMENT_ID_TYPE_FIGI)
                .setId(figi)
                .build();
        InstrumentResponse response = instrumentsStub.getInstrumentBy(request);
        return response.getInstrument();
    }

    /**
     * Получить последнюю цену инструмента
     */
    public LastPrice getLastPrice(String figi) {
        GetLastPricesRequest request = GetLastPricesRequest.newBuilder()
                .addFigi(figi)
                .build();
        GetLastPricesResponse response = marketDataStub.getLastPrices(request);

        if (response.getLastPricesCount() == 0) {
            throw new RuntimeException("Не удалось получить цену для " + figi);
        }

        return response.getLastPrices(0);
    }

    /**
     * Получить список счетов
     */
    public List<Account> getAccounts() {
        GetAccountsRequest request = GetAccountsRequest.newBuilder().build();
        GetAccountsResponse response = usersStub.getAccounts(request);
        return response.getAccountsList();
    }

    /**
     * Получить используемый accountId
     */
    public String getAccountId() {
        return accountId;
    }

    /**
     * Закрыть соединение с API
     * ВАЖНО: вызывайте при завершении работы приложения!
     */
    public void close() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            channel.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}