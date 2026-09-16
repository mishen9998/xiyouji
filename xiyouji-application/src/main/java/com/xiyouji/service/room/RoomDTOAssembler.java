package com.xiyouji.service.room;

import com.xiyouji.dto.response.room.RoomDTO;
import org.springframework.stereotype.Component;

/**
 * 房间 DTO 组装器：领域对象 Room 转换为对外的 RoomDTO。
 */
@Component
public class RoomDTOAssembler {

    public RoomDTO toDTO(Room room) {
        RoomDTO dto = new RoomDTO();
        dto.setCode(room.getCode());
        dto.setHostUserId(room.getHostUserId());
        dto.setPlayers(room.getPlayers());
        dto.setPlayerCount(room.getPlayerCount());
        dto.setStatus(room.getStatus());
        dto.setCreatedAt(room.getCreatedAt());
        dto.setFloor(room.getFloor());
        dto.setMaxLayer(room.getMaxLayer());
        dto.setMap(room.getMap());
        dto.setCurrentNode(room.getCurrentNode());
        dto.setBonfireUpgradesLeft(room.getBonfireUpgradesLeft());
        dto.setStateVersion(room.getStateVersion());
        if (room.getStatus() != RoomStatus.WAITING) dto.setStoryEvent(com.xiyouji.service.event.StoryCatalog.map(
            room.getCode(),room.getFloor(),room.getCurrentNode(),room.getStatus() == RoomStatus.FINISHED,
            room.getPlayers().stream().filter(p -> p.getHp() > 0).map(com.xiyouji.service.event.EventActor::room).toList()));
        return dto;
    }
}
