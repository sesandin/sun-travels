package com.suntravels.entity;
import jakarta.persistence.*;
@Entity
@Table(name="room_type")
public class RoomType {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="room_type_id")
 private Integer roomTypeId;
 @Column(name="name", nullable=false, length=100) private String name;
 @Column(name="description", length=255) private String description;
 public RoomType() {}
 public Integer getRoomTypeId() { return roomTypeId; }
 public String getName() { return name; }
 public void setName(String name) { this.name=name; }
 public String getDescription() { return description; }
 public void setDescription(String description) { this.description=description; }
}
