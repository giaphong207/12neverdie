package com.auction.client.controller;

import com.auction.server.service.AuctionService;
import com.auction.server.service.DefaultAuctionService;
import com.auction.shared.model.Auction;
import com.auction.shared.model.AuctionStatus;
import com.auction.client.context.ClientSession;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class AuctionListController {

    @FXML private TableView<Auction> auctionTable;
    @FXML private TableColumn<Auction, String> itemIdCol;
    @FXML private TableColumn<Auction, Long> currentPriceCol;
    @FXML private TableColumn<Auction, AuctionStatus> statusCol;
    @FXML private TableColumn<Auction, LocalDateTime> endTimeCol;

    private final AuctionService auctionService = new DefaultAuctionService();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @FXML
    public void initialize() {
        // Cấu hình các cột lấy dữ liệu từ class Auction
        itemIdCol.setCellValueFactory(new PropertyValueFactory<>("itemId"));
        currentPriceCol.setCellValueFactory(new PropertyValueFactory<>("currentPrice"));
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));

        // Cột thời gian cần format lại cho đẹp
        endTimeCol.setCellValueFactory(new PropertyValueFactory<>("endTime"));
        endTimeCol.setCellFactory(column -> new javafx.scene.control.TableCell<Auction, LocalDateTime>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(formatter.format(item));
                }
            }
        });

        loadActiveAuctions();
    }

    public void loadActiveAuctions() {
        auctionTable.setItems(FXCollections.observableArrayList(auctionService.getActiveAuctions()));
    }

    @FXML
    public void onRefreshClicked() {
        loadActiveAuctions();
    }

    @FXML
    public void onOpenDetailClicked() {
        Auction selected = auctionTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            // Lưu ID sang Session để TV4 dùng
            ClientSession.setSelectedAuctionId(selected.getId());
            new Alert(Alert.AlertType.INFORMATION, "Đã lưu Session ID: " + selected.getId()).show();
        } else {
            new Alert(Alert.AlertType.WARNING, "Vui lòng chọn 1 dòng trên bảng!").show();
        }
    }
}