package com.suntravels.dto;
import jakarta.validation.constraints.*;
public class RoomTypeRequest {
 @NotBlank(message="Room type name is required") @Size(max=100, message="Name must be at most 100 characters") private String name;
 @Size(max=255, message="Description must be at most 255 characters") private String description;
 public String getName() { return name; }
 public void setName(String name) { this.name=name; }
 public String getDescription() { return description; }
 public void setDescription(String description) { this.description=description; }
}
