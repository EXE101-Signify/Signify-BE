package fptu.exe202.signify.signifybe.features.storage.api;

import fptu.exe202.signify.apiresponse.response.ApiResponse;
import fptu.exe202.signify.signifybe.features.storage.api.dto.DeleteImageRequest;
import fptu.exe202.signify.signifybe.features.storage.api.dto.PresignedUrlResponse;
import fptu.exe202.signify.signifybe.features.storage.api.dto.UploadImageResponse;
import fptu.exe202.signify.signifybe.features.storage.application.StorageService;
import fptu.exe202.signify.signifybe.features.storage.mapper.StorageMapper;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/storage/images")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StorageController {

    StorageService storageService;
    StorageMapper storageMapper;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadImageResponse> upload(@RequestPart("file") MultipartFile file) {
        var image = storageService.uploadImage(file);
        return ApiResponse.success("Image uploaded successfully", storageMapper.toUploadImageResponse(image));
    }

    @DeleteMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Void> delete(@Valid @RequestBody DeleteImageRequest request) {
        storageService.deleteImage(request.key());
        return ApiResponse.success("Image deleted successfully", null);
    }

    @GetMapping("/url")
    public ApiResponse<PresignedUrlResponse> url(@RequestParam("key") String key) {
        return ApiResponse.success(storageMapper.toPresignedUrlResponse(storageService.generateUrl(key)));
    }
}
