package com.suntravels.repository;
import com.suntravels.entity.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RoomTypeRepository extends JpaRepository<RoomType,Integer> {
 boolean existsByNameIgnoreCase(String name);
 boolean existsByNameIgnoreCaseAndRoomTypeIdNot(String name, Integer roomTypeId);
}
