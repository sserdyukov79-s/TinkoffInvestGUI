package com.algotrading.tinkoffinvestgui.mapper;

import com.algotrading.tinkoffinvestgui.dto.InstrumentDTO;
import com.algotrading.tinkoffinvestgui.entity.Instrument;

/**
 * Маппер для конвертации между Instrument и InstrumentDTO
 */
public class InstrumentMapper {
    
    /**
     * Конвертирует Entity в DTO
     */
    public static InstrumentDTO toDTO(Instrument entity) {
        if (entity == null) {
            return null;
        }
        
        InstrumentDTO dto = new InstrumentDTO();
  //      dto.setId(entity.getId());

 //       dto.setBookdate(entity.getBookdate());
        dto.setFigi(entity.getFigi());
        dto.setName(entity.getName());
        dto.setIsin(entity.getIsin());
        dto.setPriority(entity.getPriority());
        dto.setBuyPrice(entity.getBuyPrice());
        dto.setBuyQuantity(entity.getBuyQuantity());
        dto.setSellPrice(entity.getSellPrice());
        dto.setSellQuantity(entity.getSellQuantity());
        
        return dto;
    }
    
    /**
     * Конвертирует DTO в Entity
     */
    public static Instrument toEntity(InstrumentDTO dto) {
        if (dto == null) {
            return null;
        }
        
        Instrument entity = new Instrument();
 //       entity.setId(dto.getId());
//        entity.setBookdate(dto.getBookdate());
        entity.setFigi(dto.getFigi());
        entity.setName(dto.getName());
        entity.setIsin(dto.getIsin());
        entity.setPriority(dto.getPriority());
        entity.setBuyPrice(dto.getBuyPrice());
        entity.setBuyQuantity(dto.getBuyQuantity());
        entity.setSellPrice(dto.getSellPrice());
        entity.setSellQuantity(dto.getSellQuantity());
        
        return entity;
    }
    
    /**
     * Обновляет существующий Entity из DTO
     */
    public static void updateEntity(Instrument entity, InstrumentDTO dto) {
        if (entity == null || dto == null) {
            return;
        }
        
  //      entity.setBookdate(dto.getBookdate());
        entity.setFigi(dto.getFigi());
        entity.setName(dto.getName());
        entity.setIsin(dto.getIsin());
        entity.setPriority(dto.getPriority());
        entity.setBuyPrice(dto.getBuyPrice());
        entity.setBuyQuantity(dto.getBuyQuantity());
        entity.setSellPrice(dto.getSellPrice());
        entity.setSellQuantity(dto.getSellQuantity());
    }
}
