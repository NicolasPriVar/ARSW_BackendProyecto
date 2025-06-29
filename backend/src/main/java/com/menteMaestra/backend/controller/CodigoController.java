package com.menteMaestra.backend.controller;
import org.springframework.web.bin.annotation.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@RestController
@CrossOrigin(origins = "http://localhost:3000")
@RequestMapping("/api/codigo")
public class CodigoController {

    @PostMapping
    public Map<String, String> generarCodigo(){
        String codigo = String.valueOf(100000 + new Random().nextInt(900000));
        Map<String, String> response = new HashMap<>();
        response.put("codigo", codigo);
        return response;
    }
}