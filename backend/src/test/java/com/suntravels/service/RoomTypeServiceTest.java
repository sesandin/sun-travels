package com.suntravels.service;
import com.suntravels.dto.*;
import com.suntravels.entity.RoomType;
import com.suntravels.exception.*;
import com.suntravels.repository.RoomTypeRepository;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class RoomTypeServiceTest {
 @Mock RoomTypeRepository repository;
 @InjectMocks RoomTypeService service;
 RoomTypeRequest request(String name, String description) { RoomTypeRequest r=new RoomTypeRequest(); r.setName(name); r.setDescription(description); return r; }
 @Test void createsNormalizedReferenceData() {
  when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
  var response=service.createRoomType(request(" Deluxe ","  Spacious  "));
  assertEquals("Deluxe",response.name()); assertEquals("Spacious",response.description());
  verify(repository).existsByNameIgnoreCase("Deluxe");
 }
 @Test void rejectsDuplicateCreateWithoutSaving() {
  when(repository.existsByNameIgnoreCase("Deluxe")).thenReturn(true);
  assertThrows(DuplicateRoomTypeException.class,() -> service.createRoomType(request("Deluxe",null)));
  verify(repository,never()).save(any());
 }
 @Test void updatesExistingTypeAndAllowsItsOwnName() {
  RoomType room=new RoomType(); room.setName("Deluxe");
  when(repository.findById(3)).thenReturn(Optional.of(room));
  when(repository.save(room)).thenReturn(room);
  var response=service.updateRoomType(3,request("Deluxe","  "));
  assertNull(response.description()); verify(repository).existsByNameIgnoreCaseAndRoomTypeIdNot("Deluxe",3);
 }
 @Test void duplicateUpdatePreservesExistingData() {
  RoomType room=new RoomType(); room.setName("Standard");
  when(repository.findById(3)).thenReturn(Optional.of(room));
  when(repository.existsByNameIgnoreCaseAndRoomTypeIdNot("Deluxe",3)).thenReturn(true);
  assertThrows(DuplicateRoomTypeException.class,() -> service.updateRoomType(3,request("Deluxe",null)));
  assertEquals("Standard",room.getName()); verify(repository,never()).save(any());
 }
 @Test void missingReadAndUpdateAreNotFound() {
  assertThrows(RoomTypeNotFoundException.class,() -> service.getRoomTypeById(9));
  assertThrows(RoomTypeNotFoundException.class,() -> service.updateRoomType(9,request("Standard",null)));
 }
 @Test void listsResponseDtos() {
  RoomType room=new RoomType(); room.setName("Standard");
  when(repository.findAll(any(Sort.class))).thenReturn(List.of(room));
  assertEquals("Standard",service.getAllRoomTypes().get(0).name());
 }
}
