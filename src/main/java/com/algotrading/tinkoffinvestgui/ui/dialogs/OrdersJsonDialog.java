package com.algotrading.tinkoffinvestgui.ui.dialogs;

import com.algotrading.tinkoffinvestgui.ui.utils.DialogUtils;
import com.algotrading.tinkoffinvestgui.ui.utils.TableUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;

/**
 * Диалог просмотра JSON заявок
 */
public class OrdersJsonDialog extends JDialog {
    private static final Logger log = LoggerFactory.getLogger(OrdersJsonDialog.class);

    public OrdersJsonDialog(JFrame parent, String ordersJson, String accountId, int instrumentsCount) {
        super(parent, "JSON Заявки", false);
        initializeUI(ordersJson, accountId, instrumentsCount);
    }

    private void initializeUI(String ordersJson, String accountId, int instrumentsCount) {
        setSize(800, 600);
        setLocationRelativeTo(getParent());
        setLayout(new BorderLayout(10, 10));

        // Заголовок
        JLabel titleLabel = new JLabel(
                String.format("Account: %s | Инструментов: %d", accountId, instrumentsCount),
                SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(titleLabel, BorderLayout.NORTH);

        // Текстовая область с JSON
        JTextArea jsonArea = new JTextArea(ordersJson);
        jsonArea.setEditable(false);
        jsonArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        jsonArea.setLineWrap(false);

        JScrollPane scrollPane = new JScrollPane(jsonArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(scrollPane, BorderLayout.CENTER);

        // Кнопки
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));

        JButton copyButton = new JButton("Копировать");
        copyButton.addActionListener(e -> {
            TableUtils.copyToClipboard(ordersJson);
            log.info("📋 JSON скопирован в буфер обмена");
            DialogUtils.showSuccess(this, "JSON скопирован в буфер обмена!");
        });

        JButton closeButton = new JButton("Закрыть");
        closeButton.addActionListener(e -> dispose());

        buttonPanel.add(copyButton);
        buttonPanel.add(closeButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }
}
