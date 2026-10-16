package com.suntravels.repository;

import com.suntravels.entity.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RoomTypeRepository extends JpaRepository<RoomType, Integer>,
        JpaSpecificationExecutor<RoomType> {
    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndRoomTypeIdNot(String name, Integer roomTypeId);
}
