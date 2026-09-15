package cn.veryai.arcreactor.web.api;

import cn.veryai.arcreactor.service.InstanceService;
import cn.veryai.arcreactor.web.params.CreateInstanceParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/instances")
public class InstanceController {

    @Autowired
    private InstanceService instanceService;


    @PostMapping
    public void createInstance(@RequestBody CreateInstanceParam createInstanceParam) {

    }

}
