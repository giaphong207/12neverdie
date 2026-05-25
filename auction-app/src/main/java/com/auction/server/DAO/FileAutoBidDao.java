package com.auction.server.DAO;

import com.auction.shared.model.AutoBidConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation của AutoBidDao đọc/ghi qua DataManager singleton (TV3 dựng tuần 1).
 *
 * GIẢ ĐỊNH (CẦN PHỐI HỢP VỚI TV3):
 *   AppDataStore phải có:
 *       List<AutoBidConfig> getAutoBidConfigs();
 *       void setAutoBidConfigs(List<AutoBidConfig> configs);
 *
 *   Nếu AppDataStore của TV3 chưa có, mở PR thêm 1 field:
 *       private List<AutoBidConfig> autoBidConfigs = new ArrayList<>();
 *   và 2 getter/setter cho nó.
 */
public class FileAutoBidDao implements AutoBidDao {

    private final DataManager dataManager;

    public FileAutoBidDao(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    @Override
    public List<AutoBidConfig> findByAuctionId(String auctionId) {
        AppDataStore store = dataManager.load();
        List<AutoBidConfig> result = new ArrayList<>();
        for (AutoBidConfig cfg : safeList(store.getAutoBidConfigs())) {
            if (cfg.getAuctionId().equals(auctionId)) {
                result.add(cfg);
            }
        }
        return result;
    }

    @Override
    public Optional<AutoBidConfig> findByAuctionIdAndBidderId(String auctionId, String bidderId) {
        AppDataStore store = dataManager.load();
        for (AutoBidConfig cfg : safeList(store.getAutoBidConfigs())) {
            if (cfg.getAuctionId().equals(auctionId)
                    && cfg.getBidderId().equals(bidderId)) {
                return Optional.of(cfg);
            }
        }
        return Optional.empty();
    }

    @Override
    public void save(AutoBidConfig config) {
        AppDataStore store = dataManager.load();
        List<AutoBidConfig> configs = new ArrayList<>(safeList(store.getAutoBidConfigs()));
        boolean updated = false;
        for (int i = 0; i < configs.size(); i++) {
            if (configs.get(i).getId().equals(config.getId())) {
                configs.set(i, config);
                updated = true;
                break;
            }
        }
        if (!updated) configs.add(config);
        store.setAutoBidConfigs(configs);
        dataManager.save(store);
    }

    @Override
    public void deleteById(String configId) {
        AppDataStore store = dataManager.load();
        List<AutoBidConfig> configs = new ArrayList<>(safeList(store.getAutoBidConfigs()));
        configs.removeIf(c -> c.getId().equals(configId));
        store.setAutoBidConfigs(configs);
        dataManager.save(store);
    }

    @Override
    public List<AutoBidConfig> findAll() {
        AppDataStore store = dataManager.load();
        return new ArrayList<>(safeList(store.getAutoBidConfigs()));
    }

    private List<AutoBidConfig> safeList(List<AutoBidConfig> list) {
        return list == null ? new ArrayList<>() : list;
    }
}