package com.suntravels.repository;

import com.suntravels.entity.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RoomTypeRepository extends JpaRepository<RoomType, Integer>,
        JpaSpecificationExecutor<RoomType> {
    // Checks whether a room type name already exists, ignoring case.
    boolean existsByNameIgnoreCase(String name);

    // Checks whether another room type uses the same name, ignoring case.
    boolean existsByNameIgnoreCaseAndRoomTypeIdNot(String name, Integer roomTypeId);
}
