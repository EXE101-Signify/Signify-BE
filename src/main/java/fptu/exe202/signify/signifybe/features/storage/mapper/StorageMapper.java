package fptu.exe202.signify.signifybe.features.storage.mapper;

import fptu.exe202.signify.signifybe.features.storage.api.dto.PresignedUrlResponse;
import fptu.exe202.signify.signifybe.features.storage.api.dto.UploadImageResponse;
import fptu.exe202.signify.signifybe.features.storage.domain.StorageObject;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface StorageMapper {
    UploadImageResponse toUploadImageResponse(StorageObject object);

    PresignedUrlResponse toPresignedUrlResponse(String url);
}
