package com.suntravels.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table( name = "room_type" )
public class RoomType
{
    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    @Column( name = "room_type_id" )
    private Integer roomTypeId;
    @Column( name = "name", nullable = false, length = 100 )
    private String name;
    @Column( name = "description", length = 255 )
    private String description;

    // Creates an empty object for fields to be populated after construction.
    public RoomType()
    {
        // Required by persistence or JSON binding; fields are populated after construction.
    }

    // Returns the room type id.
    public Integer getRoomTypeId()
    {
        return roomTypeId;
    }

    // Returns the name.
    public String getName()
    {
        return name;
    }

    // Updates the name.
    public void setName( String name )
    {
        this.name = name;
    }

    // Returns the description.
    public String getDescription()
    {
        return description;
    }

    // Updates the description.
    public void setDescription( String description )
    {
        this.description = description;
    }
}
