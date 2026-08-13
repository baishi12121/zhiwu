package com.hyf.malluserservice.service;

import com.hyf.malluserservice.dto.request.ProfileUpdateRequest;
import com.hyf.malluserservice.dto.response.ProfileResponse;

public interface UserService {

    public ProfileResponse getProfile();
    public ProfileResponse updateProfile(ProfileUpdateRequest req);
    public ProfileResponse updateAvatar(String avatarUrl);

}
