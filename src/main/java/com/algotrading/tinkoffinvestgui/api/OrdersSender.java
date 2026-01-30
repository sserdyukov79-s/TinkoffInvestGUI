package com.algotrading.tinkoffinvestgui.api;

import com.algotrading.tinkoffinvestgui.config.ConnectorConfig;
import com.algotrading.tinkoffinvestgui.entity.Instrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Утилита для массовой отправки заявок на биржу
 */
public class OrdersSender {

    private static final Logger log = LoggerFactory.getLogger(OrdersSender.class);

    /**
     * Отправляет заявки на покупку и продажу для списка инструментов
     */
    public static void sendOrders(List<Instrument> instruments, String accountId) {
        log.info("╔══════════════════════════════════════════════════════════════");
        log.info("║ МАССОВАЯ ОТПРАВКА ЗАЯВОК НА БИРЖУ");
        log.info("╠══════════════════════════════════════════════════════════════");
        log.info("║ Account ID: {}", accountId);
        log.info("║ Количество инструментов: {}", instruments.size());
        log.info("╚══════════════════════════════════════════════════════════════");

        OrdersService ordersService = new OrdersService(
                ConnectorConfig.getApiToken(),
                ConnectorConfig.API_URL,
                ConnectorConfig.API_PORT
        );

        int successCount = 0;
        int errorCount = 0;

        for (Instrument instrument : instruments) {
            try {
                log.info("\n📊 Обработка инструмента: {}", instrument.getName());
                log.info("   FIGI: {}", instrument.getFigi());
                log.info("   ISIN: {}", instrument.getIsin());
                log.info("   Приоритет: {}", instrument.getPriority());

                // Отправляем заявку на покупку (если указана)
                if (instrument.getBuyPrice() != null &&
                        instrument.getBuyQuantity() != null &&
                        instrument.getBuyQuantity() > 0) {

                    log.info("\n🟢 Отправка заявки на ПОКУПКУ:");
                    log.info("   Цена: {}", instrument.getBuyPrice());
                    log.info("   Количество: {}", instrument.getBuyQuantity());

                    ordersService.postBuyOrder(
                            accountId,
                            instrument.getFigi(),
                            instrument.getBuyQuantity(),
                            instrument.getBuyPrice()
                    );

                    successCount++;
                    Thread.sleep(500); // Задержка между запросами
                }

                // Отправляем заявку на продажу (если указана)
                if (instrument.getSellPrice() != null &&
                        instrument.getSellQuantity() != null &&
                        instrument.getSellQuantity() > 0) {

                    log.info("\n🔴 Отправка заявки на ПРОДАЖУ:");
                    log.info("   Цена: {}", instrument.getSellPrice());
                    log.info("   Количество: {}", instrument.getSellQuantity());

                    ordersService.postSellOrder(
                            accountId,
                            instrument.getFigi(),
                            instrument.getSellQuantity(),
                            instrument.getSellPrice()
                    );

                    successCount++;
                    Thread.sleep(500); // Задержка между запросами
                }

            } catch (Exception e) {
                log.error("❌ Ошибка отправки заявки для {}: {}",
                        instrument.getName(), e.getMessage(), e);
                errorCount++;
            }
        }

        log.info("\n╔══════════════════════════════════════════════════════════════");
        log.info("║ ИТОГИ ОТПРАВКИ ЗАЯВОК");
        log.info("╠══════════════════════════════════════════════════════════════");
        log.info("║ ✅ Успешно отправлено: {}", successCount);
        log.info("║ ❌ Ошибок: {}", errorCount);
        log.info("╚══════════════════════════════════════════════════════════════");
    }
}
