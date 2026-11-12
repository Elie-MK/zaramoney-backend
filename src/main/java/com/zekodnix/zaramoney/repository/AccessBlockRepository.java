package com.zekodnix.zaramoney.repository;

import com.zekodnix.zaramoney.domain.AccessBlock;
import com.zekodnix.zaramoney.domain.enumeration.AccessBlockType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccessBlockRepository extends JpaRepository<AccessBlock, Long> {
    boolean existsByBlockTypeAndBlockValue(AccessBlockType blockType, String blockValue);

    Optional<AccessBlock> findOneByBlockTypeAndBlockValue(AccessBlockType blockType, String blockValue);
}
