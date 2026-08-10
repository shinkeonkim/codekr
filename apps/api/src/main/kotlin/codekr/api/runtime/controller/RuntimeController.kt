package codekr.api.runtime.controller

import codekr.api.runtime.RuntimeDefinition
import codekr.api.runtime.RuntimeRegistry
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/runtimes")
class RuntimeController(private val runtimeRegistry: RuntimeRegistry) {

    @GetMapping
    fun findAll(): List<RuntimeDefinition> = runtimeRegistry.findAll()
}
