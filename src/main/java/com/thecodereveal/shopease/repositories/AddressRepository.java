package com.thecodereveal.shopease.repositories;

import com.thecodereveal.shopease.auth.entities.User;
import com.thecodereveal.shopease.entities.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AddressRepository extends JpaRepository<Address, UUID> {
    List<Address> findByUser(User user);
}
