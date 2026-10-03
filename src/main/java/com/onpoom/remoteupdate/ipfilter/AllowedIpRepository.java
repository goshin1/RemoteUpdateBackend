package com.onpoom.remoteupdate.ipfilter;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AllowedIpRepository extends JpaRepository<AllowedIp, Long> {
}
