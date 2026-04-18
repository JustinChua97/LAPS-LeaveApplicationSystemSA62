package com.iss.laps.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    // forward SPA routes to index.html
    // regex excludes paths with dots so static files are still served
    @GetMapping({"/app", "/app/{p1:[^.]+}", "/app/{p1:[^.]+}/{p2:[^.]+}"})
    public String spa() {
        return "forward:/app/index.html";
    }
}
