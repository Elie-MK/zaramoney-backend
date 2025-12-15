package com.zekodnix.zaramoney.service.mapper;

import com.zekodnix.zaramoney.domain.IdempotencyRecord;
import com.zekodnix.zaramoney.service.dto.IdempotencyRecordDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link IdempotencyRecord} and its DTO {@link IdempotencyRecordDTO}.
 */
@Mapper(componentModel = "spring")
public interface IdempotencyRecordMapper extends EntityMapper<IdempotencyRecordDTO, IdempotencyRecord> {}
