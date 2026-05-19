package com.example.demo.repository;

import com.example.demo.entity.RoomFurniture;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RoomFurnitureRepository extends JpaRepository<RoomFurniture, Long> {
    

    List<RoomFurniture> findByRoomId(String roomId);

    void deleteByRoomId(String roomId);
}
