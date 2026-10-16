package com.suntravels.service;

import java.util.List;

import com.suntravels.dto.*;
import com.suntravels.entity.RoomType;
import com.suntravels.exception.*;
import com.suntravels.repository.RoomTypeRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RoomTypeService {
    private final RoomTypeRepository repository;

    public RoomTypeService(RoomTypeRepository repository) {
        this.repository = repository;
    }

    public List<RoomTypeResponse> getAllRoomTypes() {
        return repository.findAll(Sort.by("name").and(Sort.by("roomTypeId"))).stream().map(
                this::toResponse).toList();
    }

    public RoomTypeResponse getRoomTypeById(Integer id) {
        return toResponse(find(id));
    }

    @Transactional
    public RoomTypeResponse createRoomType(RoomTypeRequest request) {
        String name = request.getName().trim();
        // Best-effort check only: the existing schema has no unique name index.
        if (repository.existsByNameIgnoreCase(name)) throw new DuplicateRoomTypeException(name);
        RoomType roomType = new RoomType();
        apply(roomType, request);
        return toResponse(repository.save(roomType));
    }

    @Transactional
    public RoomTypeResponse updateRoomType(Integer id, RoomTypeRequest request) {
        RoomType roomType = find(id);
        String name = request.getName().trim();
        if (repository.existsByNameIgnoreCaseAndRoomTypeIdNot(name, id))
            throw new DuplicateRoomTypeException(name);
        apply(roomType, request);
        return toResponse(repository.save(roomType));
    }

    private RoomType find(Integer id) {
        return repository.findById(id).orElseThrow(() -> new RoomTypeNotFoundException(id));
    }

    private void apply(RoomType roomType, RoomTypeRequest request) {
        roomType.setName(request.getName().trim());
        String description = request.getDescription();
        roomType.setDescription(description == null || description.isBlank() ? null : description.trim());
    }

    private RoomTypeResponse toResponse(RoomType roomType) {
        return new RoomTypeResponse(roomType.getRoomTypeId(), roomType.getName(), roomType.getDescription());
    }
}
