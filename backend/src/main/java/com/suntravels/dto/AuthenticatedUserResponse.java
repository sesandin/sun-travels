package com.suntravels.dto;

import com.suntravels.entity.UserRole;

public record AuthenticatedUserResponse(Integer userId, String username, String fullName, UserRole role) {}
