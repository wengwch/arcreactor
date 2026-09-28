package cn.veryai.arcreactor.web.api;

import cn.veryai.arcreactor.entity.ImageCategoryEntity;
import cn.veryai.arcreactor.service.ImageCategoryService;
import cn.veryai.arcreactor.web.params.SaveImageCategoryParam;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/image-categories")
@RequiredArgsConstructor
public class ImageCategoryController {
    private final ImageCategoryService service;

    @PostMapping
    public ResponseEntity<ImageCategoryEntity> create(@Valid @RequestBody SaveImageCategoryParam param) {
        ImageCategoryEntity entity = service.create(param);
        return ResponseEntity.status(HttpStatus.CREATED).body(entity);
    }

    @GetMapping
    public List<ImageCategoryEntity> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public ImageCategoryEntity get(@PathVariable("id") String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public ImageCategoryEntity update(@PathVariable("id") String id,
                                         @Valid @RequestBody SaveImageCategoryParam param) {
        return service.update(id, param);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") String id) {
        service.delete(id);
    }
}
