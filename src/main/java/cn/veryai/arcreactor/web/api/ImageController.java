package cn.veryai.arcreactor.web.api;

import cn.veryai.arcreactor.entity.ImageEntity;
import cn.veryai.arcreactor.service.ImageService;
import cn.veryai.arcreactor.web.params.SaveImageParam;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
public class ImageController {
    private final ImageService service;

    @PostMapping
    public ResponseEntity<ImageEntity> create(@Valid @RequestBody SaveImageParam param) {
        ImageEntity entity = service.create(param);
        return ResponseEntity.status(HttpStatus.CREATED).body(entity);
    }

    @GetMapping
    public List<ImageEntity> list(@RequestParam(value = "regionId", required = false) String regionId,
                                  @RequestParam(value = "categoryId", required = false) String categoryId) {
        return service.list(regionId, categoryId);
    }

    @GetMapping("/{id}")
    public ImageEntity get(@PathVariable("id") String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public ImageEntity update(@PathVariable("id") String id,
                                         @Valid @RequestBody SaveImageParam param) {
        return service.update(id, param);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") String id) {
        service.delete(id);
    }
}
