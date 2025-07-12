package io.ejangs.docsa.domain.save.api;

import io.ejangs.docsa.domain.save.app.SaveService;
import io.ejangs.docsa.domain.save.dto.SaveGetIdDto;
import io.ejangs.docsa.domain.save.dto.SaveUpdateIdDto;
import io.ejangs.docsa.domain.save.dto.request.SaveUpdateRequest;
import io.ejangs.docsa.domain.save.dto.response.SaveGetResponse;
import io.ejangs.docsa.domain.save.dto.response.SaveUpdateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SaveController {

    private final SaveService saveService;

    @GetMapping("/api/document/{documentId}/save/{saveId}")
    public ResponseEntity<SaveGetResponse> getSave(@RequestParam Long userId,
            @PathVariable Long saveId,
            @PathVariable Long documentId) {
        SaveGetResponse response = saveService.getSave(SaveGetIdDto.of(documentId, saveId, userId));
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PutMapping("/api/document/{documentId}/save/{saveId}")
    public ResponseEntity<SaveUpdateResponse> updateSave(@RequestParam Long userId,
            @PathVariable Long documentId,
            @PathVariable Long saveId,
            @RequestBody SaveUpdateRequest saveUpdateRequest) {
        SaveUpdateResponse response = saveService.updateSave(
                SaveUpdateIdDto.of(documentId, saveId, userId), saveUpdateRequest);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
