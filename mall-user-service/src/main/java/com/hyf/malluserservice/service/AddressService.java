package com.hyf.malluserservice.service;

import com.hyf.malluserservice.dto.request.AddressSaveRequest;
import com.hyf.malluserservice.dto.response.AddressResponse;
import java.util.List;

public interface AddressService {

    public AddressResponse createAddress(AddressSaveRequest req);
    public List<AddressResponse> listAddresses();
    public AddressResponse getAddress(Long id);
    public AddressResponse updateAddress(Long id, AddressSaveRequest req);
    public void deleteAddress(Long id);

}
